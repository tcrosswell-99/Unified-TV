package io.unifiedtv.preview;

import android.content.Intent;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import org.json.JSONObject;

/** Owner-defined adapters; no manufacturer endpoint, camera or script identifier in public code. */
public final class HouseholdActivity extends Screen {
    @Override public void onCreate(Bundle b){super.onCreate(b);show();}
    private void show(){
        page("Household alerts & room actions");JSONObject config;
        try{config=new Vault(this).read("household");}catch(Exception e){config=new JSONObject();}
        text("Experimental. Requires your Home Assistant connection. Alarm, Ring and reminder overlays need TV permission and real playback tests. No native Tizen overlay is possible from this APK.",20);
        EditText room=field("Room profile: tcl, sony or frame",getSharedPreferences("unified-tv-preview",0).getString("room","tcl"),false);
        EditText door=field("Doorbell button binary sensor entity",config.optString("door"),false);
        EditText person=field("Person detection binary sensor entity (not generic motion)",config.optString("person"),false);
        EditText camera=field("Live camera entity",config.optString("camera"),false);
        EditText calendar=field("Tottenham fixtures calendar entity (optional)",config.optString("calendar"),false);
        EditText xbox=field("Owner script for Xbox power and HDMI (optional)",config.optString("xbox"),false);
        EditText plus=field("Owner script for native Samsung TV Plus (optional)",config.optString("tvplus"),false);
        EditText art=field("Owner script to restore native Frame Art Mode (optional)",config.optString("art"),false);
        EditText ringEnter=field("Frame Ring entry script: capture prior state then change input, never wake an off TV",config.optString("ringEnter"),false);
        EditText ringExit=field("Frame Ring return script: restore the captured mode and input",config.optString("ringExit"),false);
        // On unless the household turns it off: found from Home Assistant's own smoke and carbon monoxide sensors, nothing to type.
        CheckBox alarms=new CheckBox(this);alarms.setText("Show smoke and carbon monoxide alarms on this TV, above the doorbell");alarms.setTextColor(-1);alarms.setChecked(config.optBoolean("alarms",true));column.addView(alarms);
        CheckBox reminders=new CheckBox(this);reminders.setText("Allow private reminder events from Home Assistant");reminders.setTextColor(-1);reminders.setChecked(config.optBoolean("reminders"));column.addView(reminders);
        button("Save household setup",()->{
            try{
                String profile=room.getText().toString().trim();if(!profile.matches("tcl|sony|frame"))throw new IllegalArgumentException();
                JSONObject value=new JSONObject();
                String[] names={"door","person","camera","xbox","tvplus","art","ringEnter","ringExit"};EditText[] fields={door,person,camera,xbox,plus,art,ringEnter,ringExit};
                for(int n=0;n<names.length;n++){
                    String v=fields[n].getText().toString().trim();String domain=n<2?"binary_sensor":n==2?"camera":"script";
                    if(!v.isEmpty()&&!v.matches(domain+"\\.[a-z0-9_]+"))throw new IllegalArgumentException();value.put(names[n],v);
                }
                String cal=calendar.getText().toString().trim();if(!cal.isEmpty()&&!cal.matches("calendar\\.[a-z0-9_]+"))throw new IllegalArgumentException();
                value.put("calendar",cal).put("reminders",reminders.isChecked()).put("alarms",alarms.isChecked());new Vault(this).save("household",value);
                getSharedPreferences("unified-tv-preview",0).edit().putString("room",profile).apply();note("Saved locally. Restart Home to update room shortcuts.");
            }catch(Exception e){note("Check room and entity names. Setup was not saved.");}
        });
        button("Grant optional alert overlay permission",()->{
            try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));}catch(Exception e){note("Open this TV's special app access settings manually");}
        });
        button("Start household alerts",()->{
            if(!Settings.canDrawOverlays(this)){note("Grant overlay permission first. No alert service started.");return;}
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},8);return;}
            startForegroundService(new Intent(this,HouseholdService.class));note("Alert service requested. Check its notification for actual connection state.");
        });
        button("Stop household alerts",()->{stopService(new Intent(this,HouseholdService.class));note("Household alerts stopped");});
        button("Request native Frame Art Mode",()->{if(getSharedPreferences("unified-tv-preview",0).getString("room","").equals("frame"))action(this,"art");else note("This action belongs to the Frame room profile");});
        button("Forget household setup",()->{stopService(new Intent(this,HouseholdService.class));new Vault(this).forget("household");show();});
        button("Back",this::finish);
    }
    static void action(android.app.Activity activity,String name){
        java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();executor.execute(()->{
            boolean ok=false;
            try{JSONObject config=new Vault(activity).read("ha"),house=new Vault(activity).read("household");String entity=house.optString(name);
                if(!entity.matches("script\\.[a-z0-9_]+"))throw new IllegalArgumentException();
                LocalApi.post(config,"/api/services/script/turn_on",new JSONObject().put("entity_id",entity));ok=true;
            }catch(Exception ignored){}
            final boolean sent=ok;activity.runOnUiThread(()->Toast.makeText(activity,sent?"Action requested · verify the TV response":"Action unavailable · check household setup",Toast.LENGTH_LONG).show());executor.shutdown();
        });
    }
    static void backgroundAction(android.content.Context context,String key){
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();worker.execute(()->{
            try{JSONObject config=new Vault(context).read("ha"),house=new Vault(context).read("household");String script=house.optString(key);
                if(script.matches("script\\.[a-z0-9_]+"))LocalApi.post(config,"/api/services/script/turn_on",new JSONObject().put("entity_id",script));
            }catch(Exception ignored){}finally{worker.shutdown();}
        });
    }
}
