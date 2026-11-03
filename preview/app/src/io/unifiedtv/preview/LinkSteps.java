package io.unifiedtv.preview;

import android.content.Context;
import android.content.SharedPreferences;
import io.unifiedtv.adb.AdbException;
import io.unifiedtv.adb.AdbLink;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The session link to this TV's own debugging (OD-25, OD-28, notification access): what each step of the last attempt
 * was, the plain words for each failure, and the limit on a whole attempt. Owner's Sony BRAVIA 4K UR2 (Android TV 10,
 * 3 November 2026): after the TV's "Allow USB debugging?" the page waited with no end and no word of where it was.
 *
 * <p>Kept on this TV only, for Owner diagnostics ("Last debugging-link step: …" and the last attempt's steps): plain
 * words and times. Never a token, key, signature, command output, serial number or address; a command is recorded as
 * the allowlisted command itself (at most a package name). A failure also goes in the event log, as its step only.
 */
final class LinkSteps {
    private LinkSteps() {}

    static final String PREFS = "unified-tv-link-steps", KEY = "steps";
    static final int MAX = 40, WORDS_MAX = 120;
    /** A whole attempt (the TV's Allow, then every command) stops after this long, whatever is still waiting. */
    static final long FLOW_MS = 120_000;

    // --- words (tests compare them)
    static final String WAITING_ALLOW = "Waiting for you to choose Allow on the TV…";
    static final String BACK_CANCELS = "Press Back to cancel.";
    static final String CONNECT_REFUSED = "The TV didn't let Unified TV connect to its own debugging service. Network debugging is off on this TV: turn on USB debugging in Settings > System > Developer options, then choose Try again.";
    static final String CLOSED_EARLY = "The TV didn't let Unified TV connect to its own debugging service: it closed the link before Unified TV could sign in. Turn USB debugging off and on again in Developer options, then choose Try again.";
    static final String NO_ANSWER = "The TV didn't answer the debugging link. Turn USB debugging off and on again in Developer options, then choose Try again.";
    static final String ALLOW_TIMEOUT = "The TV's \"Allow USB debugging?\" wasn't answered with Allow within a minute. Choose Try again; when the TV asks, tick \"Always allow from this computer\" and choose OK.";
    static final String NOT_ALLOWED = "The TV didn't allow Unified TV. Try again, and when the TV asks \"Allow USB debugging?\", tick \"Always allow from this computer\" and choose OK.";
    static final String COMMAND_TIMEOUT = "The TV took too long to answer a command, so Unified TV closed its link.";
    static final String COMMAND_LOST = "The TV stopped answering a command, so Unified TV closed its link.";
    static final String LOST = "The TV stopped answering, so Unified TV closed its link.";
    static final String REFUSED_COMMAND = "The TV refused to run a command.";
    static final String NOT_UNDERSTOOD = "This TV's debugging answered in a way Unified TV doesn't understand (for example, wireless-debugging pairing only).";
    static final String NOT_THIS_TV = "Something other than this TV answered, so nothing was read or changed.";
    static final String FLOW_TIMEOUT = "This took longer than two minutes, so Unified TV stopped and closed its link.";
    static final String LAST = "Last debugging-link step: ";

    /** The plain words for a failed link, by what failed and at which step. */
    static String words(AdbException e) {
        AdbLink.Step s = e.step;
        switch (e.kind) {
            case CLOSED: return CONNECT_REFUSED;
            case TIMED_OUT: return s == AdbLink.Step.ALLOW ? ALLOW_TIMEOUT : s == AdbLink.Step.COMMAND ? COMMAND_TIMEOUT : NO_ANSWER;
            case NOT_ALLOWED: return NOT_ALLOWED;
            case NOT_THIS_TV: return NOT_THIS_TV;
            case PROTOCOL: return s == AdbLink.Step.COMMAND ? REFUSED_COMMAND : NOT_UNDERSTOOD;
            case REFUSED: return "That isn't a command Unified TV may send, so it wasn't sent.";
            default:
                if (s == AdbLink.Step.HELLO || s == AdbLink.Step.SIGN_IN) return CLOSED_EARLY;
                return s == AdbLink.Step.COMMAND ? COMMAND_LOST : LOST;
        }
    }

    /** One short line for the event log and the step history: where it stopped. */
    static String stopped(AdbException e) {
        String where = e.step != null ? e.step.words : "the link";
        switch (e.kind) {
            case TIMED_OUT: return "Stopped (no answer in time) at: " + where;
            case NOT_ALLOWED: return "Stopped (not allowed) at: " + where;
            case CLOSED: return "Stopped (debugging service closed) at: " + where;
            default: return "Stopped at: " + where;
        }
    }

    // --- the last attempt's steps
    static final class Entry { final long at; final String words; Entry(long at, String words) { this.at = at; this.words = words; } }

    private static SharedPreferences store(Context c) { return c.getSharedPreferences(PREFS, 0); }

    /** A new attempt: the history starts again. {@code flow} names the page ("Take over the Home button"). */
    static synchronized void begin(Context c, String flow) {
        List<Entry> fresh = new ArrayList<>();
        fresh.add(new Entry(System.currentTimeMillis(), clean("Started: " + flow)));
        save(c, fresh);
    }

    static synchronized void step(Context c, String words) {
        if (c == null || words == null || words.trim().isEmpty()) return;
        List<Entry> all = history(c);
        all.add(new Entry(System.currentTimeMillis(), clean(words)));
        while (all.size() > MAX) all.remove(0);
        save(c, all);
    }

    /** The attempt stopped: the step goes in the history and, as its step only, in the event log. */
    static void failed(Context c, String words) {
        step(c, words);
        EventLog.record(c, EventLog.CONNECTION, "Debugging link: " + words);
    }
    static void failed(Context c, AdbException e) { failed(c, stopped(e)); }

    static synchronized List<Entry> history(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(store(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length() && i < MAX * 2; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !o.optString("w").isEmpty()) out.add(new Entry(o.optLong("t"), clean(o.optString("w"))));
            }
        } catch (Exception ignored) { /* damaged: none */ }
        return out;
    }

    private static void save(Context c, List<Entry> all) {
        JSONArray a = new JSONArray();
        for (Entry e : all) try { a.put(new JSONObject().put("t", e.at).put("w", e.words)); } catch (Exception ignored) { /* skipped */ }
        try { store(c).edit().putString(KEY, a.toString()).apply(); } catch (RuntimeException ignored) { /* best effort */ }
    }

    private static String clean(String words) {
        String w = words.replaceAll("\\s+", " ").trim();
        return w.length() > WORDS_MAX ? w.substring(0, WORDS_MAX) : w;
    }

    /** "Last debugging-link step: …" */
    static String last(Context c) {
        List<Entry> all = history(c);
        return LAST + (all.isEmpty() ? "none yet." : all.get(all.size() - 1).words);
    }

    /** Owner diagnostics: the last step, then the last attempt's steps with their times. */
    static String diagnosis(Context c) {
        List<Entry> all = history(c);
        StringBuilder b = new StringBuilder(last(c));
        if (!all.isEmpty()) b.append("\nThe last attempt's steps:");
        for (Entry e : all) b.append("\n").append(OwnerChecks.when(c, e.at)).append("  ").append(e.words);
        return b.toString();
    }

    // --- hooks for the link and the pages
    /** The link's own steps, into the history. */
    static AdbLink.Trace trace(Context c) {
        Context app = c.getApplicationContext();
        return (step, detail) -> step(app, step.words + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")"));
    }

    /** Each command into the history before it is sent (the command itself: allowlisted, at most a package name). */
    static Cleanup.Shell traced(Context c, Cleanup.Shell sh) {
        Context app = c.getApplicationContext();
        return command -> { step(app, "Command: " + command); return sh.run(command); };
    }
}
