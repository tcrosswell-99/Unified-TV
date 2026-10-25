package io.unifiedtv.preview;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * SET-010 guided setup: five short, optional steps in the Settings sidebar over a still Home (OD-19, a GlassScreen):
 * Welcome, the remote (SET-071 key help with a live demonstration), your apps (Build My TV), your films and TV (the media
 * server; no address is ever typed) and Finish. "Skip the guided setup" is on every step and Back goes one step back, so
 * nobody is trapped; progress is saved at every step, so leaving (Home, Back, another app) resumes where it was from
 * Settings. Never in Kids. With {@link #ONLY} it shows one part on its own (SET-015) and leaves the saved progress alone.
 */
/* OD-19: a setup screen, so the glass sidebar over a still Home (GlassScreen). */
public final class FirstRunActivity extends GlassScreen {
    /** Intent extra: one part only (FirstRun.REMOTE, APPS or LIBRARY), or absent for the whole guided setup. */
    static final String ONLY="only";
    private String step,only;private TextView demo;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        // Never in Kids: the setup changes the household's Home, so it is for Adults only.
        if(Profiles.kids(this)){finish();return;}
        only=FirstRun.part(getIntent().getStringExtra(ONLY));
        if(only!=null){step=only;return;}
        // Where it was: this screen's own saved step after a restart, else the saved progress, else Welcome.
        FirstRun.State saved=FirstRun.load(prefs);
        String kept=state==null?null:state.getString("step");
        step=FirstRun.STEPS.contains(kept)?kept:FirstRun.IN_PROGRESS.equals(saved.status)?saved.step:FirstRun.WELCOME;
        FirstRun.save(prefs,FirstRun.at(saved,step));
    }
    /** Drawn on every return, so Build My TV's or the library's progress shows as soon as the household comes back. */
    @Override protected void onResume(){super.onResume();if(!isFinishing())show();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("step",step);}
    String step(){return step;}

    // --- moving between steps
    private void go(String next){
        if(next==null){done();return;}
        step=next;if(only==null)FirstRun.save(prefs,FirstRun.at(FirstRun.load(prefs),step));show();
    }
    private void skipAll(){FirstRun.save(prefs,FirstRun.skipped(FirstRun.load(prefs)));close();}
    private void done(){if(only==null)FirstRun.save(prefs,FirstRun.finished());close();}
    /** Back: one step back; from Welcome (or a single part) it leaves. Leaving Welcome this way counts as "not now". */
    @Override public void onBackPressed(){
        if(only!=null){close();return;}
        String before=FirstRun.previous(step);
        if(before!=null){go(before);return;}
        FirstRun.State s=FirstRun.load(prefs);if(FirstRun.IN_PROGRESS.equals(s.status))FirstRun.save(prefs,FirstRun.skipped(s));
        close();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        // The remote step's live demonstration: say what the button just pressed does on Home; the key still works here.
        if(demo!=null&&FirstRun.REMOTE.equals(step)&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){String s=RemoteHelp.pressed(e.getKeyCode());if(s!=null)demo.setText(s);}
        return super.dispatchKeyEvent(e);
    }

    // --- the pages
    private void show(){
        demo=null;
        String sub=only!=null?"One part of the guided setup":"Step "+(FirstRun.STEPS.indexOf(step)+1)+" of "+FirstRun.STEPS.size()+" · optional";
        switch(step){
            case FirstRun.REMOTE:remote(sub);break;
            case FirstRun.APPS:apps(sub);break;
            case FirstRun.LIBRARY:library(sub);break;
            case FirstRun.FINISH:finishPage(sub);break;
            default:welcome(sub);break;
        }
    }
    private void welcome(String sub){
        page("Welcome to Unified TV",sub);card();
        text("A few short steps: how the remote works here, your apps, and your films and TV. Each one is optional, and everything stays in Settings for later.",18);
        text("Nothing here asks for an account, a password or an address.",15);
        row("Start",null,true,()->go(FirstRun.REMOTE));
        skipRow();
    }
    private void remote(String sub){
        page("Your remote",sub);card();
        text("Try a button: this line says what it does on Home.",18);
        demo=text("Up, Down, Right, OK or Menu",15);
        next();
        group("What the buttons do");card();
        for(RemoteHelp.Key k:RemoteHelp.keys(DefaultHome.available(this),DefaultHome.available(this)&&DefaultHome.isDefault(this)))info(k.button,k.meaning);
        skipRow();
    }
    private void apps(String sub){
        page("Your apps",sub);card();
        String build=HubBuilder.load(prefs,Profiles.current(prefs)).status;
        text(HubBuilder.COMPLETED.equals(build)?"Build My TV is done. You can open it again to change your choices.":"Build My TV asks a few questions, finds the apps already on this TV and helps you arrange the apps on Home.",18);
        text("Nothing is installed without your OK, and the places on Home don't all need filling.",15);
        row(HubBuilder.IN_PROGRESS.equals(build)?"Continue Build My TV":HubBuilder.COMPLETED.equals(build)?"Open Build My TV again":"Open Build My TV",null,true,()->BuildMyTvActivity.start(this));
        next();
        skipRow();
    }
    private void library(String sub){
        page("Your films and TV",sub);card();
        boolean ready=HomeShelf.configured(this);
        if(ready){text("Your Films & TV is set up from "+MediaSources.name(this,MediaSources.library(this))+".",18);
            row("Change the media server or folders",null,true,this::findServer);}
        else{text("Unified TV can look for a media server on your home network, such as a NAS. You never type an address.",18);
            text("No media server? Carry on: Home works without one, and you can set it up later in Library & Media.",15);
            row("Find my media server",null,true,this::findServer);}
        next();
        skipRow();
    }
    private void finishPage(String sub){
        page("All set",sub);card();
        String build=HubBuilder.load(prefs,Profiles.current(prefs)).status;
        text("Apps: "+(HubBuilder.COMPLETED.equals(build)?"Build My TV is done":HubBuilder.IN_PROGRESS.equals(build)?"Build My TV is part done":"not chosen yet"),18);
        text("Films & TV: "+(HomeShelf.configured(this)?"set up":"not set up yet"),18);
        text("Run any of this again from Settings > Updates & About > Guided setup.",15);
        row("Go to Home",null,false,this::done);
    }
    private void findServer(){
        try{startActivity(new Intent(this,DlnaLibraryActivity.class).putExtra("setup",true));}catch(RuntimeException e){note("Films & TV setup couldn't open");}
    }
    /** "Next", or "Done" for a single part, which then closes. */
    private void next(){if(only!=null)row("Done",null,false,this::close);else row("Next",null,true,()->go(FirstRun.next(step)));}
    private void skipRow(){if(only!=null)return;group("Not now");card();row("Skip the guided setup",null,false,this::skipAll);}
    /** A read-only row: focusable so the remote can move through the list (and scroll it), with nothing to switch. */
    private void info(String button,String meaning){
        Button b=row(button+" · "+meaning,null,false,()->{});b.setContentDescription(button+": "+meaning);b.setMaxLines(4);
        b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
}
