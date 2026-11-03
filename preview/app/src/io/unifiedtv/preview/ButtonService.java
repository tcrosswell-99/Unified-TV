package io.unifiedtv.preview;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * OD-31's "Unified TV remote button" (RemoteButton has the decisions and the honest limits). Launcher app only: declared
 * in launcher/AndroidManifest.xml with res/xml/remote_button.xml (key events only: no event types, no window content).
 *
 * <p>{@link #onKeyEvent}: only the chosen keycode, and only while the household has the option on, is consumed (its
 * press and its release) and brings Unified TV's Home to the front; every other key returns false, untouched. While the
 * When the TV turns on page is choosing a button, the first allowed button pressed is reported to it instead. Nothing
 * here logs or keeps a key. Key filtering is dropped from the service's info whenever the option is off, so Android then
 * sends it no keys at all (some TCL builds lose volume's hold-to-repeat while any service filters keys).
 *
 * <p>Waking from standby: while the option is on, it also hears "the screen is on again" and, once per wake, brings
 * Unified TV forward ({@link RemoteButton#wake(Context, boolean)}), unless OD-23's own watcher is running.
 */
public final class ButtonService extends AccessibilityService {
    /** The service Android has bound now, or null; RemoteButton asks it to follow the switch. */
    private static volatile java.lang.ref.WeakReference<ButtonService> connected = new java.lang.ref.WeakReference<>(null);
    /** The last wake decision; read by tests and owner checks only. */
    static volatile TurnOn.Skip lastWake;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final TurnOn.Wake wake = new TurnOn.Wake();
    private final Runnable look = this::look;
    private BroadcastReceiver screen;
    /** When the chosen button was last pressed (elapsed real time), for "the screen came on just after it". */
    private long seenAt = Long.MIN_VALUE / 2;
    /** A key whose press was consumed: its release is consumed too, so the TV never acts on half a press. */
    private int swallowUp = KeyEvent.KEYCODE_UNKNOWN;
    /**
     * The household's choice, read once on connecting and again whenever it changes (the preferences listener and
     * {@link #refilter}). With the option on, every key on the TV passes through {@link #onKeyEvent} on Unified TV's main
     * thread, which an accessibility service can't move; so it reads only these fields: no binder call, no preferences,
     * no allocation.
     */
    private volatile boolean offered, on;
    private volatile int chosen = KeyEvent.KEYCODE_UNKNOWN;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = (p, key) -> {
        if (key == null || RemoteButton.ON.equals(key) || RemoteButton.KEY.equals(key)) { read(); filter(); }
    };
    private SharedPreferences prefs;
    private final Runnable home = this::home;
    private final Runnable refilterNow = this::filter;
    /** Tests: how often this service asked for its preferences, package manager or content resolver. */
    static final java.util.concurrent.atomic.AtomicInteger CONTEXT_READS = new java.util.concurrent.atomic.AtomicInteger();
    /** Tests and owner checks: whether the service last asked Android for keys. */
    static volatile Boolean lastFilter;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = new java.lang.ref.WeakReference<>(this);
        offered = RemoteButton.offered(this);   // the app never changes flavour while it runs
        if (prefs == null) try { prefs = getSharedPreferences(HouseholdSettings.PREFS, 0); prefs.registerOnSharedPreferenceChangeListener(changes); } catch (RuntimeException e) { prefs = null; }
        read();
        filter();
        if (screen != null) return;
        screen = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { heard(i == null ? null : i.getAction()); } };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_USER_PRESENT);
        // System broadcasts only; no other app can reach this receiver.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen, f);
        } catch (RuntimeException e) { screen = null; }
    }

    /** Follows the switch: asks Android for keys only while they could matter ({@link RemoteButton#wantsKeys}). */
    static void refilter() { ButtonService s = connected.get(); if (s != null) s.main.post(s.refilterNow); }
    private void read() { on = RemoteButton.on(this); chosen = RemoteButton.key(this); }
    private void filter() {
        read();
        main.removeCallbacks(refilterNow);
        // A choice under way stops by itself: look again then, so keys aren't filtered a moment longer than needed.
        long now = SystemClock.uptimeMillis(), until = RemoteButton.chooseUntil();
        if (until > now) main.postAtTime(refilterNow, until + 1);   // only ahead: never a loop once it has run out
        boolean keys = offered && (RemoteButton.chooser(now) != null || (on && chosen != KeyEvent.KEYCODE_UNKNOWN));
        lastFilter = keys;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) return;
            int flags = keys ? info.flags | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS : info.flags & ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            if (flags == info.flags) return;
            info.flags = flags; setServiceInfo(info);
        } catch (RuntimeException ignored) { /* the declared info stays; onKeyEvent still passes everything else on */ }
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        try { return handle(e); } catch (RuntimeException ex) { return false; }
    }
    private boolean handle(KeyEvent e) {
        if (e == null) return false;
        int code = e.getKeyCode(); boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (!down && e.getAction() == KeyEvent.ACTION_UP && code == swallowUp && code != KeyEvent.KEYCODE_UNKNOWN) { swallowUp = KeyEvent.KEYCODE_UNKNOWN; return true; }
        if (!offered) return false;
        // Choosing: buttons that can't be chosen (Back, the arrows, OK…) reach the page as always; an allowed one is reported.
        RemoteButton.Chooser chooser = RemoteButton.chooser(SystemClock.uptimeMillis());
        if (chooser != null && Comeback.front() instanceof TurnOnChoiceActivity) {
            if (!RemoteButton.allowed(code)) return false;
            if (down && e.getRepeatCount() == 0) { swallowUp = code; chooser.chose(code); }
            return true;
        }
        if (code != chosen || !on) return false;   // every other key: one compare, passed on untouched
        Activity front = Comeback.front();
        RemoteButton.Press what = RemoteButton.press(true, true, chosen, code, HouseholdService.alarmOnScreen, front instanceof PlayerActivity && !front.isFinishing());
        if (what == RemoteButton.Press.PASS) return false;
        if (down && e.getRepeatCount() == 0) {
            seenAt = SystemClock.elapsedRealtime(); swallowUp = code;
            if (what == RemoteButton.Press.HOME) main.post(home);   // the launch itself runs after the key is answered
        }
        return true;
    }
    private void home() { try { startActivity(TurnOn.home(this)); } catch (RuntimeException ignored) { /* Android refused: nothing else */ } }

    // The counting seam for RemoteButtonTest: the key path must reach none of these.
    @Override public SharedPreferences getSharedPreferences(String name, int mode) { CONTEXT_READS.incrementAndGet(); return super.getSharedPreferences(name, mode); }
    @Override public android.content.pm.PackageManager getPackageManager() { CONTEXT_READS.incrementAndGet(); return super.getPackageManager(); }
    @Override public android.content.ContentResolver getContentResolver() { CONTEXT_READS.incrementAndGet(); return super.getContentResolver(); }

    private void heard(String action) {
        if (Intent.ACTION_SCREEN_OFF.equals(action)) { main.removeCallbacks(look); wake.off(); return; }
        if (!Intent.ACTION_SCREEN_ON.equals(action) && !Intent.ACTION_USER_PRESENT.equals(action)) return;
        if (!RemoteButton.watchesWake(this)) return;
        if (wake.wake(SystemClock.elapsedRealtime())) { main.removeCallbacks(look); main.postDelayed(look, TurnOn.SETTLE_MS); }
    }
    /** Once per wake, after the TV has settled. */
    private void look() {
        if (!RemoteButton.watchesWake(this)) return;
        lastWake = RemoteButton.wake(this, SystemClock.elapsedRealtime() - seenAt <= RemoteButton.WAKE_KEY_MS + TurnOn.SETTLE_MS);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* none asked for */ }
    @Override public void onInterrupt() {}
    @Override public boolean onUnbind(Intent intent) { stop(); return super.onUnbind(intent); }
    @Override public void onDestroy() { stop(); super.onDestroy(); }
    private void stop() {
        if (connected.get() == this) connected = new java.lang.ref.WeakReference<>(null);
        if (prefs != null) try { prefs.unregisterOnSharedPreferenceChangeListener(changes); } catch (RuntimeException ignored) { /* gone */ }
        prefs = null;
        main.removeCallbacksAndMessages(null);
        if (screen != null) try { unregisterReceiver(screen); } catch (RuntimeException ignored) { /* already gone */ }
        screen = null;
    }
}
