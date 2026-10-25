package io.unifiedtv.preview;

import android.view.KeyEvent;
import java.util.*;

/**
 * Spec 6.4, RC-05, HRC-09, OD-06: the pure record behind "Check your remote's buttons". It is told about each key
 * event (Android classes stay in RemoteCheckActivity) and works out presses, long presses, the finish gesture and
 * the summary the owner copies into the report. It never invents a key mapping: it reports only what arrived, and
 * says plainly which buttons Android keeps for itself so their absence is not mistaken for a fault.
 */
final class RemoteLog {
    /** Held at least this long, or auto-repeated by Android, counts as a long press. */
    static final long LONG_PRESS_MS = 500;
    /** Holding OK this long finishes the check. */
    static final long FINISH_HOLD_MS = 2000;
    /** A second Back press within this time finishes the check. */
    static final long BACK_TWICE_MS = 2000;

    enum Outcome { NONE, BACK_ONCE, FINISH }

    /** Buttons that spec 6.4 leaves with Android: Unified TV records them but always passes them on. */
    private static final Set<Integer> OS_KEYS = new HashSet<>(Arrays.asList(
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_TV_INPUT_HDMI_1, KeyEvent.KEYCODE_TV_INPUT_HDMI_2,
            KeyEvent.KEYCODE_TV_INPUT_HDMI_3, KeyEvent.KEYCODE_TV_INPUT_HDMI_4, KeyEvent.KEYCODE_TV_ANTENNA_CABLE,
            KeyEvent.KEYCODE_TV_INPUT_COMPOSITE_1, KeyEvent.KEYCODE_TV_INPUT_COMPONENT_1, KeyEvent.KEYCODE_TV_INPUT_VGA_1,
            KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_TV_AUDIO_DESCRIPTION,
            KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_ALL_APPS, KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_NOTIFICATION, KeyEvent.KEYCODE_BRIGHTNESS_UP, KeyEvent.KEYCODE_BRIGHTNESS_DOWN));

    /** Buttons Android normally handles before any app sees them. Their absence is expected, not a fault. */
    static final int[] USUALLY_RESERVED = {KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_ALL_APPS};

