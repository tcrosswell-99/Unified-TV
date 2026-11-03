package io.unifiedtv.preview;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * SET-034 manual title correction, "Wrong picture?": held OK on a tile in Your Films &amp; TV (Adults only). The glass
 * sidebar over the still library (OD-19, the owner's sidebar rule) lists up to {@link #MAX} of TMDB's titles for the
 * tile's cleaned name, each with its own small poster, so the household chooses the right one by eye; "Search for a
 * different title" takes a name typed with the TV's own keyboard; "No picture" shows a plain title card (not even the
 * server's picture); "Use the automatic picture" goes back to the cautious match. The choice is kept per item (Posters),
 * for every profile, and is used from then on. Only the title goes to TMDB (OD-18), with the household's own key; with no
 * key there is nothing to search and the sheet says where an adult adds one, while "No picture" still works.
 */
public final class PictureFixActivity extends GlassScreen {
    static final String ACTION="Wrong picture?";
    static final int MAX=8;
    /** Tests only: TMDB answers on this executor instead of a background thread. */
    static volatile Executor testWorker;

    static Intent intent(Context c,LibraryCatalogue.Card card,String udn){
        return new Intent(c,PictureFixActivity.class).putExtra("udn",udn==null?"":udn).putExtra("key",card.lookupKey()).putExtra("title",card.lookupTitle())
            .putExtra("year",card.lookupYear()).putExtra("tv",card.tv()).putExtra("name",card.name());
    }

    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private String udn,key,name,year,query;private boolean tv,searching,typing;private int ticket;
    private List<Tmdb.Match> found=new ArrayList<>();private String problem;
    private final Map<Long,Button> rows=new HashMap<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);Intent in=getIntent();
        udn=in.getStringExtra("udn");key=in.getStringExtra("key");name=in.getStringExtra("name");year=in.getStringExtra("year");tv=in.getBooleanExtra("tv",false);
        query=in.getStringExtra("title");if(query==null||query.trim().isEmpty())query=name==null?"":name;
        // Kids keep favourites but never change pictures; a tile with no key can't be corrected.
        if(Profiles.kids(this)||key==null||key.isEmpty()||udn==null){finish();return;}
        if(name==null)name=query;if(year==null)year="";
        if(Posters.on(this))search(query,year);else show();
    }
    @Override protected void onDestroy(){ticket++;worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    private Executor work(){Executor t=testWorker;return t!=null?t:worker;}

    // --- pages
    /** The choices: TMDB's candidates (or why there are none), another title, films or programmes, no picture, automatic. */
    private void show(){
        typing=false;String was=focusedWords();
        page(ACTION,name+(year.isEmpty()?"":" ("+year+")"));rows.clear();
        Posters posters=Posters.get(this);org.json.JSONObject chosen=posters.fix(udn,key);long chosenId=chosen==null||chosen.optBoolean("none")?-1:chosen.optLong("id",-1);
        if(!Posters.on(this)){
            card();text("Posters from TMDB aren't set up on this TV, so there are no other pictures to choose from. An adult adds the free key in Settings > Library & Media > Film posters.",17);
        }else{
            group((tv?"Programmes":"Films")+" called “"+query+"”");card();
            if(searching)busy("Looking on TMDB…");
            else if(problem!=null){text(problem,17);row("Try again",null,false,()->search(query,year));}
            else if(found.isEmpty())text("TMDB has nothing called “"+query+"”. Try a different title, or look in "+(tv?"films":"programmes")+".",17);
            else for(Tmdb.Match m:found){Button b=row(m.title+(m.year.isEmpty()?"":" ("+m.year+")"),m.id==chosenId?"Chosen":null,false,()->choose(m));b.setTag(m);rows.put(m.id,b);}
            row("Search for a different title",null,true,this::typeTitle);
            row("Look in "+(tv?"films":"programmes")+" instead",null,false,()->{tv=!tv;search(query,"");});
        }
        group("Or");card();
        row("No picture",chosen!=null&&chosen.optBoolean("none")?"Chosen":null,false,()->choose(null));
        if(chosen!=null)row("Use the automatic picture",null,false,()->{Posters.get(this).automatic(udn,key);note("The automatic picture is back");close();});
        text("Your choice is kept for this title on this TV, for everyone. Only the title is sent to TMDB. "+Tmdb.ATTRIBUTION,15);
        refocus(was);thumbnails();
    }
    /** Another title, typed with the TV's keyboard (or a phone keyboard paired with the TV); Search or Back. */
    private void typeTitle(){
        typing=true;page("Search for a different title",name);card();
        EditText field=new EditText(this);field.setSingleLine(true);field.setText(query);field.setSelection(field.getText().length());field.setHint("Type the title as it should be");
        field.setTextColor(SettingsGlass.TEXT);field.setHintTextColor(SettingsGlass.SECONDARY);field.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(28));field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setContentDescription("Title to search for");field.setSaveEnabled(false);field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);field.setPadding(px(24),px(14),px(24),px(14));
        column.addView(field,rowParams());
        Runnable go=()->{String q=field.getText().toString().trim();if(q.isEmpty())return;search(q,"");};
        field.setOnEditorActionListener((v,action,e)->{go.run();return true;});
        row("Search",null,false,go);
        text("Only the words you type are sent to TMDB.",15);
        main.post(field::requestFocus);
    }
    @Override public void onBackPressed(){if(typing){show();return;}close();}

    // --- work
    private void search(String q,String nearYear){
        query=q;searching=true;problem=null;found=new ArrayList<>();int mine=++ticket;show();
        boolean asTv=tv;Context app=getApplicationContext();
        try{work().execute(()->{
            List<Tmdb.Match> list=new ArrayList<>();String why=null;
            try{list=Posters.get(app).candidates(asTv,q,nearYear,MAX);}
            catch(Tmdb.Busy b){why="TMDB asked Unified TV to wait a moment. Try again shortly.";}
            catch(Exception e){why=Posters.on(app)?"TMDB isn't answering right now. Check this TV's internet connection, then try again.":null;}
            List<Tmdb.Match> result=list;String said=why;
            main.post(()->{if(mine!=ticket||isFinishing())return;searching=false;found=result;problem=said;show();});
        });}catch(RejectedExecutionException ignored){}
    }
    /** Each candidate's own small poster beside its name, when it arrives; never another title's picture. */
    private void thumbnails(){
        if(searching||found.isEmpty())return;int mine=ticket;Posters posters=Posters.get(this);
        for(Tmdb.Match m:found){Button b=rows.get(m.id);if(b==null)continue;
            try{work().execute(()->{Bitmap art=posters.candidatePoster(m);if(art==null)return;
                main.post(()->{if(mine!=ticket||b.getTag()!=m||isFinishing())return;Drawable d=new BitmapDrawable(getResources(),art);d.setBounds(0,0,px(52),px(78));
                    Drawable[] now=b.getCompoundDrawablesRelative();b.setCompoundDrawablesRelative(d,null,now[2],null);b.setCompoundDrawablePadding(px(16));});});}
            catch(RejectedExecutionException ignored){}
        }
    }
    private void choose(Tmdb.Match m){
        Posters.get(this).choose(udn,key,m);
        note(m==null?"No picture for "+name:"Picture changed to "+m.title+(m.year.isEmpty()?"":" ("+m.year+")"));
        close();
    }
    /** The focused view straight from the window's views (as SettingsActivity reads it). */
    @Override public View getCurrentFocus(){View decor=getWindow().peekDecorView();return decor==null?null:decor.findFocus();}
    /** After the list is rebuilt, focus goes back to the row it was on (by its words), else the first row. */
    private String focusedWords(){View f=getCurrentFocus();return f instanceof TextView&&isInside(f)?((TextView)f).getText().toString():null;}
    private void refocus(String words){
        if(words==null)return;
        body.post(()->{for(View v:LibraryViews.all(body)){if(v instanceof Button&&words.contentEquals(((Button)v).getText())){v.requestFocus();return;}}});
    }
    /** For tests: the candidates on screen and whether a search is running. */
    List<Tmdb.Match> candidates(){return Collections.unmodifiableList(found);}
    boolean searching(){return searching;}

    /** Small helper: every view inside a group, depth first. */
    static final class LibraryViews{
        static List<View> all(View v){List<View> out=new ArrayList<>();add(v,out);return out;}
        private static void add(View v,List<View> out){out.add(v);if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++)add(((android.view.ViewGroup)v).getChildAt(i),out);}
    }
}
