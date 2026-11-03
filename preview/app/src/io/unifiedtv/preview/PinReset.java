package io.unifiedtv.preview;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.*;

/**
 * "Forgot the PIN?" (SET-043; owner, 25 October 2026): an adult at a computer with ADB access to this TV resets it:
 *
 *   adb shell am broadcast -a io.unifiedtv.RESET_PIN --include-stopped-packages -n <package>/io.unifiedtv.preview.PinReset
 *
 * (--include-stopped-packages: a force-stopped app otherwise receives no broadcast at all.)
 *
 * It clears the adult PIN and its lock-out, returns the TV to Adults and switches the Kids app guard off. Nothing else
 * (folders, approved apps, connections) is touched, and nothing is logged.
 *
 * Why this is safe: the manifest protects the receiver with android.permission.WRITE_SECURE_SETTINGS, so Android
 * delivers the broadcast only from a sender holding that permission. Its protection level is
 * signature|privileged|development (with role|installer on current AOSP: core/res/AndroidManifest.xml; "Not for use
 * by third-party applications" in the Manifest.permission reference), so an app from a store cannot request it. The
 * ADB shell (com.android.shell) holds it, which is why `adb shell am broadcast` works (packages/Shell/AndroidManifest.xml).
 * The only other holders are platform-signed or privileged system apps, the system's installer or role holders, and
 * apps granted it with `pm grant` over ADB: all of them already need the access this reset relies on. ADB must be
 * switched on in the TV's developer options, which the household controls.
 */
public final class PinReset extends BroadcastReceiver {
    static final String ACTION="io.unifiedtv.RESET_PIN";
    /** The preferences the reset removes: the PIN, its failure count and wait, and the Kids guard's pass. */
    static final List<String> REMOVED=Collections.unmodifiableList(Arrays.asList("pin","pinFailures","pinWait",KidsGuard.PASS));
    /** The preferences it sets: Adults, and the Kids guard off. */
    static Map<String,Object> set(){Map<String,Object> m=new LinkedHashMap<>();m.put(Profiles.PREF,Profiles.ADULT);m.put(KidsGuard.PREF,false);return m;}
    /** Pure: the settings after a reset. */
    static Map<String,Object> after(Map<String,?> before){Map<String,Object> out=new LinkedHashMap<>(before);for(String k:REMOVED)out.remove(k);out.putAll(set());return out;}
    /** The exact command for this app (the preview and the launcher are different packages; the class keeps its name). */
    static String command(String pkg){return "adb shell am broadcast -a "+ACTION+" --include-stopped-packages -n "+pkg+"/"+PinReset.class.getName();}

    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        SharedPreferences.Editor e=context.getSharedPreferences(HouseholdSettings.PREFS,0).edit();
        for(String k:REMOVED)e.remove(k);
        e.putString(Profiles.PREF,Profiles.ADULT).putBoolean(KidsGuard.PREF,false).commit();
        setResultCode(android.app.Activity.RESULT_OK);
    }
}
