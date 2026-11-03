package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Button;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * OD-31 (owner, 3 November 2026): "On the Sony, pressing the remote's TV button should turn the TV on if it's asleep and
 * then open Unified TV." Offered on any TV (the TCL too), in the launcher app only, off by default, and changed only by an
 * adult (PIN): Settings &gt; Appearance &amp; Home &gt; When the TV turns on &gt; Remote button that opens Unified TV.
 *
 * <p>How it works: the household chooses one button by pressing it (the TV button is suggested), and switches on a
 * separate, purpose-named accessibility service, {@link ButtonService} ("Unified TV remote button"), in the TV's own
 * accessibility settings. That service asks Android for key events only while the option is on (or while a button is
 * being chosen), reacts only to the chosen keycode, and lets every other key pass on untouched. It reads no screen
 * content and logs no keys. This narrows module 03's "no remote interception or key reassignment" for this one opt-in,
 * one-button case only; nothing else in Unified TV reads or consumes another app's keys.
 *
 * <p>Pressing the chosen button brings Unified TV's Home to the front ({@link TurnOn#home}: the same task, never a new
 * Home, no CLEAR_TOP). With a Unified TV screen in front it simply goes Home, except that a film playing in Unified TV's
 * own player is never closed by it. The alarm screen keeps priority.
 *
 * <p>From standby: Android wakes the TV on the button itself (the Sony does on most buttons), but an app can't tell which
 * button woke the TV, and the key itself may never reach an app while the screen is off. So while the option is on, the
 * service also listens for "the screen is on again" and, once per wake, brings Unified TV forward as OD-23 does (the same
 * {@link TurnOn.Wake} and the same reasons to stay put; OD-23's own watcher does it instead while that is on). That means
 * any wake opens Unified TV while this is on, and the UI says so. Android lets a switched-on accessibility service bring
 * its own app to the front, so this path needs no "Display over other apps".
 *
 * <p>The decisions ({@link #refusal}, {@link #press}, {@link #wake(TurnOn.Now, boolean)}, {@link #state}) are pure and
 * tested (RemoteButtonTest).
 */
final class RemoteButton {
    private RemoteButton() {}

    /** The household's choice (plain preferences, not secret; not in SettingsSchema, like OD-23's switch). */
    static final String ON = "remoteButton.on", KEY = "remoteButton.key";
    /** A chosen key seen this soon before the screen came on counts as the household pressing it. */
    static final long WAKE_KEY_MS = 3000;
    /** Choosing by pressing stops by itself after this long, so the service can never go on taking keys. */
    static final long CHOOSE_MS = 120_000;

    static final String TITLE = "Remote button that opens Unified TV";
    static final String SERVICE_LABEL = "Unified TV remote button";
    static final String INTRO = "Choose one button on your remote. Pressing it brings Unified TV's Home to the front, from any app. Unified TV doesn't react to any other button.";
    static final String SUGGEST = "Use the TV button (suggested)";
    static final String CHOOSE = "Choose a button by pressing it";
    static final String PRESS = "Press the button you want to use…";
    static final String PRESS_HINT = "Back stops without changing anything. Buttons the TV keeps for itself may not reach Unified TV until Android's switch below is on.";
    static final String SWITCH = "Open Unified TV with this button";
    static final String ANDROID_ROW = "Android's accessibility switch";
    static final String SWITCHED_ON = "Switched on ✓", NOT_ON = "Not switched on";
    static final String CHOOSE_FIRST = "Choose a button first.", NOT_CHOSEN = "Not chosen";
    static final String TIMED_OUT = "No button was pressed, so nothing changed.";
    static final String WAKE_WORDS = "If the TV is asleep, the button wakes it; Unified TV then opens as it does when the TV turns on. The TV's own Live TV may show for a moment first.";
    static final String ANY_WAKE = "While this is on, Unified TV comes to the front whenever the TV wakes from standby, whatever woke it: Android doesn't tell apps which button woke the TV.";
    static final String WARNING_MEANS = "Android warns that an accessibility service can observe what you do. Unified TV only reacts to the one button you chose; it doesn't read the screen or other keys.";
    static final String STEPS = "In the TV's settings, open Device Preferences > Accessibility > " + SERVICE_LABEL + ", switch it on and confirm. Then press Back to come back here.";
    static final String OPEN_SETTINGS = "Open the TV's accessibility settings";
    static final String NO_SCREEN = "This TV has no accessibility settings screen Unified TV can open. Once, from a computer with ADB connected to this TV, type:";
    static final String KIDS = "In Kids the button still opens Unified TV; only an adult can change it.";
    static final String PREVIEW_WORDS = "This is the Unified TV preview. It never reacts to remote buttons; the separate Unified TV app can.";

    enum State {
        OFF("Off"), NO_BUTTON("Choose a button"), NEEDS_ANDROID("Needs Android's switch"), ON("On");
        final String words;
        State(String words) { this.words = words; }
    }

    // --- pure
    /** Buttons you need to get around (Back, Home, the arrows, OK). */
    private static final Set<Integer> MOVES = new HashSet<>(Arrays.asList(
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP_LEFT,
        KeyEvent.KEYCODE_DPAD_UP_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B));
    /** Numbers and channels: Live TV and the guide use them. */
    private static final Set<Integer> NUMBERS = new HashSet<>(Arrays.asList(
        KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_NUMPAD_DOT, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_11, KeyEvent.KEYCODE_12,
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_LAST_CHANNEL));
    /** Players' buttons: Unified TV's own player and the apps' players use them. */
    private static final Set<Integer> PLAYERS = new HashSet<>(Arrays.asList(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_RECORD, KeyEvent.KEYCODE_MEDIA_EJECT, KeyEvent.KEYCODE_MEDIA_CLOSE, KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
        KeyEvent.KEYCODE_MEDIA_TOP_MENU, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MENU));

    /**
     * Why this button can't be the one, in plain words; null when it can. Back, Home, the arrows and OK; volume, mute,
     * power, input, voice, settings and the other buttons the TV keeps (spec 6.4, {@link RemoteLog#passToAndroid});
     * numbers and channels; and the players' buttons are all refused, so the choice can never take a button the household
     * needs.
     */
    static String refusal(int code) {
        if (code == KeyEvent.KEYCODE_UNKNOWN) return "Android doesn't name that button, so Unified TV can't tell it apart from others";
        if (MOVES.contains(code)) return "you need it to get around Unified TV and the TV";
        if (code == KeyEvent.KEYCODE_POWER || code == KeyEvent.KEYCODE_TV_POWER || code == KeyEvent.KEYCODE_SLEEP || code == KeyEvent.KEYCODE_SOFT_SLEEP
            || code == KeyEvent.KEYCODE_WAKEUP || RemoteLog.passToAndroid(code)) return "the TV keeps it for itself";
        if ((code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) || (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9)
            || NUMBERS.contains(code)) return "Live TV and the guide use the numbers and channels";
        if (PLAYERS.contains(code)) return "players use it";
        return null;
    }
    /**
     * Allowed or not, from a table made once: no boxing or allocation, so the service can ask it for a key arriving on
     * the main thread in constant time.
     */
    static boolean allowed(int code) { return code >= 0 && code < ALLOWED.length ? ALLOWED[code] : refusal(code) == null; }
    private static final boolean[] ALLOWED = new boolean[KeyEvent.getMaxKeyCode() + 1];
    static { for (int i = 0; i < ALLOWED.length; i++) ALLOWED[i] = refusal(i) == null; }

    /** What the line under the rows says when a pressed button is refused. */
    static String refused(int code) { return name(code) + " can't be used: " + refusal(code) + ". Press another button, or Back to stop."; }

    /** The button's name as the household knows it: "TV", "Red", "Guide"…; never a raw number when Android names it. */
    static String name(int code) {
        if (code == KeyEvent.KEYCODE_TV) return "TV";
        if (code == KeyEvent.KEYCODE_UNKNOWN) return "That button";
        String raw;
        try { raw = KeyEvent.keyCodeToString(code); } catch (RuntimeException | LinkageError e) { raw = ""; }
        if (raw == null || raw.isEmpty() || raw.matches("[0-9]+")) raw = "";
        String label = RemoteLog.label(code, raw);
        if (label.matches("[A-Z0-9_]+") && !label.matches("[0-9]+")) {
            String words = label.replace('_', ' ').toLowerCase(Locale.ROOT);
            return Character.toUpperCase(words.charAt(0)) + words.substring(1);
        }
        return label.isEmpty() || label.matches("[0-9]+") ? "Button " + code : label;
    }

    /** What the chosen button does when pressed. */
    enum Press { PASS, HOME, STAY }
    /**
     * PASS: not this feature's key (or it's off), so Android passes it on untouched. HOME: bring Unified TV's Home to the
     * front. STAY: the chosen key, consumed, but nothing moves: the alarm screen is up, or a film is playing in Unified TV's
     * own player (going Home would close it).
     */
    static Press press(boolean launcherApp, boolean on, int chosen, int keyCode, boolean alarm, boolean playerInFront) {
        if (!launcherApp || !on || chosen == KeyEvent.KEYCODE_UNKNOWN || keyCode != chosen || !allowed(chosen)) return Press.PASS;
        if (alarm || playerInFront) return Press.STAY;
        return Press.HOME;
    }

    /**
     * The wake decision, OD-23's own ({@link TurnOn#decide}) with the button's switch in place of OD-23's and no "Display
     * over other apps" needed (the accessibility service may bring its own app forward). When the chosen button was seen
     * just before the screen came on, the household pressed it: then Live TV or playing music don't hold Unified TV back,
     * as they don't when the button is pressed while the TV is on. The alarm screen and a call always do.
     */
    static TurnOn.Skip wake(TurnOn.Now n, boolean keyJustSeen) {
        TurnOn.Now m = new TurnOn.Now();
        m.launcherApp = n.launcherApp; m.switchedOn = true; m.permitted = true; m.homeIsOurs = n.homeIsOurs;
        m.alarm = n.alarm; m.call = n.call; m.liveTv = n.liveTv && !keyJustSeen; m.playing = n.playing && !keyJustSeen; m.showing = n.showing;
        return TurnOn.decide(m);
    }

    /** The row's honest state. */
    static State state(boolean on, int key, boolean serviceOn) {
        if (key == KeyEvent.KEYCODE_UNKNOWN) return on ? State.NO_BUTTON : State.OFF;
        if (!on) return State.OFF;
        return serviceOn ? State.ON : State.NEEDS_ANDROID;
    }

    /** The PC alternative: adds this service to Android's list (never replaces it, so the Kids guard stays on). */
    static String adbLine(String component) { return KidsGuard.adbLine(component); }

    // --- choosing by pressing: the page in front tells the service it is listening
    interface Chooser { void chose(int keyCode); }
    private static volatile Chooser chooser;
    private static volatile long chooseUntil;
    static void listen(Chooser c, long untilUptime) { chooser = c; chooseUntil = c == null ? 0 : untilUptime; }
    /** The page listening now, or null (none, or its time is up). */
    static Chooser chooser(long nowUptime) { Chooser c = chooser; return c != null && nowUptime < chooseUntil ? c : null; }
    /** When the choice under way stops by itself (uptime), or 0 when none is. */
    static long chooseUntil() { return chooser == null ? 0 : chooseUntil; }

    // --- this TV
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(HouseholdSettings.PREFS, 0); }
    /** Only the launcher app has it: the preview never declares the service and never shows the rows. */
    static boolean offered(Context c) { return DefaultHome.available(c); }
    static boolean on(Context c) { try { return prefs(c).getBoolean(ON, false); } catch (RuntimeException e) { return false; } }
    static void on(Context c, boolean on) { prefs(c).edit().putBoolean(ON, on).apply(); ButtonService.refilter(); }
    static int key(Context c) { try { return prefs(c).getInt(KEY, KeyEvent.KEYCODE_UNKNOWN); } catch (RuntimeException e) { return KeyEvent.KEYCODE_UNKNOWN; } }
    static void key(Context c, int code) { if (allowed(code)) prefs(c).edit().putInt(KEY, code).apply(); }
    static String component(Context c) { return c.getPackageName() + "/" + ButtonService.class.getName(); }
    /** Android's list of switched-on accessibility services includes this one. */
    static boolean serviceOn(Context c) {
        try { return KidsGuard.enabledIn(Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), c.getPackageName(), ButtonService.class.getName()); }
        catch (RuntimeException e) { return false; }
    }
    static State state(Context c) { return state(on(c), key(c), serviceOn(c)); }
    /** The service asks Android for keys only while they could matter: the option on with a button, or a choice under way. */
    static boolean wantsKeys(Context c, long nowUptime) {
        if (!offered(c)) return false;
        return chooser(nowUptime) != null || (on(c) && key(c) != KeyEvent.KEYCODE_UNKNOWN);
    }
    /** The service's own wake watch: the option on and a button chosen, while OD-23's watcher isn't already doing it. */
    static boolean watchesWake(Context c) { return offered(c) && on(c) && key(c) != KeyEvent.KEYCODE_UNKNOWN && !TurnOn.active(c); }
    static String adb(Context c) { return adbLine(component(c)); }
    /** Android's accessibility settings, or null when this TV has none Unified TV can open. */
    static Intent accessibilityScreen(Context c) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        try { return i.resolveActivity(c.getPackageManager()) != null ? i : null; } catch (RuntimeException e) { return null; }
    }
    /** Decide at a wake and, if nothing is in the way, bring Home forward once. */
    static TurnOn.Skip wake(Context c, boolean keyJustSeen) {
        TurnOn.Skip why = wake(TurnOn.read(c), keyJustSeen);
        if (why == TurnOn.Skip.NONE) try { c.startActivity(TurnOn.home(c)); } catch (RuntimeException ignored) { /* stays where it is */ }
        return why;
    }

    /**
     * The rows on When the TV turns on (launcher app): the button and how to choose it, the switch, Android's
     * accessibility switch with the way to it, and the plain lines. Every change asks the adult PIN. The page passes its
     * keys to {@link #onKey} and calls {@link #paused} and {@link #resumed}.
     */
    static final class Page {
        private final GlassScreen a;
        private final Runnable changed;
        private boolean listening, androidAsked;
        /** Ends a choice nobody finished, after {@link #CHOOSE_MS}: the service then stops taking keys for it. */
        private final android.os.Handler later = new android.os.Handler(android.os.Looper.getMainLooper());
        private final Runnable expire = this::expired;
        /** The last line about a press (refused, or chosen), shown under the rows until the next change. */
        private String said;

        Page(GlassScreen a, Runnable changed) { this.a = a; this.changed = changed; }
        boolean listening() { return listening; }

        /** Draws the section; returns the row to focus when the page was opened for it. */
        Button rows() {
            a.group("Remote button");
            a.card();
            a.text(TITLE, 18);
            a.text(INTRO, 15);
            int key = RemoteButton.key(a);
            boolean svc = serviceOn(a);
            if (listening) {
                a.text(PRESS, 18);
                a.text(PRESS_HINT, 15);
                if (said != null) a.text(said, 15);
                return null;
            }
            Button first = a.row(CHOOSE, key == KeyEvent.KEYCODE_UNKNOWN ? NOT_CHOSEN : name(key), false, () -> EditLock.ask(a, this::listen));
            if (key != KeyEvent.KEYCODE_TV) a.row(SUGGEST, null, false, () -> EditLock.ask(a, () -> chosen(KeyEvent.KEYCODE_TV)));
            State s = state(a);
            Button sw = a.row(SWITCH, on(a) ? "On" : "Off", false, () -> EditLock.ask(a, this::toggle));
            sw.setContentDescription(SWITCH + ", " + s.words);
            a.row(ANDROID_ROW, svc ? SWITCHED_ON : NOT_ON, false, () -> EditLock.ask(a, this::askAndroid));
            if (s == State.NEEDS_ANDROID) a.text("Not working yet: " + SERVICE_LABEL + " isn't switched on in the TV's accessibility settings.", 18);
            if (said != null) a.text(said, 15);
            a.text(WAKE_WORDS, 15);
            if (on(a)) a.text(ANY_WAKE, 15);
            a.text(WARNING_MEANS, 15);
            a.text(KIDS, 15);
            return first;
        }

        void listen() {
            listening = true; said = null;
            RemoteButton.listen(code -> a.runOnUiThread(() -> { if (listening) chosen(code); }), android.os.SystemClock.uptimeMillis() + CHOOSE_MS);
            later.removeCallbacks(expire); later.postDelayed(expire, CHOOSE_MS);
            ButtonService.refilter();
            changed.run();
        }
        private void expired() { if (listening) { stop(); said = TIMED_OUT; changed.run(); } }
        private void stop() { listening = false; later.removeCallbacks(expire); RemoteButton.listen(null, 0); ButtonService.refilter(); }

        void chosen(int code) {
            if (listening) stop();
            if (!allowed(code)) { said = refused(code); changed.run(); return; }
            RemoteButton.key(a, code); said = "Chosen: " + name(code) + ".";
            ButtonService.refilter();
            changed.run();
        }

        /**
         * A key arriving at the page itself while it listens (buttons that reach apps; the service reports the others).
         * True when used. Back stops; a refused button says why and keeps listening.
         */
        boolean onKey(KeyEvent e) {
            if (!listening) return false;
            if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() > 0) return true;
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) { stop(); said = null; changed.run(); return true; }
            if (!allowed(code)) { said = refused(code); changed.run(); return true; }
            chosen(code);
            return true;
        }

        void toggle() {
            if (RemoteButton.key(a) == KeyEvent.KEYCODE_UNKNOWN) { said = CHOOSE_FIRST; changed.run(); return; }
            boolean now = !on(a);
            on(a, now); said = null;
            changed.run();
            if (now && !serviceOn(a)) askAndroid();
        }

        /** The guided step: plain instructions and what Android's warning means, then the TV's own screen (or the PC line). */
        void askAndroid() {
            Intent screen = accessibilityScreen(a);
            AlertDialog.Builder ask = new AlertDialog.Builder(a).setTitle(SERVICE_LABEL);
            if (screen != null) ask.setMessage(STEPS + "\n\n" + WARNING_MEANS).setNegativeButton("Cancel", null)
                .setPositiveButton(OPEN_SETTINGS, (d, w) -> {
                    KidsGuard.grantPass(a); androidAsked = true;
                    try { a.startActivity(screen); } catch (RuntimeException e) { androidAsked = false; said = NO_SCREEN + " " + adb(a); changed.run(); }
                });
            else ask.setMessage(NO_SCREEN + "\n" + adb(a) + "\n\n" + WARNING_MEANS).setPositiveButton("OK", null);
            ask.show();
        }

        /** Leaving the page always ends a choice, so the service never goes on taking keys for it. */
        void paused() { if (listening) { stop(); said = null; } }
        /** Back from the TV's accessibility settings: say what is true now. */
        void resumed() {
            if (!androidAsked) return;
            androidAsked = false;
            said = serviceOn(a) ? SWITCHED_ON + " " + SERVICE_LABEL + " is on." : NOT_ON + ": " + SERVICE_LABEL + " is still off in the TV's accessibility settings.";
            ButtonService.refilter();
        }
    }
}
