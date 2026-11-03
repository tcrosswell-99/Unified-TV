package io.unifiedtv.preview;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.os.Bundle;
import android.provider.Settings;

/**
 * OD-23 (owner, 25 October 2026): the opt-in "Open Unified TV when the TV turns on", for TVs that keep their own Home
 * button (the owner's TCL may). When the TV starts, or wakes from standby, Unified TV brings its own Home to the front,
 * once. Off by default, behind the adult PIN, in the launcher app only (never the preview), and not offered once the Home
 * button already opens Unified TV.
 *
 * <p>Module 03 (no remote interception or key reassignment) and SPEC 6.1: this never uses accessibility, never reads or
 * consumes a key, never watches for the TV's own Home and never reassigns or blocks the Home button, which still opens the
 * TV's own Home screen. The only signals are Android's own "the TV has started" (BOOT_COMPLETED, {@link TurnOnBoot}) and
 * "the screen is on again" (SCREEN_ON / USER_PRESENT, {@link TurnOnService}). Coming to the front from the background
 * needs Android's "Display over other apps" (SYSTEM_ALERT_WINDOW) permission, which the household grants; it is used only
 * for that: nothing is ever drawn over another app for this feature.
 *
 * <p>It never acts while another app is playing, during a call, while the Live TV guard is watching or while the smoke and
 * carbon monoxide alarm screen is up; once per wake (debounced), never on a restart of its own, and never retried, so it
 * cannot loop. Android gives apps no way to tell that the TV was woken by HDMI-CEC into another input (the TV's current
 * input and the wake reason are system-only), so that case is not detected: see docs/OWNER_TESTING.md.
 *
 * <p>The decisions ({@link #decide}, {@link Wake}, {@link #state}) are pure and tested with made-up facts (TurnOnTest).
 */
final class TurnOn {
    private TurnOn(){}
    /** The household's switch (plain preferences, not secret). Not in SettingsSchema: no reset or import changes it. */
    static final String PREF="turnOn.open";
    /** On the Intent that brings Home forward, so Home lands on its main screen as the Home button would. */
    static final String EXTRA="turnedOn";
    /** After a wake, wait for the TV to settle (its own Home and inputs), then look once. */
    static final long SETTLE_MS=2000;
    /** However the TV flickers on and off, Unified TV comes forward at most once in this time. */
    static final long DEBOUNCE_MS=30_000;
    static final String TITLE="Open Unified TV when the TV turns on";
    static final String PERMISSION_WORDS="It needs Android's \"Display over other apps\" for Unified TV. That is used only to bring Unified TV to the front; nothing is ever drawn over other apps.";
    static final String NO_SCREEN_WORDS="This TV has no screen for it. Once, from a computer with ADB connected to this TV, type:";
    static final String EXPLAIN="When the TV starts or wakes from standby, Unified TV comes to the front. The Home button still opens the TV's own Home screen.";

    enum State {
        ON("On"),NEEDS_PERMISSION("Needs permission"),OFF("Off");
        final String words;
        State(String words){this.words=words;}
    }
    /** The row's honest state: on only when switched on and Android allows coming to the front. */
    static State state(boolean switchedOn,boolean permitted){return !switchedOn?State.OFF:permitted?State.ON:State.NEEDS_PERMISSION;}
    static State state(Context c){return state(enabled(c),permitted(c));}

    /** Offered only in the launcher app, and not while the Home button already opens Unified TV. */
    static boolean offered(boolean launcherApp,DefaultHome.State home){return launcherApp&&home!=DefaultHome.State.OURS;}
    static boolean offered(Context c){
        if(!DefaultHome.available(c))return false;
        try{return offered(true,DefaultHome.state(c));}catch(RuntimeException e){return true;}
    }
    static boolean enabled(Context c){try{return c.getSharedPreferences(HouseholdSettings.PREFS,0).getBoolean(PREF,false);}catch(RuntimeException e){return false;}}
    static void enabled(Context c,boolean on){c.getSharedPreferences(HouseholdSettings.PREFS,0).edit().putBoolean(PREF,on).apply();}
    static boolean permitted(Context c){try{return Settings.canDrawOverlays(c);}catch(RuntimeException e){return false;}}
    /** The watcher runs only when switched on, offered and allowed; otherwise nothing of this feature is running. */
    static boolean active(Context c){return enabled(c)&&permitted(c)&&offered(c);}

    /** Starts or stops the small watcher to match the switch. Safe to call often (Settings, Home's onStart, boot). */
    static void sync(Context c){
        if(!DefaultHome.available(c))return;   // the preview has no watcher at all
        if(active(c))TurnOnService.start(c,null);
        else TurnOnService.stop(c);
    }

