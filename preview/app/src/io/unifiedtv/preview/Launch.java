package io.unifiedtv.preview;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Every route into another app, from Home or Apps, comes through here, so the Live TV gate can't be bypassed (P05-03). */
final class Launch {
    /** Opens the app, or explains why not; null when it opened. A protected Live TV app needs the recorded protection check. */
    static String open(Activity a,String pkg,String label){
        if(pkg==null||pkg.isEmpty())return label+" is not installed on this TV";
        if(Endpoint.protectedApp(pkg,label,a.getSharedPreferences("unified-tv-preview",0).getString("livePackage",""))){
            LiveTvProtection.Decision d=LiveTvProtection.now(a,pkg);
            if(d!=LiveTvProtection.Decision.ALLOW){VpnGate.show(a,LiveTvProtection.message(d));return "";}
        }
        PackageManager pm=a.getPackageManager();Intent intent=pm.getLeanbackLaunchIntentForPackage(pkg);if(intent==null)intent=pm.getLaunchIntentForPackage(pkg);
        if(intent==null)return label+" can't be opened from here";
        boolean live=Endpoint.protectedApp(pkg,label,a.getSharedPreferences("unified-tv-preview",0).getString("livePackage",""));
        try{if(live)LiveTvGuard.start(a);a.startActivity(intent);return null;}catch(RuntimeException e){if(live)LiveTvGuard.stop(a);return "Unable to open "+label;}
    }
    static void info(Activity a,String pkg){try{a.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.fromParts("package",pkg,null)));}catch(RuntimeException e){android.widget.Toast.makeText(a,"App information isn't available on this TV",android.widget.Toast.LENGTH_LONG).show();}}
}
