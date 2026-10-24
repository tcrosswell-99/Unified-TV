package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;
import java.util.*;

/** Private server catalogue. A child's token must belong to a non-admin restricted server user. */
public final class LibraryActivity extends Screen {
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private final ExecutorService artwork=Executors.newSingleThreadExecutor();
    private final android.os.Handler focusTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private Future<?> heroTask;
    private JSONObject config; private int generation;
    private boolean shelf;
    private ImageView hero; private int focusedImage;
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private String currentRequest="";
    private void navigate(String path){history.push(currentRequest);shelf=false;load(path);}
    @Override public void onCreate(Bundle b){super.onCreate(b);shelf=getIntent().getBooleanExtra("shelf",false);
        try {if("dlna".equals(getSharedPreferences("unified-tv-preview",0).getString("librarySource",""))||!new Vault(this).read(Vault.mediaKey(this)).optBoolean("enabled")){startActivity(new Intent(this,DlnaLibraryActivity.class));finish();return;}}catch(Exception e){page("Your Films & TV");text("Unlock your saved library in Settings.",20);button("Back",this::finish);return;}load("");}
    private String rootQuery(String type,String search){
        return "/Users/"+LocalApi.segment(config.optString("user"))+"/Items?Recursive=true&IncludeItemTypes="+type+
            "&Fields=Overview,UserData&EnableImageTypes=Primary,Backdrop&Limit="+(shelf?"12":"24")+
            "&SortBy=DateCreated&SortOrder=Descending"+(search.isEmpty()?"":"&SearchTerm="+LocalApi.segment(search));
    }
    private void load(String request){
        currentRequest=request;
        int current=++generation; page("Your Films & TV");
        android.view.View content=((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        ((android.view.ViewGroup)content.getParent()).removeView(content);
        android.widget.FrameLayout scene=new android.widget.FrameLayout(this);
        hero=new ImageView(this);hero.setScaleType(ImageView.ScaleType.CENTER_CROP);scene.addView(hero,new android.widget.FrameLayout.LayoutParams(-1,-1));
        content.setBackgroundColor(0xb5101827);scene.addView(content);setContentView(scene);
        text(shelf?"Recently added · choose a title or View All":"Films, series and episodes from your own library",20);
        try {config=new Vault(this).read(Vault.mediaKey(this));}catch(Exception e){unavailable("Unlock your media connection again in Settings.");return;}
        if(!config.optBoolean("enabled")){unavailable("Connect your library in Settings > Connections.");return;}
        if(request.isEmpty()){
            button(shelf?"View All":"Films",()->navigate(rootQuery("Movie","")));
            button("Series",()->navigate(rootQuery("Series","")));
            button("Genres",()->navigate("/Genres?UserId="+LocalApi.segment(config.optString("user"))+"&IncludeItemTypes=Movie,Series&Recursive=true"));
            button("Search",()->{EditText search=new EditText(this);search.setSingleLine(true); new AlertDialog.Builder(this).setTitle("Search your library").setView(search).setNegativeButton("Cancel",null)
                .setPositiveButton("Search",(d,w)->navigate(rootQuery("Movie,Series",search.getText().toString()))).show();});
        }
        String initialPath=request.isEmpty()?rootQuery("Movie,Series",getIntent().getStringExtra("search")==null?"":getIntent().getStringExtra("search")):request;
        final String path=shelf?initialPath:pagePath(initialPath,offset(initialPath));
        TextView state=text("Loading your library…",22);
        worker.execute(()->{
            try {
                JSONObject me=LocalApi.get(config,"/Users/Me");
                MediaAccount.require(me,config.optString("user"),EditLock.child(this));
                JSONObject result=LocalApi.get(config,path);JSONArray items=result.optJSONArray("Items");
                runOnUiThread(()->{
                    if(!active(current))return;
                    state.setText(items==null||items.length()==0?"No titles available for this profile":"");
                    if(path.startsWith("/Genres"))renderGenres(items);else render(items,current);
                    if(!shelf){
                        int start=offset(path),total=result.optInt("TotalRecordCount");
                        if(start>0)button("Previous page",()->navigate(pagePath(path,Math.max(0,start-24))));
                        if(start+24<total)button("Next page",()->navigate(pagePath(path,start+24)));
                    }
                });
            }catch(Exception e){runOnUiThread(()->{if(active(current)){state.setText("Library unavailable · check this profile's connection and server permissions.");button("Retry",()->load(request));}});}
        });
    }
    private boolean active(int g){return g==generation&&!isFinishing()&&!isDestroyed();}
    private static int offset(String path){try{return Integer.parseInt(android.net.Uri.parse(path).getQueryParameter("StartIndex"));}catch(Exception e){return 0;}}
    private static String pagePath(String path,int offset){
        android.net.Uri uri=android.net.Uri.parse(path);android.net.Uri.Builder builder=uri.buildUpon().clearQuery();
        for(String key:uri.getQueryParameterNames())if(!key.equals("Limit")&&!key.equals("StartIndex"))for(String value:uri.getQueryParameters(key))builder.appendQueryParameter(key,value);
        return builder.appendQueryParameter("Limit","24").appendQueryParameter("StartIndex",""+offset).build().toString();
    }
    private void renderGenres(JSONArray items){
        if(items!=null)for(int n=0;n<items.length();n++){
            JSONObject genre=items.optJSONObject(n);if(genre==null)continue;
            button(genre.optString("Name","Genre"),()->navigate(rootQuery("Movie,Series","")+"&GenreIds="+LocalApi.segment(genre.optString("Id"))));
        }
        button("Back",this::onBackPressed);
    }
    private void unavailable(String message){text(message,24);button("Open Settings",()->{EditLock.ask(this,()->startActivity(new Intent(this,SettingsActivity.class)));});button("Back to Home",this::finish);}
    private void render(JSONArray items,int current){
        if(items==null)return;
        HorizontalScrollView shelfView=null; LinearLayout row=null;
        if(shelf){shelfView=new HorizontalScrollView(this); row=new LinearLayout(this);shelfView.addView(row);column.addView(shelfView);}
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            String id=item.optString("Id"),name=item.optString("Name","Untitled");
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(8,8,8,14);
            ImageView poster=new ImageView(this);poster.setContentDescription(name);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(poster,new LinearLayout.LayoutParams(dp(160),dp(220)));
            Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(19);b.setContentDescription("Open "+name);card.addView(b,new LinearLayout.LayoutParams(dp(180),-2));
            b.setOnClickListener(v->{
                String type=item.optString("Type");
                if(type.equals("Series")){history.push("");shelf=false;load("/Shows/"+LocalApi.segment(id)+"/Seasons?UserId="+LocalApi.segment(config.optString("user")));}
                else if(type.equals("Season")){history.push("/Shows/"+LocalApi.segment(item.optString("SeriesId"))+"/Seasons?UserId="+LocalApi.segment(config.optString("user")));load("/Shows/"+LocalApi.segment(item.optString("SeriesId"))+"/Episodes?SeasonId="+LocalApi.segment(id)+"&UserId="+LocalApi.segment(config.optString("user")));}
                else startActivity(new Intent(this,PlayerActivity.class).putExtra("item",id).putExtra("title",name));
            });
            if(shelf)row.addView(card);else {
                if(i%4==0){row=new LinearLayout(this);column.addView(row);}
                row.addView(card,new LinearLayout.LayoutParams(0,-2,1));
            }
            b.setOnFocusChangeListener((v,has)->{
                if(!has)return;
                // Clear the previous identity before an asynchronous same-title image request.
                final int ticket=++focusedImage;
                hero.setImageDrawable(null);
                if(heroTask!=null)heroTask.cancel(true);
                focusTimer.removeCallbacksAndMessages(null);
                focusTimer.postDelayed(()->heroTask=artwork.submit(()->{
                    try{
                        JSONArray backdrops=item.optJSONArray("BackdropImageTags");
                        String kind=backdrops!=null&&backdrops.length()>0?"Backdrop/0":"Primary";
                        byte[] bytes=LocalApi.bytes(config,"/Items/"+LocalApi.segment(id)+"/Images/"+kind+"?MaxWidth=1280&MaxHeight=720&Quality=75");
                        android.graphics.Bitmap background=Images.decode(bytes,1280,720);
                        runOnUiThread(()->{if(active(current)&&ticket==focusedImage)hero.setImageBitmap(background);});
                    }catch(Exception ignored){}
                }),135);
            });
            if(item.optJSONObject("ImageTags")!=null&&item.optJSONObject("ImageTags").has("Primary"))worker.execute(()->{
                try{
                    byte[] bytes=LocalApi.bytes(config,"/Items/"+LocalApi.segment(id)+"/Images/Primary?MaxWidth=320&MaxHeight=440&Quality=80");
                    android.graphics.Bitmap image=Images.decode(bytes,320,440);
                    runOnUiThread(()->{if(active(current))poster.setImageBitmap(image);});
                }catch(Exception ignored){}
            });
        }
        button("Back",this::onBackPressed);
    }
    @Override public void onBackPressed(){if(!history.isEmpty())load(history.pop());else super.onBackPressed();}
    @Override protected void onDestroy(){generation++;focusTimer.removeCallbacksAndMessages(null);artwork.shutdownNow();worker.shutdownNow();super.onDestroy();}
}
