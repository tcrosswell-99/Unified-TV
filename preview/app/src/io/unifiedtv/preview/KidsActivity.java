package io.unifiedtv.preview;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import java.util.*;

/**
 * Settings > Profiles & Family > Kids settings (behind the adult PIN). One page: the Kids folders on the NAS, the apps Kids may
 * open, the optional Kids app guard, and where the TV's own parental controls are. It says plainly what each one can and
 * can't do; none of it has been tried on the household's TVs yet. A settings screen, so the glass sidebar over a still Home
 * (OD-11), redrawn in place each time it comes back to the front.
 */
public final class KidsActivity extends GlassScreen {
    @Override protected void onResume(){super.onResume();show();}
    private void show(){
        page("Kids settings","Profiles & Family");boolean kids=Profiles.kids(prefs);
        card();text("Kids is on now: "+(kids?"yes":"no")+". "+(prefs.getString("pin","").isEmpty()?"Set an adult PIN first: Kids can't be switched on without one.":"Leaving Kids always asks for the adult PIN."),18);

        group("Films & TV for Kids");card();
        org.json.JSONObject folders;try{folders=new Vault(this).read(KidsPolicy.STORE);}catch(Exception e){folders=new org.json.JSONObject();}
        int chosen=KidsPolicy.roots(folders).size();
        text(chosen==0?"No Kids folders yet. Kids sees “"+KidsPolicy.ASK_ADULT+"” instead of the library.":chosen+(chosen==1?" Kids folder":" Kids folders")+" on "+(folders.optString("name").isEmpty()?"your media server":folders.optString("name"))+".",17);
        if(kids)text("Switch to Adults to choose Kids folders.",16);
        else button(chosen==0?"Choose Kids folders":"Choose Kids folders again",()->startActivity(new Intent(this,DlnaLibraryActivity.class).putExtra("kidsSetup",true)));
        if(chosen>0)button("Stop showing Kids folders",()->{new Vault(this).forget(KidsPolicy.STORE);note("Kids folders cleared");show();});
        text("In Kids, browsing, the Home row, resume and playback stay inside these folders; the player checks with the media server again before every Kids title. Ratings: "+HouseholdSettings.scheme(this).label+" (Settings > Profiles & Family). Where the server sends a rating, Kids gets only "+HouseholdSettings.scheme(this).allowedWords()+". Anything else rated (12, 15, PG-13, TV-14, NR and the rest) is left out. A title with no rating is shown only because it is inside a Kids folder.",16);

        group("Apps for Kids");card();
        text("In Kids, Unified TV opens only the apps switched on here, from Home, Apps, voice and everywhere else. Netflix, Disney+ and others have their own kids profiles: choose those inside each app.",16);
        Set<String> approved=KidsPolicy.apps(prefs.getString(KidsPolicy.APPS_PREF,""));
        String room=prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");
        if("sony".equals(room))app("Xbox (HDMI)","xbox",approved);
        if("frame".equals(room))app("Samsung TV Plus","tvplus",approved);
        PackageManager pm=getPackageManager();Map<String,String> apps=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){if(r.activityInfo==null||r.activityInfo.packageName.equals(getPackageName()))continue;String label=String.valueOf(r.loadLabel(pm));if(!apps.containsValue(r.activityInfo.packageName))apps.put(label+"\u0000"+r.activityInfo.packageName,r.activityInfo.packageName);}
        for(Map.Entry<String,String> e:apps.entrySet())app(e.getKey().substring(0,e.getKey().indexOf('\u0000')),e.getValue(),approved);

        group("Kids app guard");card();
        String setting="";try{setting=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);}catch(RuntimeException ignored){}
        boolean allowed=KidsGuard.enabledIn(setting,getPackageName(),KidsGuardService.class.getName());
        text("Optional. While Kids is on, if an app that isn't switched on above comes to the front (from the TV's own Home, a notification or another app), Unified TV Home comes back with a short message. It does nothing in Adults.",16);
        switchRow("Use the Kids app guard",prefs.getBoolean(KidsGuard.PREF,false),yes->prefs.edit().putBoolean(KidsGuard.PREF,yes).apply());
        text("TV permission: "+(allowed?"allowed.":"not allowed yet. The guard can't work until the TV allows it."),17);
        button("Open the TV's accessibility settings",()->Launch.unlocked(this,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),"This TV doesn't offer that setting. Use the computer step below."));
        text("On the TV: Settings > Device Preferences > Accessibility (Android TV), or Settings > System > Accessibility (Google TV), then “Unified TV Kids guard”, then turn it on. Some newer TVs first need “Allow restricted settings” in Unified TV's App info.",16);
        text("Or from a computer on the same network, with network debugging on (it adds the guard without switching off anything else):",16);
        android.widget.TextView line=text(KidsGuard.adbLine(new ComponentName(this,KidsGuardService.class).flattenToString()),14);line.setTypeface(android.graphics.Typeface.MONOSPACE);
        text("Turn network debugging off again afterwards.",16);
        text("What it can't do: Android lets no app lock a TV without a factory reset to set it up as a managed device, so this is a guard, not a lock. A determined child could switch it off in the TV's Settings, which is why system Settings is sent back to Home in Kids unless an adult opened it from Unified TV's Settings with the PIN (then it stays open for ten minutes). A force stop of Unified TV can also switch it off on some TVs. The TV's input button stays usable, and on many TVs that includes broadcast TV.",16);

        group("The TV's own controls");card();
        text("Sony (Google TV): Google TV has its own kids profiles with app limits, set up with a Google account on the TV (Settings > Accounts & sign-in > Add a kid). TCL (Android TV): some models offer a restricted profile in Settings > Device Preferences > Security & restrictions. Use these as well where they exist. Neither has been tried with Unified TV yet.",16);
        card();button("Back",this::finish);
    }
    private void app(String label,String id,Set<String> approved){
        switchRow(label,approved.contains(id),yes->{String saved=prefs.getString(KidsPolicy.APPS_PREF,"");if(KidsPolicy.apps(saved).contains(id)!=yes)prefs.edit().putString(KidsPolicy.APPS_PREF,KidsPolicy.toggle(saved,id)).apply();});
    }
}
