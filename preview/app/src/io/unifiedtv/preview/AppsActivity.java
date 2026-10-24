package io.unifiedtv.preview;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.*;
import android.os.*;
import android.widget.*;
import java.util.*;

/** P05-03: every installed app, in a compact grid. Hold OK for Open, Add to / Remove from Home and App information. */
public final class AppsActivity extends Screen {
    private SharedPreferences prefs;private String room;
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("unified-tv-preview",0);room=prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.UK).contains("sony")?"sony":"tcl");show(null);}
    private List<String> dock(){return DockPolicy.order(room,prefs.contains("dock")?prefs.getString("dock",""):null);}
    private void show(String focus){
        page("Apps");text("Hold OK on an app to add it to Home or remove it.",17);
        PackageManager pm=getPackageManager();Map<String,ResolveInfo> apps=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){if(r.activityInfo==null||r.activityInfo.packageName.equals(getPackageName()))continue;String label=String.valueOf(r.loadLabel(pm));if(!apps.containsKey(label+"\u0000"+r.activityInfo.packageName))apps.put(label+"\u0000"+r.activityInfo.packageName,r);}
        List<String> home=dock();Set<String> seen=new HashSet<>();LinearLayout row=null;int n=0;Button target=null;
        // Unified TV's own shortcuts (no app of their own) come first, so a removed one can always be put back.
        for(String id:DockPolicy.defaults(room)){if(DockPolicy.packages(id).length>0||id.equals("music"))continue;String label=SHORTCUTS.getOrDefault(id,id);
            if(n++%5==0){row=new LinearLayout(this);column.addView(row);}
            boolean onHome=home.contains(id);Button open=styled(label+(onHome?"  ·  On Home":""),()->shortcut(id));open.setTextSize(16);open.setOnLongClickListener(v->{menu(id,label);return true;});
            open.setContentDescription(label+(onHome?", on Home":"")+". Hold OK for options.");row.addView(open,new LinearLayout.LayoutParams(0,-2,1));if(id.equals(focus)||(target==null&&n==1))target=open;}
        for(Map.Entry<String,ResolveInfo> e:apps.entrySet()){String pkg=e.getValue().activityInfo.packageName;if(!seen.add(pkg))continue;String label=e.getKey().substring(0,e.getKey().indexOf('\u0000'));
            if(n++%5==0){row=new LinearLayout(this);column.addView(row);}
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(6),dp(6),dp(6),dp(6));
            ImageView icon=new ImageView(this);try{icon.setImageDrawable(pm.getApplicationIcon(pkg));}catch(Exception ignored){}icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(icon,new LinearLayout.LayoutParams(-1,dp(56)));
            boolean onHome=home.contains(DockPolicy.canonical(pkg));
            Button open=styled(label+(onHome?"  ·  On Home":""),()->launch(pkg,label));open.setTextSize(16);open.setOnLongClickListener(v->{menu(pkg,label);return true;});
            open.setContentDescription(label+(onHome?", on Home":"")+". Hold OK for options.");card.addView(open,new LinearLayout.LayoutParams(-1,-2));
            row.addView(card,new LinearLayout.LayoutParams(0,-2,1));if(pkg.equals(focus))target=open;else if(target==null&&n==1)target=open;
        }
        if(apps.isEmpty())text("No apps found.",20);
        button("Back to Home",this::finish);if(target!=null)target.requestFocus();
    }
    private static final Map<String,String> SHORTCUTS=new HashMap<>();
    static{SHORTCUTS.put("live","Live TV");SHORTCUTS.put("xbox","Xbox");SHORTCUTS.put("tvplus","Samsung TV Plus");}
    private void shortcut(String id){if(id.equals("live"))launch(LiveTvProtection.livePackage(this),"Live TV");else HouseholdActivity.action(this,id);}
    private void launch(String pkg,String label){String why=Launch.open(this,pkg,label);if(why!=null&&!why.isEmpty())note(why);}
    private void menu(String pkg,String label){
        boolean onHome=dock().contains(DockPolicy.canonical(pkg));
        String[] actions={"Open",onHome?"Remove from Home":"Add to Home","App information","Cancel"};
        new android.app.AlertDialog.Builder(this).setTitle(label).setItems(actions,(d,which)->{
            if(which==0){if(SHORTCUTS.containsKey(pkg))shortcut(pkg);else launch(pkg,label);}
            else if(which==1)EditLock.ask(this,()->{String next=onHome?DockPolicy.remove(dock(),pkg):DockPolicy.add(room,dock(),pkg);prefs.edit().putString("dock",next).apply();note(onHome?"Removed from Home":"Added to Home");show(pkg);});
            else if(which==2){if(SHORTCUTS.containsKey(pkg))note(label+" is a Unified TV shortcut");else Launch.info(this,pkg);}
        }).show();
    }
}
