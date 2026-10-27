package io.unifiedtv.preview;

import java.util.*;
import java.util.regex.Pattern;

/**
 * What the Optional clean-up may never turn off, whatever a list says, and how risky each listed app is (SPEC §13 and
 * "No blanket cleanup", AGENTS.md rule 9). Pure: the facts come from the TV (Cleanup.check), the words go on the page.
 *
 * <p>Never offered: Android's core packages (the same patterns as tools/recovery.py and disable.sh, #884); the stock
 * launcher, every app that answers Home and Unified TV itself (SPEC §6.1: the factory Home is never disabled); Live TV,
 * the tuner and HDMI inputs (com.tcl.tv, com.tcl.tvinput, any app asking for TV_INPUT_HARDWARE or opening channels,
 * #321); the firmware updater; the TV's settings and whatever opens them; the remote, Bluetooth and the microphone
 * plumbing; the network and Wi-Fi owners, including the setup wizard (setupwraith can own the saved Wi-Fi); keyboards;
 * accessibility (TalkBack and its speech engine); sound and HDMI-CEC; Google Play and Play services; the household's
 * VPN and its Live TV app; any WebView (any package with "webview" in its name: com.tcl.android.webview may be this
 * TV's active WebView provider); the active screensaver (read with {@code settings get secure screensaver_components});
 * Google's TV recommendations while a stock launcher is installed and on (its Home rows use them); and every package a
 * tested list's notes say to keep.
 *
 * <p>On the owner's exact firmware, packages that only the sibling lists (TCL 55C725, 65C725) name are still offered, as
 * "optional, your choice" with at least some risk, and never recommended (risk(), recommended()).
 */
final class CleanupPolicy {
    private CleanupPolicy() {}

    enum Risk { LOW, MEDIUM, HIGH;
        String words() { return this == LOW ? "Low risk" : this == MEDIUM ? "Some risk" : "Higher risk"; } }

    /** tools/recovery.py CORE_RE and CORE_OK, character for character (tests/test_cleanup_record.py compares them). */
    static final Pattern CORE = Pattern.compile("^(android|com\\.android\\.systemui|com\\.android\\.shell|com\\.android\\.phone|com\\.android\\.settings|com\\.android\\.tv\\.settings|com\\.(google\\.)?android\\.(permissioncontroller|packageinstaller|ext\\.services|ext\\.shared|modulemetadata)|com\\.google\\.android\\.overlay\\.modules.*|com\\.android\\.providers\\..*)$");
    static final Pattern CORE_OK = Pattern.compile("^com\\.android\\.providers\\.(calendar|contacts|userdictionary|blockednumber)$");
    /** Stock Home apps this project knows by name, whatever answers Home today. */
    static final Set<String> LAUNCHERS = new HashSet<>(Arrays.asList("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher", "com.tcl.tvlauncher", "com.gathin.tvhome"));
    /** Any WebView: com.tcl.android.webview may be this TV's active WebView provider, so none is ever offered. */
    static final Pattern WEBVIEW = Pattern.compile("(?i).*webview.*");
    static final String RECOMMENDATIONS = "com.google.android.tvrecommendations";

    /** Name rules, in the order they are checked, each with the reason the page gives. */
    private static final Object[][] RULES = {
        {Pattern.compile("(?i)^io\\.unifiedtv\\..*"), "This is Unified TV."},
        {Pattern.compile("(?i).*launcher.*"), "It's a Home screen. The TV's own Home is never turned off."},
        {Pattern.compile("(?i)^com\\.tcl\\.(tv|tvinput)$|.*(tvinput|tv\\.input|livetv|tuner|hdmi|passthrough|\\.dtv|\\.atv|tvprovider).*"), "Live TV and the HDMI inputs need it."},
        {Pattern.compile("(?i).*\\bcec\\b.*|.*(audio|sound|dolby|dts|volume).*"), "Sound, volume or HDMI control needs it."},
        {Pattern.compile("(?i).*(versionupdate|systemupdate|\\.ota\\b|\\bota\\.|ota$|updater|update|fota|ttvs).*"), "It installs the TV's updates."},
        {Pattern.compile("(?i).*settings.*"), "The TV's settings need it."},
        {Pattern.compile("(?i).*(bluetooth|bt_rcu|rcu|autopair|remote|\\.system\\.server|keycustom|suspension|micmanager|\\.tv\\.assistant$).*"), "Your remote, its buttons or Bluetooth need it."},
        {Pattern.compile("(?i).*(wifi|network|connectivity|ethernet|providers\\.config|initsetup|factory|setupwraith|provision).*"), "The TV's network connection or first-time setup needs it. On some TCL sets turning it off drops Wi-Fi."},
        {Pattern.compile("(?i).*(inputmethod|keyboard|latinime|\\.ime$|\\.ime\\.).*"), "It's the on-screen keyboard."},
        {Pattern.compile("(?i).*(accessibility|talkback|marvin|\\.tts$|\\.tts\\.|texttospeech).*"), "Accessibility needs it (screen reader or speech). Accessibility is never turned off here."},
        {Pattern.compile("(?i).*(security|keychain|certinstaller|permission|\\.gms\\b|\\.gsf\\b|vending).*"), "Google Play, security or permissions need it."},
        {Pattern.compile("(?i).*(vpn).*"), "Your VPN needs it."},
    };

