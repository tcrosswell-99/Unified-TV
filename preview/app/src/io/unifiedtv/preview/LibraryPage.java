package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings > Library & Media, the parts added for v3.4.7: managing the media server (SET-031) and playback preferences
 * (SET-033), drawn inside the Settings sheet. Choosing folders stays in LibrarySetupActivity (the glass sidebar too).
 * Adults only for anything that changes the source; Kids see the playback choices read-only.
 */
final class LibraryPage {
    private final SettingsActivity a;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean scanning;private TextView scanLine;
    LibraryPage(SettingsActivity a){this.a=a;}
    void close(){worker.shutdownNow();}
    private SharedPreferences prefs(){return a.getSharedPreferences(HouseholdSettings.PREFS,0);}

    /** SET-031: only once a media server is set up (otherwise Library offers "Set up" and nothing else). */
    void source(){
        LibraryFolders.Library lib=MediaSources.library(a);if(!lib.ready||Profiles.kids(a))return;
        a.section("Media server");
        String name=MediaSources.name(a,lib);
        a.text(name+" · "+PrivacyData.count(lib.folders.size(),"folder","folders")+" in your library",18).setFocusable(false);
        String problem=MediaSources.lastProblem(a);
        scanLine=a.text(scanning?"Reading the folders again…":problem!=null?"Last check: "+problem:"",15);if(!scanning&&problem==null)scanLine.setVisibility(android.view.View.GONE);
        a.button("Name on this TV: "+name,()->EditLock.ask(a,()->rename(lib)));
        if(MediaSources.accountToo(a)){String now=prefs().getString(MediaSources.PREFERRED,MediaSources.FOLDERS);
            a.button("Films & TV come from: "+MediaSources.preferredLabel(now),()->EditLock.ask(a,()->new AlertDialog.Builder(a).setTitle("Films & TV come from").setItems(new String[]{MediaSources.preferredLabel(MediaSources.FOLDERS),MediaSources.preferredLabel(MediaSources.ACCOUNT)},
                (d,n)->{prefs().edit().putString(MediaSources.PREFERRED,n==0?MediaSources.FOLDERS:MediaSources.ACCOUNT).apply();a.refresh();}).show()));}
        a.button(scanning?"Rescanning…":"Rescan the folders now",this::rescan);
        a.button("Refresh posters and details",()->new AlertDialog.Builder(a).setTitle("Refresh posters and details?").setMessage("Posters, backgrounds and summaries kept on this TV are deleted and fetched again as you browse.")
            .setPositiveButton("Refresh",(d,w)->{PrivacyData.clearPosters(a);a.note("Posters will be fetched again.");}).setNegativeButton("Cancel",null).show());
        a.button("Forget this media server",()->EditLock.ask(a,()->new AlertDialog.Builder(a).setTitle("Forget "+name+"?")
            .setMessage("Its folders (Kids folders too) and the saved list of titles leave this TV. Watch history stays. Nothing on the media server is changed or deleted, and you can choose it again at any time.")
            .setPositiveButton("Forget",(d,w)->{try{MediaSources.forget(a,lib);a.note(name+" forgotten on this TV.");}catch(Exception e){a.note("Couldn't change this on the TV. Nothing was forgotten.");}a.refresh();}).setNegativeButton("Cancel",null).show()));
        a.text("The name is only for this TV; the media server keeps its own.",15);
    }
    private void rename(LibraryFolders.Library lib){
        EditText f=new EditText(a);f.setSingleLine(true);f.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);f.setHint(lib.name());f.setText(prefs().getString(MediaSources.NAME,""));f.setSaveEnabled(false);
        new AlertDialog.Builder(a).setTitle("Name on this TV").setMessage("Leave it empty to use the server's own name.").setView(f).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->{String n=f.getText().toString().trim();
                if(!MediaSources.validName(n)){a.note("Use up to "+MediaSources.NAME_MAX+" letters, numbers and spaces. Nothing was changed.");return;}
                if(n.isEmpty())prefs().edit().remove(MediaSources.NAME).apply();else prefs().edit().putString(MediaSources.NAME,n).apply();a.refresh();}).show();
    }
    private void rescan(){
        if(scanning)return;scanning=true;LibraryFolders.Library lib=MediaSources.library(a);android.content.Context app=a.getApplicationContext();
        if(scanLine!=null){scanLine.setText("Reading the folders again…");scanLine.setVisibility(android.view.View.VISIBLE);}
        worker.execute(()->{String words;
            try{int n=MediaSources.rescan(app,lib);words="Done: "+PrivacyData.count(n,"title","titles")+" in your library.";}
            catch(Exception e){words=MediaSources.problem(MediaSources.kind(e));}
            String w=words;a.runOnUiThread(()->{scanning=false;a.note(w);if(a.on(SettingsActivity.LIBRARY,null))a.refresh();});});
    }

    /**
     * SET-032 Favourites: how many each profile keeps, and clearing one profile's list after a confirmation (adult PIN when
     * one is set). Titles stay on the media server; only the Favourites row forgets them.
     */
    void favourites(){
        a.section("Favourites");
        SharedPreferences p=prefs();int adults=Favourites.count(p,Profiles.ADULT),kids=Favourites.count(p,Profiles.CHILD);
        a.button(favouritesLine(adults,kids),()->{
            if(adults+kids==0){a.note("No favourites yet. Hold OK on a title in Your Films & TV to add one.");return;}
            EditLock.ask(a,this::clearFavourites);});
        a.text("Each profile keeps its own. Add or remove one by holding OK on a title in Your Films & TV.",15);
    }
    static String favouritesLine(int adults,int kids){return "Favourites: Adults "+adults+" · Kids "+kids+(adults+kids>0?" · Clear":"");}
    private void clearFavourites(){
        SharedPreferences p=prefs();String[] who={Profiles.ADULT,Profiles.CHILD};
        java.util.List<String> names=new java.util.ArrayList<>();java.util.List<String> profiles=new java.util.ArrayList<>();
        for(String w:who){int n=Favourites.count(p,w);if(n>0){names.add("Clear "+Profiles.label(w)+"' favourites ("+n+")");profiles.add(w);}}
        if(profiles.isEmpty()){a.refresh();return;}
        new AlertDialog.Builder(a).setTitle("Clear favourites").setItems(names.toArray(new String[0]),(d,n)->{String w=profiles.get(n);int count=Favourites.count(p,w);
            new AlertDialog.Builder(a).setTitle("Clear "+Profiles.label(w)+"' favourites?").setMessage(PrivacyData.count(count,"title leaves","titles leave")+" "+Profiles.label(w)+"' Favourites row. The films and programmes stay on the media server, and the other profile's favourites are kept.")
                .setPositiveButton("Clear",(d2,w2)->{Favourites.clear(p,w);a.note(Profiles.label(w)+"' favourites cleared.");a.refresh();}).setNegativeButton("Cancel",null).show();})
            .setNegativeButton("Cancel",null).show();
    }
    /**
     * SET-034 manual picture choices ("Wrong picture?"): how many titles have a picture chosen by hand, and forgetting them
     * all after a confirmation, so those titles go back to the automatic picture. Adults only.
     */
    void pictures(){
        if(Profiles.kids(a))return;
        int n=Posters.get(a).choices();
        if(n==0){a.text("Picture choices: none. Hold OK on a title in Your Films & TV and choose Wrong picture? to pick its picture.",15);return;}
        a.button("Picture choices: "+n+" · Forget",()->EditLock.ask(a,()->new AlertDialog.Builder(a).setTitle("Forget "+PrivacyData.count(n,"picture choice","picture choices")+"?")
            .setMessage("Those titles go back to the automatic picture. Nothing on the media server changes.")
            .setPositiveButton("Forget",(d,w)->{Posters.get(a).forgetChoices();a.note("Picture choices forgotten.");a.refresh();}).setNegativeButton("Cancel",null).show()));
        a.text("Pictures you chose with Wrong picture? win over the automatic ones.",15);
    }

    /** SET-033: resume, autoplay and subtitle size; only for Your Films & TV's own player. */
    void playback(){
        boolean kids=Profiles.kids(a);
        a.section("Playback");
        String resume=PlaybackPrefs.resume(prefs()),subs=Accessibility.size(prefs().getString(PlaybackPrefs.SUBTITLES,Accessibility.NORMAL));
        if(kids){a.text("When a film was left part-way: "+PlaybackPrefs.resumeLabel(resume)+" · Next episode: "+(PlaybackPrefs.autoplay(prefs())?"plays by itself":"waits for you")+" · Subtitles: "+Accessibility.label(subs),16);a.text("An adult changes these in Adults.",15);return;}
        a.button("When a film was left part-way: "+PlaybackPrefs.resumeLabel(resume),()->{String[] names=new String[PlaybackPrefs.RESUMES.length];for(int i=0;i<names.length;i++)names[i]=PlaybackPrefs.resumeLabel(PlaybackPrefs.RESUMES[i]);
            new AlertDialog.Builder(a).setTitle("When a film was left part-way").setSingleChoiceItems(names,java.util.Arrays.asList(PlaybackPrefs.RESUMES).indexOf(resume),(d,n)->{prefs().edit().putString(PlaybackPrefs.RESUME,PlaybackPrefs.RESUMES[n]).apply();d.dismiss();a.refresh();}).setNegativeButton("Cancel",null).show();});
        a.toggle("Play the next episode automatically",PlaybackPrefs.AUTOPLAY,false,on->{});
        a.text("Off by default. When it's on, the next episode starts "+PlaybackPrefs.NEXT_SECONDS+" seconds after one ends; Back stops it.",15);
        a.button("Subtitle size: "+Accessibility.label(subs),()->{String[] names=new String[Accessibility.SIZES.length];for(int i=0;i<names.length;i++)names[i]=Accessibility.label(Accessibility.SIZES[i]);
            new AlertDialog.Builder(a).setTitle("Subtitle size").setSingleChoiceItems(names,java.util.Arrays.asList(Accessibility.SIZES).indexOf(subs),(d,n)->{prefs().edit().putString(PlaybackPrefs.SUBTITLES,Accessibility.SIZES[n]).apply();d.dismiss();a.refresh();}).setNegativeButton("Cancel",null).show();});
        a.text("For films with subtitles, in Your Films & TV. Other apps keep their own settings.",15);
    }
}
