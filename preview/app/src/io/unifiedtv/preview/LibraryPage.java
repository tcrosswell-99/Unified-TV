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
