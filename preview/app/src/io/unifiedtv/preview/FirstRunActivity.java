package io.unifiedtv.preview;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SET-010 guided setup: five short, optional steps in the Settings sidebar over a still Home (OD-19, a GlassScreen):
 * Welcome, the remote (SET-071 key help as one glance, with a live demonstration), your apps (Build My TV), your films
 * and TV (the media server; no address is ever typed) and Finish (what is on Home now, and what was set up or skipped).
 * Each step's first line says what to do there, or that there is nothing to do. "Skip the guided setup" is on every step
 * with something to choose (the remote step is a look only, so it has just Back and Next), and Back (or Left with nothing
 * further left) goes one step back, never out of Unified TV, so nobody is trapped; progress is saved at every step, so
 * leaving (Home, Back, another app) resumes where it was from Settings. Never in Kids. With {@link #ONLY} it shows one
 * part on its own (SET-015) and leaves the saved progress alone.
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
    // Owner, TCL build run 99: each step's first line says what to do, or that there is nothing to do; nothing that only
    // informs can be focused, so a row never looks like a task when it isn't one.
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
        text("Press Start for a short tour: your remote, your apps, and your films and TV. Or skip it and go straight to Home.",18);
        text("Each step is optional, and everything stays in Settings for later. Nothing here asks for an account, a password or an address.",15);
        row("Start",null,true,()->go(FirstRun.REMOTE));
        skipRow();
    }
    /**
     * A look, not a task: one screen with no scrolling, the buttons as a two-column glance that can't be focused, and only
     * Back and Next to press. A button pressed here still says what it does on Home (SET-071's live demonstration).
     */
    private void remote(String sub){
        page("Your remote",sub);
        text(RemoteHelp.LOOK_ONLY,18);
        boolean launcher=DefaultHome.available(this);
        card();glance(RemoteHelp.glance(launcher,launcher&&DefaultHome.isDefault(this)));
        text(RemoteHelp.TV_KEYS,15);
        column=body;demo=text("",15);
        next();
    }
    private void apps(String sub){
        page("Your apps",sub);card();
        String build=HubBuilder.load(prefs,Profiles.current(prefs)).status;
        text(HubBuilder.COMPLETED.equals(build)?"Build My TV is done. Press Next, or open it again to change your apps."
            :HubBuilder.IN_PROGRESS.equals(build)?"Build My TV is part done. Press Continue Build My TV, or press Next to leave it for later."
            :"Want to choose the apps on Home? Press Open Build My TV. Or press Next to keep Home as it is.",18);
        text("Build My TV finds the apps already on this TV. Nothing is installed without your OK.",15);
        row(HubBuilder.IN_PROGRESS.equals(build)?"Continue Build My TV":HubBuilder.COMPLETED.equals(build)?"Open Build My TV again":"Open Build My TV",null,true,()->BuildMyTvActivity.start(this));
        next();
        skipRow();
    }
    private void library(String sub){
        page("Your films and TV",sub);card();
        boolean ready=HomeShelf.configured(this);
        if(ready){text("Your Films & TV is set up from "+MediaSources.name(this,MediaSources.library(this))+". Nothing to do: press Next.",18);
            row("Change the media server or folders",null,true,this::findServer);}
        else{text("Have films and TV on a home media server, such as a NAS? Press Find my media server. If not, press Next.",18);
            text("Unified TV looks for it on your home network. You never type an address. Home works without one, and you can set it up later in Library & Media.",15);
            row("Find my media server",null,true,this::findServer);}
        next();
        skipRow();
    }
    /** What Home has now, by name, and what was set up (or skipped) along the way. */
    private void finishPage(String sub){
        page("All set",sub);card();
        List<String> home=onHome();
        if(home.isEmpty())text("Home has no apps yet. Add them any time with Build My TV, in Settings > Apps & Shortcuts.",18);
        else{text("On Home: "+String.join(", ",home)+".",18);text("You can change these any time: hold OK on an app on Home.",15);}
        group("Set up in this guide");card();
        AppCatalogue.Entry music=AppCatalogue.entry(HubBuilder.load(prefs,Profiles.current(prefs)).preferred(AppCatalogue.MUSIC));
        if(music!=null)text("Music: "+music.name,15);
        text("Films & TV: "+(HomeShelf.configured(this)?"set up from "+MediaSources.name(this,MediaSources.library(this)):SKIPPED),15);
        int photos=HubBuilder.photoFolders(this);
        text("Photos: "+(photos>0?(photos==1?"1 folder chosen":photos+" folders chosen"):SKIPPED),15);
        text("Smart home: "+(HubBuilder.homeAssistant(this)?"Home Assistant is set up":SKIPPED),15);
        column=body;
        text("Run any of this again from Settings > Updates & About > Guided setup.",15);
        row("Go to Home",null,false,this::done);
    }
    static final String SKIPPED="skipped — set up any time in Settings";
    /** The apps on Home now, by the names Home shows, in Home's order (as Apps shows them). */
    private List<String> onHome(){
        String room=prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");
        List<String> out=new ArrayList<>();
        for(String id:DockPolicy.visible(HouseholdSettings.dock(this,room),DockCapacity.get(prefs,Profiles.current(prefs))))out.add(DockCapacity.name(this,id));
        return out;
    }
    private void findServer(){
        try{startActivity(new Intent(this,DlnaLibraryActivity.class).putExtra("setup",true));}catch(RuntimeException e){note("Films & TV setup couldn't open");}
    }
    /** "Next", or "Done" for a single part, which then closes. */
    private void next(){if(only!=null)row("Done",null,false,this::close);else row("Next",null,true,()->go(FirstRun.next(step)));}
    private void skipRow(){if(only!=null)return;group("Not now");card();row("Skip the guided setup",null,false,this::skipAll);}
    /** The remote's buttons as a two-column glance: each pair is words to read, never something to press. */
    private void glance(List<RemoteHelp.Key> keys){
        LinearLayout row=null;
        for(int i=0;i<keys.size();i++){
            if(i%2==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setBaselineAligned(false);column.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            row.addView(pair(keys.get(i)),new LinearLayout.LayoutParams(0,-2,1));
        }
    }
    private View pair(RemoteHelp.Key k){
        LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setPadding(px(12),px(10),px(12),px(10));
        cell.setFocusable(false);cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);cell.setContentDescription(k.button+": "+k.meaning);
        TextView key=label(k.button,22,SettingsGlass.TEXT);key.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD));
        android.graphics.drawable.GradientDrawable cap=new android.graphics.drawable.GradientDrawable();cap.setCornerRadius(px(10));cap.setStroke(Math.max(1,px(2)),0x80FFFFFF);
        key.setBackground(cap);key.setPadding(px(14),px(4),px(14),px(4));key.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cell.addView(key,new LinearLayout.LayoutParams(-2,-2));
        TextView meaning=label(k.meaning,21,SettingsGlass.SECONDARY);meaning.setSingleLine(false);meaning.setMaxLines(2);meaning.setPadding(0,px(6),0,0);meaning.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cell.addView(meaning,new LinearLayout.LayoutParams(-1,-2));
        return cell;
    }
}
