package io.unifiedtv.preview;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import java.util.*;

/**
 * Spec 29.3 and 30: Unified TV as the TV's real Home, only when the owner chooses it, only through Android's own Home
 * selection, and always reversible. Nothing here disables, hides or uninstalls the manufacturer's interface.
 */
final class DefaultHome {
    static final int REQUEST=71;
    /** The Home entry ships switched off, so a fresh install is never a Home candidate. */
    static ComponentName entry(Context c){return new ComponentName(c.getPackageName(),"io.unifiedtv.preview.HomeEntry");}
    static Intent homeIntent(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
    static boolean offered(Context c){return c.getPackageManager().getComponentEnabledSetting(entry(c))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;}
    static boolean isDefault(Context c){
        try{ResolveInfo r=c.getPackageManager().resolveActivity(homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);return r!=null&&r.activityInfo!=null&&c.getPackageName().equals(r.activityInfo.packageName);}catch(RuntimeException e){return false;}
    }
    /** Make the entry available, then let Android ask the owner. Unified TV never assigns the default itself. */
    static void offer(Activity a){
        a.getPackageManager().setComponentEnabledSetting(entry(a),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        choose(a);
    }
    static void choose(Activity a){
        if(Build.VERSION.SDK_INT>=29){android.app.role.RoleManager roles=a.getSystemService(android.app.role.RoleManager.class);
            try{if(roles!=null&&roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)&&!roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)){a.startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),REQUEST);return;}}catch(RuntimeException ignored){}}
        Intent settings=new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
        if(settings.resolveActivity(a.getPackageManager())!=null)try{a.startActivity(settings);return;}catch(RuntimeException ignored){}
        // Last supported route: with two Home apps and no default, pressing Home shows Android's own chooser.
        try{a.startActivity(homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException ignored){}
    }
    /** Hand Home back: with our entry switched off, Android returns to the original interface. Reversible from Settings. */
    static void withdraw(Context c){c.getPackageManager().setComponentEnabledSetting(entry(c),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);}
    /** The manufacturer's own Home, when exactly one can be identified; Android's boot-time placeholder is not it. */
    static ComponentName original(Context c){
        List<ComponentName> found=new ArrayList<>();
        try{for(ResolveInfo r:c.getPackageManager().queryIntentActivities(homeIntent(),0)){if(r.activityInfo==null)continue;ComponentName n=new ComponentName(r.activityInfo.packageName,r.activityInfo.name);if(candidate(c.getPackageName(),n))found.add(n);}}catch(RuntimeException ignored){}
        return found.size()==1?found.get(0):null;
    }
    static boolean candidate(String self,ComponentName n){return !n.getPackageName().equals(self)&&!n.getClassName().endsWith("FallbackHome")&&!n.getPackageName().equals("com.android.settings");}
    /** "Open original interface for now": leaves the default untouched. False when this TV doesn't allow it. */
    static boolean openOriginal(Activity a){
        ComponentName original=original(a);if(original==null)return false;
        try{a.startActivity(homeIntent().setComponent(original).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}catch(RuntimeException e){return false;}
    }
}
