package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.*;

/**
 * SET-010 first-run guided setup, its saved progress (SET-BUILD-06 style: versioned, local, resumable) and when it is
 * offered. Five optional steps in the Settings sidebar ({@link FirstRunActivity}): Welcome, the remote, your apps (Build
 * My TV), your films and TV (the media server), and Finish. The launcher app adds a sixth before Finish, When the TV turns
 * on ({@link TurnOnChoice}; owner, 3 November 2026), which the preview never has. Skip is on every step with something to choose; no account,
 * password or address is asked for. It opens by itself once, on a fresh install in Adults, and never in Kids; it is run
 * again, whole or one part at a time (SET-015), from Settings &gt; Updates &amp; About &gt; Guided setup.
 *
 * Saved under {@link #KEY} as {@code {schemaVersion, status, step}}. Anything unreadable is "not started"; an unknown
 * step resumes at Welcome. Nothing else is stored, and starting over never touches the dock, Build My TV's choices or
 * the library.
 */
final class FirstRun {
    private FirstRun(){}
    static final String KEY="setup.firstRun";
    static final int SCHEMA=1;
    static final String NOT_STARTED="not_started",IN_PROGRESS="in_progress",COMPLETED="completed",SKIPPED="skipped";
    static final String WELCOME="welcome",REMOTE="remote",APPS="apps",LIBRARY="library",TURN_ON="turnOn",FINISH="finish";
    /** The preview's steps. */
    static final List<String> STEPS=Collections.unmodifiableList(Arrays.asList(WELCOME,REMOTE,APPS,LIBRARY,FINISH));
    /** The launcher app's: When the TV turns on comes after the apps and films steps, just before the summary. */
    static final List<String> LAUNCHER_STEPS=Collections.unmodifiableList(Arrays.asList(WELCOME,REMOTE,APPS,LIBRARY,TURN_ON,FINISH));
    static List<String> steps(boolean launcherApp){return launcherApp?LAUNCHER_STEPS:STEPS;}
    static List<String> steps(android.content.Context c){return steps(TurnOnChoice.offered(c));}
    private static final Set<String> STATUSES=new HashSet<>(Arrays.asList(NOT_STARTED,IN_PROGRESS,COMPLETED,SKIPPED));

    static final class State {
        final String status,step;
        State(String status,String step){this.status=STATUSES.contains(status)?status:NOT_STARTED;this.step=LAUNCHER_STEPS.contains(step)?step:WELCOME;}
        String write(){try{return new JSONObject().put("schemaVersion",SCHEMA).put("status",status).put("step",step).toString();}catch(Exception e){return "";}}
        /** Step number, from 1, for "Step 2 of 5" (1 for a step this app doesn't have). */
        int number(){return number(STEPS);}
        int number(List<String> steps){return Math.max(0,steps.indexOf(step))+1;}
    }

    // --- pure
    static State parse(String saved){
        if(saved==null||saved.isEmpty())return new State(NOT_STARTED,WELCOME);
        try{JSONObject o=new JSONObject(saved);return new State(o.optString("status",NOT_STARTED),o.optString("step",WELCOME));}catch(Exception e){return new State(NOT_STARTED,WELCOME);}
    }
    /** The step after {@code step}; Finish has none (null). */
    static String next(String step){return next(step,STEPS);}
    static String next(String step,List<String> steps){int i=steps.indexOf(step);return i<0?WELCOME:i+1<steps.size()?steps.get(i+1):null;}
    /** The step before {@code step}; Welcome has none (null). */
    static String previous(String step){return previous(step,STEPS);}
    static String previous(String step,List<String> steps){int i=steps.indexOf(step);return i<=0?null:steps.get(i-1);}
    /** Moving to a step marks the setup in progress there (finished or skipped stays so: a re-run from Settings is a visit). */
    static State at(State s,String step){return new State(IN_PROGRESS.equals(s.status)||NOT_STARTED.equals(s.status)?IN_PROGRESS:s.status,step);}
    static State finished(){return new State(COMPLETED,FINISH);}
    static State skipped(State s){return new State(COMPLETED.equals(s.status)?COMPLETED:SKIPPED,s.step);}
    /** A fresh start from Settings: in progress at Welcome. */
    static State startOver(){return new State(IN_PROGRESS,WELCOME);}
    /** The Settings row's words for where the household left it. */
    static String status(State s){return status(s,STEPS);}
    static String status(State s,List<String> steps){
        switch(s.status){
            case IN_PROGRESS:return "Continue the guided setup (step "+s.number(steps)+" of "+steps.size()+")";
            case COMPLETED:return "Run the guided setup again";
            case SKIPPED:return "Run the guided setup (skipped earlier)";
            default:return "Run the guided setup";
        }
    }
    /** Which step a single-part re-run is for (SET-015); null for one that isn't a part on its own. */
    static String part(String step){return REMOTE.equals(step)||APPS.equals(step)||LIBRARY.equals(step)?step:null;}

    // --- this TV
    static State load(SharedPreferences p){return parse(p.getString(KEY,null));}
    static void save(SharedPreferences p,State s){p.edit().putString(KEY,s.write()).apply();}
    /**
     * Opens by itself once, on a fresh install: in Adults, never started, and nothing chosen yet (no dock saved, Build My
     * TV not started, and the earlier one-off Build My TV card never shown). An earlier install keeps its Home as it was.
     */
    static boolean shouldStart(SharedPreferences p){return NOT_STARTED.equals(load(p).status)&&HubBuilder.shouldOffer(p);}

    /** Opens the guided setup over Home: at the saved step (or Welcome), or one part only ({@code only} non-null). */
    static void start(Activity a,String only){
        try{a.startActivity(new Intent(a,FirstRunActivity.class).putExtra(FirstRunActivity.ONLY,only));}
        catch(RuntimeException e){android.widget.Toast.makeText(a,"The guided setup couldn't open",android.widget.Toast.LENGTH_LONG).show();}
    }
}