    // --- the one decision, pure
    /** Why Unified TV stays where it is; NONE means come to the front. */
    enum Skip{NONE,NOT_LAUNCHER,OFF,HOME_IS_OURS,NO_PERMISSION,ALARM,CALL,LIVE_TV,PLAYING,ALREADY_SHOWING}
    /** What was read at the moment of deciding. Plain fields so tests can make up any moment. */
    static final class Now {
        boolean launcherApp,switchedOn,homeIsOurs,permitted;
        boolean alarm,call,liveTv,playing,showing;
    }
    static Skip decide(Now n){
        if(!n.launcherApp)return Skip.NOT_LAUNCHER;
        if(!n.switchedOn)return Skip.OFF;
        if(n.homeIsOurs)return Skip.HOME_IS_OURS;
        if(!n.permitted)return Skip.NO_PERMISSION;
        // Anything the household is in the middle of wins: the alarm screen first, then a call, Live TV and playback.
        if(n.alarm)return Skip.ALARM;
        if(n.call)return Skip.CALL;
        if(n.liveTv)return Skip.LIVE_TV;
        if(n.playing)return Skip.PLAYING;
        if(n.showing)return Skip.ALREADY_SHOWING;
        return Skip.NONE;
    }

    /**
     * Once per wake. A new watcher starts disarmed, so starting it (the switch, Home opening, Android restarting it) never
     * acts by itself; the screen going off arms it, and the first "screen on" or "user present" after that is the wake.
     * Boot arms it once. Never twice within {@link #DEBOUNCE_MS}.
     */
    static final class Wake {
        private boolean armed;private long last=Long.MIN_VALUE/2;
        void off(){armed=true;}
        void boot(){armed=true;}
        /** True for the first wake signal after the screen was off (or after boot), at most once per DEBOUNCE_MS. */
        boolean wake(long now){
            if(!armed||now-last<DEBOUNCE_MS)return false;
            armed=false;last=now;return true;
        }
    }

    // --- reading the moment on the TV
    static Now read(Context c){
        Now n=new Now();
        n.launcherApp=DefaultHome.available(c);n.switchedOn=enabled(c);n.permitted=permitted(c);
        try{n.homeIsOurs=n.launcherApp&&DefaultHome.state(c)==DefaultHome.State.OURS;}catch(RuntimeException ignored){}
        n.alarm=HouseholdService.alarmOnScreen;
        n.liveTv=LiveTvGuard.running;
        n.showing=SHOWN.count()>0;
        try{AudioManager audio=c.getSystemService(AudioManager.class);
            if(audio!=null){int mode=audio.getMode();n.call=mode==AudioManager.MODE_IN_CALL||mode==AudioManager.MODE_IN_COMMUNICATION||mode==AudioManager.MODE_RINGTONE;
                n.playing=audio.isMusicActive();}}catch(RuntimeException ignored){}
        // With notification access, any other app's playing session counts too (a paused one does not).
        if(!n.playing)try{for(MediaController m:MediaWatch.sessions(c))if(!c.getPackageName().equals(m.getPackageName())&&NowPlaying.playing(MediaWatch.state(m))){n.playing=true;break;}}catch(RuntimeException ignored){}
        return n;
    }
    /** Unified TV's own Home, to the front: the same task, Home's main screen, nothing cleared but what sits above Home. */
    static Intent home(Context c){
        return new Intent(c,UnifiedPreviewActivity.class).putExtra(EXTRA,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }
    /** Decide now and, if nothing is in the way, come to the front once. Returns the reason for staying put. */
    static Skip act(Context c){
        Skip why=decide(read(c));
        // Android refusing (no permission after all) is not retried: the TV simply stays on its own Home.
        if(why==Skip.NONE)try{c.startActivity(home(c));}catch(RuntimeException ignored){}
        return why;
    }

    // --- whether any Unified TV screen is on show (started), so a wake that already shows it changes nothing
    static final Shown SHOWN=new Shown();
    static final class Shown implements Application.ActivityLifecycleCallbacks {
        private int started;
        synchronized int count(){return started;}
        synchronized void reset(){started=0;}
        @Override public synchronized void onActivityStarted(Activity a){started++;}
        @Override public synchronized void onActivityStopped(Activity a){started=Math.max(0,started-1);}
        @Override public void onActivityCreated(Activity a,Bundle b){}
        @Override public void onActivityResumed(Activity a){}
        @Override public void onActivityPaused(Activity a){}
        @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
        @Override public void onActivityDestroyed(Activity a){}
    }
    /** PreviewApp: counts this process's started screens (a fresh count for each new process). */
    static void track(Application app){SHOWN.reset();app.registerActivityLifecycleCallbacks(SHOWN);}

    /** The one-time computer instruction when this TV has no "Display over other apps" screen; the real package name. */
    static String adb(Context c){return "adb shell appops set "+c.getPackageName()+" SYSTEM_ALERT_WINDOW allow";}
    /** Android's own "Display over other apps" screen for this app, or null when this TV doesn't have one. */
    static Intent permissionScreen(Context c){
        Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+c.getPackageName()));
        try{return i.resolveActivity(c.getPackageManager())!=null?i:null;}catch(RuntimeException e){return null;}
    }
}
