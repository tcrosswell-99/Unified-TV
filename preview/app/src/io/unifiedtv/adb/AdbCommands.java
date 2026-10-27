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

    public static boolean isPackage(String pkg) { return pkg != null && pkg.length() <= PACKAGE_MAX && PACKAGE.matcher(pkg).matches(); }
    private static String pkg(String pkg) { if (!isPackage(pkg)) throw new IllegalArgumentException("not a package name"); return pkg; }

    /** True only for a command on the list above, exactly as written there. */
    public static boolean allowed(String command) {
        if (command == null) return false;
        if (FIXED.contains(command)) return true;
        for (String prefix : new String[]{"dumpsys package ", "pm disable-user --user 0 ", "pm enable "})
            if (command.startsWith(prefix) && isPackage(command.substring(prefix.length()))) return true;
        return false;
    }

    /** True for the two commands that change the TV; everything else on the list only reads. */
    public static boolean changes(String command) {
        return allowed(command) && (command.startsWith("pm disable-user ") || command.startsWith("pm enable "));
    }
}
