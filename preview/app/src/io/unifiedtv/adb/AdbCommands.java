package io.unifiedtv.adb;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The only shell commands the Optional clean-up can send (AGENTS.md rule 9, SPEC "No blanket cleanup"). Everything else
 * is refused before it reaches the TV, by {@link AdbLink#shell}, whoever asks.
 *
 * <p>Allowed: reading the firmware and the TV's ids, the package lists, one package's version and requested permissions,
 * which apps answer Home, Live TV channels and Settings, the animation scales (read only, for the recovery record), and
 * the only two changes: {@code pm disable-user --user 0 <package>} and {@code pm enable <package>}.
 *
 * <p>Taking over the Home button (OD-28, launcher app only, HomeTakeover) adds one more change,
 * {@code cmd package set-home-activity --user 0 <component>}, and only for two kinds of component: Unified TV's own Home
 * entry ({@link #HOME_ENTRY} in the launcher app or its review build), and a stock Home app's own component when Home is
 * handed back ({@link #STOCK_HOMES}). Any other component, or a Home entry in any other package, is refused unsent.
 *
 * <p>Never: {@code pm uninstall}, {@code pm hide}, {@code pm clear}, {@code pm grant}, {@code settings put}, a reboot, root
 * ({@code su}), {@code am}, {@code input}, a second command after a {@code ;}, {@code &&} or {@code |}, a redirect, or a
 * package name with anything but letters, digits, '_' and dots. The tested lists do not ask for {@code pm hide} anywhere,
 * so it is not here at all.
 */
public final class AdbCommands {
    private AdbCommands() {}

    /** The same shape tools/recovery.py accepts: nothing that could carry shell syntax. */
    public static final Pattern PACKAGE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    static final int PACKAGE_MAX = 200;

    public static final String FINGERPRINT = "getprop ro.build.fingerprint", SERIAL = "getprop ro.serialno",
        ADB_SECURE = "getprop ro.adb.secure", ANDROID_ID = "settings get secure android_id",
        PACKAGES = "pm list packages", DISABLED = "pm list packages -d", ENABLED = "pm list packages -e", SYSTEM = "pm list packages -s",
        HOME = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        HOME_CLAIMS = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        CHANNEL_APPS = "cmd package query-activities --brief -a android.intent.action.VIEW -t vnd.android.cursor.item/channel",
        SETTINGS_APPS = "cmd package query-activities --brief -a android.settings.SETTINGS",
        /** The active screensaver (read only), so the clean-up never turns it off. */
        SCREENSAVER = "settings get secure screensaver_components";
    /** Unified TV's own Home entry: the activity-alias launcher/AndroidManifest.xml declares, switched off until chosen. */
    public static final String HOME_ENTRY = "io.unifiedtv.preview.HomeEntry";
    /** The only apps whose Home entry set-home-activity may name: the launcher app and its review build. Never the preview. */
    public static final Set<String> UNIFIED_HOMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("io.unifiedtv.launcher", "io.unifiedtv.launcher.devicecheck")));
    /** Stock Home apps whose own component set-home-activity may name when Home is handed back (the TV's previous Home). */
    public static final Set<String> STOCK_HOMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.tcl.tvlauncher")));
    static final String SET_HOME = "cmd package set-home-activity --user 0 ";
    /** package/class, the class either ".Relative" or fully named: nothing that could carry shell syntax. */
    static final Pattern COMPONENT = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/(\\.?[A-Za-z_][A-Za-z0-9_$]*(?:\\.[A-Za-z_][A-Za-z0-9_$]*)*)");
    /** The animation scales tools/recovery.py records; read only. */
    public static final String[] SCALES = {"window_animation_scale", "transition_animation_scale", "animator_duration_scale"};

    private static final Set<String> FIXED;
    static {
        Set<String> s = new HashSet<>(Arrays.asList(FINGERPRINT, SERIAL, ADB_SECURE, ANDROID_ID, PACKAGES, DISABLED, ENABLED, SYSTEM,
            HOME, HOME_CLAIMS, CHANNEL_APPS, SETTINGS_APPS, SCREENSAVER));
        for (String key : SCALES) s.add(scale(key));
        FIXED = Collections.unmodifiableSet(s);
    }

    public static String scale(String key) { return "settings get global " + key; }
    public static String dumpsys(String pkg) { return "dumpsys package " + pkg(pkg); }
    public static String disable(String pkg) { return "pm disable-user --user 0 " + pkg(pkg); }
    public static String enable(String pkg) { return "pm enable " + pkg(pkg); }

    /** Makes {@code component} the TV's Home: only Unified TV's own Home entry, or a stock Home's own component (see above). */
    public static String setHome(String component) { if (!homeTarget(component)) throw new IllegalArgumentException("not a Home this can set"); return SET_HOME + component; }

    /** True for Unified TV's own Home entry in the launcher app (or its review build), or a stock Home app's component. */
    public static boolean homeTarget(String component) {
        if (component == null || component.length() > PACKAGE_MAX * 2) return false;
        java.util.regex.Matcher m = COMPONENT.matcher(component);
        if (!m.matches() || !isPackage(m.group(1))) return false;
        if (UNIFIED_HOMES.contains(m.group(1))) return m.group(2).equals(HOME_ENTRY);
        return STOCK_HOMES.contains(m.group(1));
    }

    public static boolean isPackage(String pkg) { return pkg != null && pkg.length() <= PACKAGE_MAX && PACKAGE.matcher(pkg).matches(); }
    private static String pkg(String pkg) { if (!isPackage(pkg)) throw new IllegalArgumentException("not a package name"); return pkg; }

    /** True only for a command on the list above, exactly as written there. */
    public static boolean allowed(String command) {
        if (command == null) return false;
        if (FIXED.contains(command)) return true;
        for (String prefix : new String[]{"dumpsys package ", "pm disable-user --user 0 ", "pm enable "})
            if (command.startsWith(prefix) && isPackage(command.substring(prefix.length()))) return true;
        return command.startsWith(SET_HOME) && homeTarget(command.substring(SET_HOME.length()));
    }

    /** True for the commands that change the TV (disable, enable, set Home); everything else on the list only reads. */
    public static boolean changes(String command) {
        return allowed(command) && (command.startsWith("pm disable-user ") || command.startsWith("pm enable ") || command.startsWith(SET_HOME));
    }
}