    /** What the TV told the check, for the rules that depend on this TV rather than on a name. */
    static final class Facts {
        final Set<String> keep = new HashSet<>(), stockLaunchers = new HashSet<>(), homeClaims = new HashSet<>(), channelApps = new HashSet<>(),
            settingsApps = new HashSet<>(), inputDrivers = new HashSet<>(), screensavers = new HashSet<>();
        String homeOwner, self, liveTv;
        /** A stock launcher is installed and on: its recommendation rows are still in use. */
        boolean stockLauncherOn;
    }

    /** Why this package may never be turned off here, in plain words; null when it may be offered. */
    static String blocked(String pkg, Facts f) {
        if (!io.unifiedtv.adb.AdbCommands.isPackage(pkg)) return "Not a package name.";
        if (CORE.matcher(pkg).matches() && !CORE_OK.matcher(pkg).matches()) return "It's part of Android itself. Without it the TV might not start.";
        if (pkg.equals(f.self) || pkg.equals(f.liveTv)) return pkg.equals(f.self) ? "This is Unified TV." : "It's your Live TV app.";
        if (LAUNCHERS.contains(pkg) || f.stockLaunchers.contains(pkg) || pkg.equals(f.homeOwner) || f.homeClaims.contains(pkg))
            return "It can be the TV's Home screen. The TV's own Home is never turned off.";
        if (f.inputDrivers.contains(pkg) || f.channelApps.contains(pkg)) return "Live TV and the HDMI inputs need it (#321: turning it off can leave the inputs black).";
        if (f.settingsApps.contains(pkg)) return "The TV's Settings open with it.";
        if (WEBVIEW.matcher(pkg).matches()) return "Apps use it to show web pages. It may be this TV's WebView.";
        if (f.screensavers.contains(pkg)) return "It's the TV's screensaver now. Choose another screensaver in the TV's settings first.";
        if (RECOMMENDATIONS.equals(pkg) && f.stockLauncherOn) return "The TV's own Home screen is still on and uses it for its rows.";
        for (Object[] rule : RULES) if (((Pattern) rule[0]).matcher(pkg).matches()) return (String) rule[1];
        if (f.keep.contains(pkg)) return "The tested lists say to keep it on this family of TCL TVs.";
        return null;
    }

    // --- advice: risk, a warning in plain words, and whether to suggest it
    private static final Map<String, String> HIGH = new HashMap<>();
    private static final Map<String, String> MEDIUM = new HashMap<>();
    static {
        HIGH.put("com.google.android.katniss", "Voice search and the remote's microphone button stop working, and so does Ask your TV.");
        HIGH.put("com.tcl.assistant", "The remote's microphone button may stop working.");
        HIGH.put("com.tcl.dashboard", "The remote's settings button may stop opening TCL's quick panel. The TV's own Settings still open from Home.");
        HIGH.put("com.google.android.apps.tv.dreamx", "It's the screensaver (ambient mode). Choose another screensaver in the TV's settings first.");
        HIGH.put(RECOMMENDATIONS, "The TV's own Home loses its recommendation rows. Only offered while the TV's own Home is turned off.");
        MEDIUM.put("com.tcl.partnercustomizer", "TCL's partner settings. On some TVs the stock Home's Inputs button uses it.");
        MEDIUM.put("com.tcl.ui_mediaCenter", "Turn it back on to play films from a USB drive.");
        MEDIUM.put("android.autoinstalls.config.tcl.device", "Stops TCL installing its own apps silently. Apps you installed are not affected.");
        MEDIUM.put("android.autoinstalls.config.google.gtvpai", "Stops Google installing partner apps silently. Apps you installed are not affected.");
        MEDIUM.put("com.google.android.syncadapters.calendar", "Only matters if an app on the TV shows your Google calendar.");
    }
    /** Apps the household might simply use: never suggested, always "your choice". */
    private static final Pattern YOUR_CHOICE = Pattern.compile("(?i)^(com\\.netflix\\.|au\\.com\\.stan\\.|tv\\.wuaki\\.|com\\.google\\.android\\.(videos|play\\.games|katniss)|com\\.tcl\\.(browser|gallery|smartalexa|ui_mediacenter|assistant|ocean\\.)).*");

    static Risk risk(CleanupLists.Entry e) {
        Risk r = HIGH.containsKey(e.pkg) ? Risk.HIGH : MEDIUM.containsKey(e.pkg) ? Risk.MEDIUM : Risk.LOW;
        return !e.thisFirmware && r == Risk.LOW ? Risk.MEDIUM : r;   // not tested on this firmware: never "low"
    }
    static String warning(String pkg) { String w = HIGH.get(pkg); return w != null ? w : MEDIUM.get(pkg); }

    /** Suggested only for adverts, tracking and background leftovers tested on this exact firmware, at low risk. */
    static boolean recommended(CleanupLists.Entry e) {
        if (!e.thisFirmware || risk(e) != Risk.LOW || YOUR_CHOICE.matcher(e.pkg).matches()) return false;
        String g = e.group.toLowerCase(Locale.ROOT);
        return g.contains("ads") || g.contains("promo") || g.contains("telemetry") || g.contains("analytics") || g.contains("background");
    }

    /** The name a person reads: the list's own note, first letter capitalised and cut at its first ';' or '(', else the package. */
    static String title(CleanupLists.Entry e) {
        String n = e.note == null ? "" : e.note.trim();
        for (String cut : new String[]{";", " (", " — "}) { int at = n.indexOf(cut); if (at > 0) n = n.substring(0, at); }
        n = n.trim();
        if (n.isEmpty()) return e.pkg;
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
