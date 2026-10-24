package io.unifiedtv.preview;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Category focus previews the right panel; Right/OK enters it, Left returns. */
public final class SettingsActivity extends Screen {
    private final String[] categories={"Connections","Profiles","Notifications","Appearance","TV & performance","Privacy & security"};
    private final Button[] navigation=new Button[6];private LinearLayout details;private int category=-1;private String detail="";
    @Override public void onCreate(Bundle state){super.onCreate(state);autoFocusButtons=false;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(30),dp(16),dp(30),dp(18));root.setBackground(MaterialScene.backdrop(this));
        column=root;text("Settings",28);LinearLayout row=new LinearLayout(this);root.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);side.setPadding(dp(12),dp(16),dp(12),dp(16));side.setBackground(MaterialScene.panel(this));row.addView(side,new LinearLayout.LayoutParams(dp(240),-1));
        ScrollView scroll=new ScrollView(this);scroll.setFocusable(false);scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setPadding(dp(24),dp(12),dp(24),dp(16));scroll.addView(details);scroll.setBackground(MaterialScene.panel(this));LinearLayout.LayoutParams pane=new LinearLayout.LayoutParams(0,-1,1);pane.setMargins(dp(20),0,0,0);row.addView(scroll,pane);
        column=side;
        for(int i=0;i<categories.length;i++){final int index=i;Button n=button(categories[i],()->enter());n.setTextSize(17);n.setId(View.generateViewId());navigation[i]=n;
            n.setOnFocusChangeListener((v,focused)->{if(focused&&category!=index)show(index);});
            n.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(e.getAction()==KeyEvent.ACTION_DOWN)enter();return true;}return false;});}
        for(int i=0;i<navigation.length;i++){navigation[i].setNextFocusUpId(navigation[Math.max(0,i-1)].getId());navigation[i].setNextFocusDownId(navigation[Math.min(5,i+1)].getId());}
        int selected=state==null?Math.max(0,getIntent().getIntExtra("category",0)):state.getInt("category",0);show(Math.min(5,selected));navigation[category].requestFocus();setContentView(root);
    }
    private void enter(){for(int i=0;i<details.getChildCount();i++){View v=details.getChildAt(i);if(v.isFocusable()){v.requestFocus();return;}}}
    @Override Button button(String title,Runnable action){Button b=super.button(title,action);b.setTextSize(17);b.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(e.getAction()==KeyEvent.ACTION_DOWN&&category>=0)navigation[category].requestFocus();return true;}return false;});return b;}
    private void heading(String title,String description){details.removeAllViews();column=details;text(title,25);text(description,17);}
    private void show(int selected){category=selected;detail="";heading(categories[selected],new String[]{"Home Assistant, media libraries and services","Choose who is watching and protect adult settings","Manage household interruptions and camera alerts","Shape the look of Home and your Gallery","TV controls and a read-only performance check","Private connections, microphone use and access"}[selected]);
        switch(selected){
        case 0:
            button("Home Assistant",()->startActivity(new Intent(this,ServiceDiscoveryActivity.class)));
            button("Your Films & TV",()->startActivity(new Intent(this,DlnaLibraryActivity.class).putExtra("setup",true)));
            button("Media server accounts",()->new AlertDialog.Builder(this).setTitle("Media account").setItems(new String[]{"Adult","Child"},(d,n)->connection(n==0?"media-adult":"media-child")).show());
            button("Connected services",this::services);break;
        case 1:
            text("Current profile: "+(EditLock.child(this)?"Child":"Adult"),18);
            button("Switch profile",()->chooseProfile(this));button("Set or change adult PIN",()->EditLock.ask(this,()->EditLock.set(this)));
            text("Child restrictions require a separate restricted media account. This does not lock Android or switch streaming-app accounts.",16);break;
        case 2:
            button("Doorbell, reminders & room setup",()->EditLock.ask(this,()->startActivity(new Intent(this,HouseholdActivity.class))));
            button("Stop household alerts",()->{stopService(new Intent(this,HouseholdService.class));note("Household alerts stopped");});
            text("Ring takes priority. Native Samsung and third-party protected-video behaviour require separate device tests.",16);break;
        case 3:
            toggle("Reduce motion","reduceMotion");toggle("High contrast","highContrast");toggle("Reduce transparency","opaque");toggle("Lightweight rendering","lightweight");
            button("Gallery & slideshow",()->startActivity(new Intent(this,GalleryActivity.class).putExtra("edit",true)));break;
        case 4:
            button("Picture, sound, network & Bluetooth",()->{try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){note("Use the TV's own Settings");}});
            button("Performance check",()->startActivity(new Intent(this,PerformanceActivity.class)));
            text("No automatic cleanup or factory package changes are performed.",16);break;
        case 5:
            button("Manage private connections",()->show(0));button("Set or change adult PIN",()->EditLock.ask(this,()->EditLock.set(this)));
            text("Credentials are encrypted locally and excluded from backup. Speech uses the TV's installed provider only after activation and may use its cloud service. General OpenAI discovery is not connected.",16);
            button("Integration readiness",()->new AlertDialog.Builder(this).setTitle("Preview limitations").setMessage("VPN tunnel visibility does not verify protected playback. NAS, Ring, reminders, microphone and manufacturer controls need their own acceptance tests. The Android APK cannot run on native Tizen.").setPositiveButton("Close",null).show());break;
        }
    }
    private void services(){detail="services";heading("Connected services","Available services on this Android TV");button("NordVPN",()->VpnGate.show(this));button("Ask your TV",()->startActivity(new Intent(this,VoiceActivity.class)));button("Amazon Music",()->startActivity(new Intent(this,MusicActivity.class)));button("AirPlay receiver",this::airplay);}
    private void airplay(){android.content.pm.PackageManager pm=getPackageManager();Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);for(android.content.pm.ResolveInfo ri:pm.queryIntentActivities(q,0))if(ri.loadLabel(pm).toString().toLowerCase(java.util.Locale.ROOT).contains("airpin")){Intent i=pm.getLeanbackLaunchIntentForPackage(ri.activityInfo.packageName);if(i!=null){startActivity(i);return;}}note("No supported receiver found");}
    private void toggle(String name,String key){CheckBox c=new CheckBox(this);c.setText(name);c.setTextColor(-1);c.setTextSize(17);c.setPadding(8,dp(10),8,dp(10));c.setChecked(getSharedPreferences("unified-tv-preview",0).getBoolean(key,false));c.setOnCheckedChangeListener((v,on)->getSharedPreferences("unified-tv-preview",0).edit().putBoolean(key,on).apply());c.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_DPAD_LEFT){navigation[category].requestFocus();return true;}return false;});column.addView(c);}
    private void connection(String key){startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key",key));}
    @Override public void onBackPressed(){if(!detail.isEmpty())show(category);else if(!navigation[category].hasFocus())navigation[category].requestFocus();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("category",category);}
    static void chooseProfile(android.app.Activity a){new AlertDialog.Builder(a).setTitle("Who's watching?").setItems(new String[]{"Adult","Child"},(d,n)->{Runnable change=()->{a.getSharedPreferences("unified-tv-preview",0).edit().putString("profile",n==0?"adult":"child").apply();a.recreate();};if(n==0)EditLock.ask(a,change);else if(a.getSharedPreferences("unified-tv-preview",0).getString("pin","").isEmpty())new AlertDialog.Builder(a).setMessage("Set an adult PIN before enabling Child mode. Use a separate restricted media account.").setPositiveButton("OK",null).show();else change.run();}).show();}
}
