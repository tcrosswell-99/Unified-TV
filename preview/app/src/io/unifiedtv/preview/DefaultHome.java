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
    /** Only the launcher app carries the Home entry; the preview never does. */
    static boolean available(Context c){
        try{c.getPackageManager().getActivityInfo(entry(c),PackageManager.MATCH_DISABLED_COMPONENTS);return true;}catch(PackageManager.NameNotFoundException|RuntimeException e){return false;}
    }
    static Intent homeIntent(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
    static boolean offered(Context c){return c.getPackageManager().getComponentEnabledSetting(entry(c))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;}
    /**
     * Android's own record first: on Android 10+ the Home role is the choice the owner made in the system dialog. The
     * resolved Home intent can lag behind it for a moment after the choice (seen on the TCL, 25 October 2026).
     */
    static boolean isDefault(Context c){return state(c)==State.OURS;}
    /**
     * OURS: the Home button opens Unified TV. KEPT: Android records Unified TV as Home (the role), but the Home button
     * still resolves to another Home; some TVs keep their own (owner's TCL, 25 October 2026: com.google.android.tvlauncher).
     * NOT: the Home button opens another Home.
     */
    enum State{OURS,KEPT,NOT}
    static State state(Context c){
        boolean role=false;
        if(Build.VERSION.SDK_INT>=29)try{android.app.role.RoleManager roles=c.getSystemService(android.app.role.RoleManager.class);role=roles!=null&&roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)&&roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);}catch(RuntimeException ignored){}
        String resolved=null;
        try{ResolveInfo r=c.getPackageManager().resolveActivity(homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);resolved=r==null||r.activityInfo==null?null:r.activityInfo.packageName;}catch(RuntimeException ignored){}
        return state(c.getPackageName(),role,resolved);
    }
    /** Pure: {@code resolved} is the package the Home intent resolves to (null or "android", Android's chooser: no default yet). */
    static State state(String self,boolean roleHeld,String resolved){
        if(self.equals(resolved))return State.OURS;
        boolean other=resolved!=null&&!resolved.equals("android");
        if(roleHeld)return other?State.KEPT:State.OURS;
        return State.NOT;
    }
    /**
     * Recorded when the owner chose Unified TV but this TV keeps its own Home button anyway, or offers no way to choose:
     * Settings then says so plainly and stops offering a switch that can't work. Cleared when the Home button does open
     * Unified TV, or when the owner hands Home back.
     */
    static final String FIXED="home.fixedByTv",REPORT="homeCheck";
    static boolean fixed(Context c){try{return c.getSharedPreferences(HouseholdSettings.PREFS,0).getBoolean(FIXED,false);}catch(RuntimeException e){return false;}}
    static void fixed(Context c,boolean yes){try{c.getSharedPreferences(HouseholdSettings.PREFS,0).edit().putBoolean(FIXED,yes).apply();}catch(RuntimeException ignored){}}
    static final String KEPT_WORDS="This TV keeps its own Home button. Unified TV opens as an app instead.";
    /** What the owner is told after the choice, in plain words. */
    static String words(State s){
        switch(s){
            case OURS:return "Done. The Home button now opens Unified TV.";
            case KEPT:return KEPT_WORDS;
            default:return "Nothing changed. The Home button still opens the original TV interface.";
        }
    }
    /** How long to keep asking Android after the owner's choice before concluding nothing changed. */
    static final long[] CONFIRM_MS={0,400,1000,2000};
    /**
     * After the system dialog: report what Android now records, asking again for a couple of seconds, because the choice
     * can take a moment to apply. Withdrawing the Home entry too early used to undo the owner's choice and send the TV
     * to its original Home.
     */
    static void confirm(Activity a,java.util.function.Consumer<Boolean> done){confirmState(a,s->done.accept(s==State.OURS));}
    static void confirmState(Activity a,java.util.function.Consumer<State> done){confirm(a,done,0);}
    private static void confirm(Activity a,java.util.function.Consumer<State> done,int attempt){
        if(a.isFinishing()||a.isDestroyed())return;
        State now=state(a);
        if(now==State.OURS||attempt>=CONFIRM_MS.length-1){done.accept(now);return;}
        a.getWindow().getDecorView().postDelayed(()->confirm(a,done,attempt+1),CONFIRM_MS[attempt+1]-CONFIRM_MS[attempt]);
    }
    /**
     * After the choice (owner's TCL, 25 October 2026: the owner was left on the original Home's apps screen): records what
     * Android now does, hands Home back when nothing was chosen, and returns {@code done} the result. A held role that the
     * Home button ignores (KEPT) is recorded with fixed(), so Settings stops offering the switch.
     */
    static void settle(Activity a,java.util.function.Consumer<State> done){
        confirmState(a,s->{
            if(s==State.NOT&&offered(a))withdraw(a);
            fixed(a,s==State.KEPT);
            done.accept(s);
        });
    }
    /**
     * Brings Unified TV's own Home back to the front after the system's choice screen, whatever it returned, so the owner
     * is never left on another Home; Home then reports what Android recorded (UnifiedPreviewActivity reads REPORT).
     */
    static Intent backHome(Context c){
        return new Intent(c,UnifiedPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(REPORT,true);
    }
    /** Make the entry available, then let Android ask the owner. Unified TV never assigns the default itself. */
    static Route offer(Activity a){
        a.getPackageManager().setComponentEnabledSetting(entry(a),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        return choose(a);
    }
    /** How the owner was asked: Android's Home dialog (a result comes back), its Home settings, its chooser, or not at all. */
    enum Route{ROLE,SETTINGS,CHOOSER,NONE}
    static Route choose(Activity a){
        if(Build.VERSION.SDK_INT>=29){android.app.role.RoleManager roles=a.getSystemService(android.app.role.RoleManager.class);
            try{if(roles!=null&&roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)&&!roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)){a.startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),REQUEST);return Route.ROLE;}}catch(RuntimeException ignored){}}
        Intent settings=new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
        if(settings.resolveActivity(a.getPackageManager())!=null)try{a.startActivity(settings);return Route.SETTINGS;}catch(RuntimeException ignored){}
        // Last route: with two Home apps and NO default yet, pressing Home shows Android's own chooser. With a default
        // already set, the same intent simply opens that Home: it used to drop the owner on the TV's own apps screen.
        String resolved=null;
        try{ResolveInfo r=a.getPackageManager().resolveActivity(homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);resolved=r==null||r.activityInfo==null?null:r.activityInfo.packageName;}catch(RuntimeException ignored){}
        if(!chooserHelps(resolved))return Route.NONE;
        try{a.startActivity(homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return Route.CHOOSER;}catch(RuntimeException ignored){return Route.NONE;}
    }
    /** Android's chooser appears only while no Home is the default (the intent resolves to the system's chooser). */
    static boolean chooserHelps(String resolved){return resolved==null||resolved.equals("android");}
    /** Hand Home back: with our entry switched off, Android returns to the original interface. Reversible from Settings. */
    static void withdraw(Context c){fixed(c,false);c.getPackageManager().setComponentEnabledSetting(entry(c),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);}
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