    /** Buttons most TV remotes have. Ones not seen are listed so the owner can mark each not-present or unsupported. */
    static final int[] COMMON = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_PROG_RED};

    private static final Map<Integer, String> LABELS = new HashMap<>();
    static {
        Object[] pairs = {KeyEvent.KEYCODE_DPAD_UP, "Up", KeyEvent.KEYCODE_DPAD_DOWN, "Down", KeyEvent.KEYCODE_DPAD_LEFT, "Left",
                KeyEvent.KEYCODE_DPAD_RIGHT, "Right", KeyEvent.KEYCODE_DPAD_CENTER, "OK", KeyEvent.KEYCODE_ENTER, "OK (Enter)",
                KeyEvent.KEYCODE_NUMPAD_ENTER, "OK (Enter)", KeyEvent.KEYCODE_BACK, "Back", KeyEvent.KEYCODE_MENU, "Menu",
                KeyEvent.KEYCODE_HOME, "Home", KeyEvent.KEYCODE_POWER, "Power", KeyEvent.KEYCODE_TV_POWER, "TV power",
                KeyEvent.KEYCODE_VOLUME_UP, "Volume up", KeyEvent.KEYCODE_VOLUME_DOWN, "Volume down",
                KeyEvent.KEYCODE_VOLUME_MUTE, "Mute", KeyEvent.KEYCODE_MUTE, "Microphone mute",
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play/pause", KeyEvent.KEYCODE_MEDIA_PLAY, "Play", KeyEvent.KEYCODE_MEDIA_PAUSE, "Pause",
                KeyEvent.KEYCODE_MEDIA_STOP, "Stop", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "Fast forward",
                KeyEvent.KEYCODE_MEDIA_REWIND, "Rewind", KeyEvent.KEYCODE_MEDIA_NEXT, "Next", KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous",
                KeyEvent.KEYCODE_CHANNEL_UP, "Channel up", KeyEvent.KEYCODE_CHANNEL_DOWN, "Channel down",
                KeyEvent.KEYCODE_GUIDE, "Guide", KeyEvent.KEYCODE_INFO, "Info", KeyEvent.KEYCODE_TV_INPUT, "Input/Source",
                KeyEvent.KEYCODE_ASSIST, "Assistant", KeyEvent.KEYCODE_VOICE_ASSIST, "Voice", KeyEvent.KEYCODE_SEARCH, "Search",
                KeyEvent.KEYCODE_SETTINGS, "Settings", KeyEvent.KEYCODE_APP_SWITCH, "Recent apps", KeyEvent.KEYCODE_ALL_APPS, "All apps",
                KeyEvent.KEYCODE_CAPTIONS, "Subtitles", KeyEvent.KEYCODE_PROG_RED, "Red", KeyEvent.KEYCODE_PROG_GREEN, "Green",
                KeyEvent.KEYCODE_PROG_YELLOW, "Yellow", KeyEvent.KEYCODE_PROG_BLUE, "Blue", KeyEvent.KEYCODE_0, "Digits (0)",
                KeyEvent.KEYCODE_UNKNOWN, "Unknown to Android"};
        for (int i = 0; i < pairs.length; i += 2) LABELS.put((Integer) pairs[i], (String) pairs[i + 1]);
    }

    /** One physical button as it arrived: a keycode, or for KEYCODE_UNKNOWN its scancode. */
    static final class Entry {
        final int keyCode; final String name;
        final Set<Integer> scanCodes = new TreeSet<>();
        final Set<String> sources = new TreeSet<>();
        int presses, longPresses, maxRepeat, pressRepeat; long maxHeldMs;
        Entry(int keyCode, String name) { this.keyCode = keyCode; this.name = name; }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<String> interruptions = new ArrayList<>();
    private Entry last; private String lastLine = "";
    private long lastBackDown = Long.MIN_VALUE / 2;
    private boolean finishedByHold;

    static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /** Spec 6.4: volume, mute, power, input, accessibility and the voice key stay with Android. */
    static boolean passToAndroid(int code) { return OS_KEYS.contains(code); }

    static String label(int code, String name) {
        String friendly = LABELS.get(code);
        if (friendly != null) return friendly;
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return "Digit " + (code - KeyEvent.KEYCODE_0);
        return name == null || name.isEmpty() ? "Key " + code : name.replaceFirst("^KEYCODE_", "");
    }

    /** The input device's name helps identify the remote (OD-06); anything shaped like a hardware address is removed. */
    static String cleanSource(String source) {
        if (source == null) return "";
        String s = source.replaceAll("(?i)\\b([0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b", "").replaceAll("[^\\p{L}\\p{N} ._()/-]", "").trim();
        return s.length() > 48 ? s.substring(0, 48) : s;
    }

    /**
     * One key event. down: ACTION_DOWN (otherwise ACTION_UP). Times are Android's uptime milliseconds from the event.
     * Returns FINISH when OK has been held for FINISH_HOLD_MS or Back pressed twice, BACK_ONCE after a first Back.
     */
    Outcome record(int keyCode, String name, int scanCode, boolean down, int repeatCount, long eventTime, long downTime, String source) {
        String id = keyCode == KeyEvent.KEYCODE_UNKNOWN ? "scan:" + scanCode : "key:" + keyCode;
        Entry e = entries.get(id);
        if (e == null) { e = new Entry(keyCode, name == null || name.isEmpty() ? "KEYCODE_" + keyCode : name); entries.put(id, e); }
        e.scanCodes.add(scanCode);
        String clean = cleanSource(source);
        if (!clean.isEmpty()) e.sources.add(clean);
        last = e;
        long held = Math.max(0, eventTime - downTime);
        Outcome outcome = Outcome.NONE;
        if (down) {
            if (repeatCount == 0) { e.presses++; e.pressRepeat = 0; }
            e.pressRepeat = Math.max(e.pressRepeat, repeatCount);
            e.maxRepeat = Math.max(e.maxRepeat, repeatCount);
            e.maxHeldMs = Math.max(e.maxHeldMs, held);
            if (isOk(keyCode) && repeatCount > 0 && held >= FINISH_HOLD_MS) { finishedByHold = true; outcome = Outcome.FINISH; }
            if (keyCode == KeyEvent.KEYCODE_BACK && repeatCount == 0) {
                outcome = eventTime - lastBackDown <= BACK_TWICE_MS ? Outcome.FINISH : Outcome.BACK_ONCE;
                lastBackDown = outcome == Outcome.FINISH ? Long.MIN_VALUE / 2 : eventTime;
            }
            lastLine = label(keyCode, e.name) + " · " + e.name + " (" + keyCode + ") · scancode " + scanCode
                    + (repeatCount > 0 ? " · held " + held + " ms, repeat " + repeatCount : " · pressed");
        } else {
            e.maxHeldMs = Math.max(e.maxHeldMs, held);
            boolean isLong = held >= LONG_PRESS_MS || e.pressRepeat > 0;
            if (isLong) e.longPresses++;
            e.pressRepeat = 0;
            lastLine = label(keyCode, e.name) + " · " + e.name + " (" + keyCode + ") · scancode " + scanCode + " · released after " + held + " ms"
                    + (isLong ? " (long press)" : "");
        }
        return outcome;
    }

    /** The screen lost focus, usually because the TV itself handled the last button (Home, a shortcut or an overlay). */
    void interrupted() {
        interruptions.add(last == null ? "before any button arrived" : "after " + label(last.keyCode, last.name) + " (" + last.name + ")");
    }

    List<Entry> entries() { return new ArrayList<>(entries.values()); }
    List<String> interruptions() { return new ArrayList<>(interruptions); }
    boolean received(int keyCode) {
        if (isOk(keyCode)) { for (Entry e : entries.values()) if (isOk(e.keyCode)) return true; return false; }
        return entries.containsKey("key:" + keyCode);
    }

    /** The last event, for the large line at the top of the screen. */
    String lastLine() { return lastLine.isEmpty() ? "Waiting for the first button…" : lastLine; }

    /** The live list. Buttons stay in the order they first arrived, so lines don't jump about. */
    String liveText() {
        if (entries.isEmpty()) return "No buttons yet.";
        StringBuilder out = new StringBuilder();
        for (Entry e : entries.values()) out.append(line(e)).append('\n');
        return out.toString().trim();
    }

    private String line(Entry e) {
        StringBuilder s = new StringBuilder(label(e.keyCode, e.name)).append(": ").append(e.name).append(" (").append(e.keyCode).append("), scancode ");
        s.append(join(e.scanCodes)).append("; ").append(e.presses).append(e.presses == 1 ? " press" : " presses");
        if (e.longPresses > 0) s.append(", ").append(e.longPresses).append(" long");
        if (e.maxHeldMs > 0) s.append(", held up to ").append(e.maxHeldMs).append(" ms");
        if (e.maxRepeat > 0) s.append(", repeats up to ").append(e.maxRepeat);
        if (passToAndroid(e.keyCode)) s.append("; passed on to Android");
        if (isOk(e.keyCode) && finishedByHold) s.append("; includes the hold that finished the check");
        if (!e.sources.isEmpty()) s.append("; from ").append(String.join(", ", e.sources));
        return s.toString();
    }

    private static String join(Set<Integer> values) {
        StringBuilder s = new StringBuilder();
        for (Integer v : values) { if (s.length() > 0) s.append('/'); s.append(v); }
        return s.toString();
    }

    /**
     * The block the owner copies into the report template. header carries the app and TV lines (model, Android
     * version, build); this class never adds addresses, serial numbers or account details.
     */
    String summary(String header) {
        StringBuilder s = new StringBuilder();
        s.append("Remote check (RC-05, HRC-09)\n");
        if (header != null && !header.isEmpty()) s.append(header.trim()).append('\n');
        s.append("Remote: <model from the label inside the battery cover, or a photo>\n\n");
        s.append("Received by Unified TV (").append(entries.size()).append(entries.size() == 1 ? " button):\n" : " buttons):\n");
        if (entries.isEmpty()) s.append("  none\n");
        for (Entry e : entries.values()) s.append("  ").append(line(e)).append('\n');
        s.append('\n');
        List<String> reservedSeen = new ArrayList<>(), reservedMissing = new ArrayList<>();
        for (int code : USUALLY_RESERVED) (received(code) ? reservedSeen : reservedMissing).add(label(code, null));
        s.append("Kept by Android (OS-reserved): Home never reaches apps, because Android itself takes it. That is expected, not a fault. ");
        s.append("Not received here: ").append(reservedMissing.isEmpty() ? "none" : String.join(", ", reservedMissing)).append(".\n");
        if (!reservedSeen.isEmpty()) s.append("Unusually, these did reach Unified TV: ").append(String.join(", ", reservedSeen)).append(".\n");
        s.append("App buttons (for example Netflix or YouTube) are normally handled by the TV and open that app: record them as third-party-owned.\n");
        List<String> missing = new ArrayList<>();
        for (int code : COMMON) if (!received(code)) missing.add(label(code, null));
        s.append("Common buttons not seen: ").append(missing.isEmpty() ? "none" : String.join(", ", missing))
                .append(missing.isEmpty() ? ".\n" : ". Mark each as not-present (no such button) or unsupported (pressed, nothing arrived).\n");
        if (interruptions.isEmpty()) s.append("Unified TV kept the screen throughout.\n");
        else s.append("Unified TV lost the screen ").append(interruptions.size()).append(interruptions.size() == 1 ? " time: " : " times: ")
                .append(String.join("; ", interruptions)).append(". The button pressed just then was handled by the TV, not by Unified TV.\n");
        s.append("Escape button (HRC-01): <which button once opened the TCL interface, and what it did today>\n");
        return s.toString();
    }
}
