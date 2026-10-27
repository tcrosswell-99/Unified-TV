package io.unifiedtv.preview;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;

/**
 * The optional Kids app guard (KidsGuard has the decision and the honest limits). It listens only for "a window came to
 * the front" and reads only that window's package name: no screen content, no keys, no text. In Adults, or while an adult
 * has the guard switched off in Kids settings, it does nothing. Android lets an enabled accessibility service bring its
 * own app to the front from the background, which is what makes the return to Home possible. While it is on, the same
 * package name also ends the Live TV safety net's watch when Live TV leaves the front (LiveTvGuard; in every profile).
 */
public final class KidsGuardService extends AccessibilityService {
    private String lastPkg="";private long lastAt;
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||event.getPackageName()==null)return;
        String pkg=event.getPackageName().toString();
        SharedPreferences p=getSharedPreferences("unified-tv-preview",0);long now=System.currentTimeMillis();
        String keyboard="";try{keyboard=KidsGuard.keyboard(android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD));}catch(RuntimeException ignored){}
        // OD-26: the same package name tells the Live TV safety net that Live TV has left the front (LiveTvGuard.left).
        LiveTvGuard.front(this,pkg,keyboard);
        int decision=KidsGuard.decide(pkg,getPackageName(),Profiles.kids(p),p.getBoolean(KidsGuard.PREF,false),KidsPolicy.apps(p.getString(KidsPolicy.APPS_PREF,"")),KidsGuard.pass(p.getLong(KidsGuard.PASS,0),now),keyboard);
        if(decision!=KidsGuard.RETURN)return;
        // One return per app per moment: the app's own follow-up windows don't each send Home again.
        if(pkg.equals(lastPkg)&&now-lastAt<KidsGuard.REPEAT_MS)return;
        lastPkg=pkg;lastAt=now;
        p.edit().putString(KidsGuard.LOG,KidsGuard.log(p.getString(KidsGuard.LOG,""),pkg,decision,now)).apply();
        String label;try{label=String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)));}catch(Exception e){label="";}
        try{startActivity(new Intent(this,UnifiedPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("kidsGuard",label));}
        catch(RuntimeException e){performGlobalAction(GLOBAL_ACTION_HOME);}
    }
    @Override public void onInterrupt(){}
}
