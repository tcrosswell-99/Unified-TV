package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owner-defined adapters; no manufacturer endpoint, camera or script identifier in public code.
 * Content first: each item is chosen by remote from the household's own Home Assistant. Typing an entity ID is an
 * advanced fallback only, with the previous screen's validation.
 */
public final class HouseholdActivity extends Screen {
    /** Plain names for each household record key, in screen order. */
    private static final String[][] LABELS={
        {"door","Doorbell","your doorbell"},{"person","Person detection","person detection"},{"camera","Doorbell camera","your doorbell camera"},
        {"calendar","Fixtures calendar (your team)","your fixtures calendar"},{"xbox","Xbox power and HDMI script","your Xbox script"},
        {"tvplus","Samsung TV Plus script","your Samsung TV Plus script"},{"art","Art Mode return script","your Art Mode script"},
        {"ringEnter","Doorbell entry script","your doorbell entry script"},{"ringExit","Doorbell return script","your doorbell return script"}};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean active;
    /** The latest look at Home Assistant; null while checking. */
    private HaEntities.Snapshot snapshot;
    private boolean technical;
    /** The button to keep focus on after the screen is rebuilt, so the remote stays where it was. */
    private String focusKey;
    private final List<String> found=new ArrayList<>();

    @Override public void onCreate(Bundle b){super.onCreate(b);active=true;show();refresh();}
    @Override protected void onRestart(){super.onRestart();if(snapshot!=null&&!HaStatus.AUTHORISED.equals(snapshot.status)){snapshot=null;show();refresh();}}
    @Override protected void onDestroy(){active=false;worker.shutdownNow();super.onDestroy();}

    private JSONObject config(){try{return new Vault(this).read("household");}catch(Exception e){return new JSONObject();}}
    private String room(){
        String saved=getSharedPreferences("unified-tv-preview",0).getString("room","");
        return HaEntities.room(saved)?saved:Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl";
    }

    /** One bounded read of Home Assistant, off the UI thread; then fill what has exactly one clear answer. */
    private void refresh(){
        worker.execute(()->{
            JSONObject ha;try{ha=new Vault(getApplicationContext()).read("ha");}catch(Exception e){ha=new JSONObject();}
            HaEntities.Snapshot next=HaEntities.fetch(ha);
            runOnUiThread(()->{if(!active)return;snapshot=next;autoSelect();show();});
        });
    }
    private void autoSelect(){
        found.clear();if(snapshot==null||snapshot.entities==null)return;
        JSONObject config=config();JSONArray decided=config.optJSONArray("decided");boolean changed=false;
        for(String[] label:LABELS){
            String key=label[0];if(!config.optString(key).isEmpty()||contains(decided,key)||!visible(key))continue;
            HaEntities.Candidate only=HaEntities.choices(key,snapshot.entities,config.optString("door")).auto();
            if(only==null)continue;
            try{put(config,key,only.id,only.name);changed=true;found.add("Found "+label[2]+": "+only.name);}catch(Exception ignored){}
        }
        if(changed)try{new Vault(this).save("household",config);}catch(Exception e){found.clear();note("Couldn't save on this TV. Nothing was changed.");}
    }

    private void show(){
        page("Household alerts & room actions");JSONObject config=config();
        // Spec 11 Connections: one of the five status words, then a plain sentence.
        String status=snapshot==null?null:snapshot.status;
        text(status==null?"Home Assistant: checking...":"Home Assistant: "+status,22);
        if(status!=null){
            String why=snapshot.tooLarge?"Your Home Assistant has too many devices to list here. Use \"Type it instead (advanced)\" for each item.":HaStatus.explain(status);
            text(why,18);
            if(!HaStatus.AUTHORISED.equals(status)){
                button("Set up Home Assistant",()->startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key","ha")));
                if(!HaStatus.NOT_CONFIGURED.equals(status))button("Check again",()->{snapshot=null;show();refresh();});
            }
        }
        for(String line:found)text(line+". Change it below if that's wrong.",18);
        text("Experimental. Requires your Home Assistant connection. Alarm, Ring and reminder overlays need TV permission and real playback tests. No native Tizen overlay is possible from this APK.",20);

        String room=room();
        keep("room",button("This TV: "+HaEntities.roomName(room),this::chooseRoom));
        for(String[] label:LABELS){if(!visible(label[0]))continue;String key=label[0];keep(key,button(label[1]+": "+current(config,key),()->choose(key)));}
        if(room.equals("frame"))text("On The Frame, the doorbell entry script should remember what the TV was showing, then change input, and never wake a TV that's off. The return script puts back what was there.",17);
        List<String> waiting=snapshot==null?null:HaEntities.waitingDoorbells(snapshot.entities);
        if(waiting!=null&&!waiting.isEmpty()&&config.optString("door").isEmpty())
            text("Home Assistant also shows "+waiting.get(0)+" as a newer kind of doorbell event. This preview can't listen to that kind yet, so choose a doorbell sensor if you have one.",17);

        // On unless the household turns it off: found from Home Assistant's own smoke and carbon monoxide sensors, nothing to type.
        // Owner exception to SET-053: switching alarms off takes the adult PIN and a confirmation; switching them on never does.
        // While off, Home and Settings say "Alarms off on this TV" (AlarmWatch).
        CheckBox alarms=new CheckBox(this);alarms.setText("Show smoke and carbon monoxide alarms on this TV, above the doorbell");alarms.setTextColor(-1);alarms.setChecked(config.optBoolean("alarms",true));column.addView(alarms);
        alarms.setOnCheckedChangeListener((v,on)->{
            if(on){saveFlag("alarms",true);return;}
            alarms.setChecked(true);   // stays on until the adult has confirmed (this re-enters with on=true and saves on)
            EditLock.ask(this,()->new AlertDialog.Builder(this).setTitle("Switch alarms off on this TV?").setMessage("Smoke and carbon monoxide alarms will not appear on this TV. Home will show \""+AlarmWatch.OFF_NOTICE+"\" until an adult switches them back on. Your detectors and their own alarms are not affected.")
                .setPositiveButton("Switch off",(d,w)->{focusKey="alarms";saveFlag("alarms",false);show();}).setNegativeButton("Keep them on",null).show());
        });
        if(!config.optBoolean("alarms",true))text(AlarmWatch.OFF_NOTICE+".",18);
        CheckBox reminders=new CheckBox(this);reminders.setText("Allow private reminder events from Home Assistant");reminders.setTextColor(-1);reminders.setChecked(config.optBoolean("reminders"));column.addView(reminders);
        reminders.setOnCheckedChangeListener((v,on)->saveFlag("reminders",on));
        button("Grant optional alert overlay permission",()->{
            Launch.unlocked(this,new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())),"Open this TV's special app access settings manually");
        });
        button("Start household alerts",()->{getSharedPreferences("unified-tv-preview",0).edit().putBoolean(AlarmWatch.STOPPED,false).apply();
            if(!Settings.canDrawOverlays(this)){note("Grant overlay permission first. No alert service started.");return;}
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},8);return;}
            startForegroundService(new Intent(this,HouseholdService.class));note("Alert service requested. Check its notification for actual connection state.");
        });
        button("Stop household alerts",()->EditLock.ask(this,()->new AlertDialog.Builder(this).setTitle("Stop household alerts?").setMessage("Smoke and carbon monoxide alarms and the doorbell will not appear on this TV until alerts are started again.")
            .setPositiveButton("Stop alerts",(d,w)->{getSharedPreferences("unified-tv-preview",0).edit().putBoolean(AlarmWatch.STOPPED,true).apply();stopService(new Intent(this,HouseholdService.class));note("Household alerts stopped");}).setNegativeButton("Keep alerts on",null).show()));
        // OD-07: no detectors are assumed. These test events are labelled TEST on screen and never reach Home Assistant.
        text("Test the alarm screen. Each step shows exactly what a real alarm would, marked TEST.",17);
        button("Test: smoke alarm in the kitchen",()->simulate("smoke","on","Kitchen"));
        button("Test: carbon monoxide alarm in the hallway",()->simulate("carbon_monoxide","on","Hallway"));
        button("Test: a smoke detector goes offline",()->simulate("smoke","unavailable","Landing"));
        button("Test: someone rings the doorbell",()->simulate("door",null,null));
        button("Test: a person at the door (toast only)",()->simulate("person",null,null));
        button("End the alarm test",()->simulate("end",null,null));
        // SET-003: Frame-only actions appear only in the Frame room; nothing is offered that can't work on this TV.
        if(room.equals("frame"))button("Request native Frame Art Mode",()->action(this,"art"));
        button("Forget household setup",()->{stopService(new Intent(this,HouseholdService.class));new Vault(this).forget("household");found.clear();show();});
        button("Back",this::finish);
    }

    private void keep(String key,android.widget.Button b){if(key.equals(focusKey))b.requestFocus();}
    /** Xbox belongs to the Sony room, TV Plus, Art Mode and the doorbell input scripts to the Frame room (DockPolicy). */
    private boolean visible(String key){
        String room=room();
        if(key.equals("xbox"))return room.equals("sony");
        if(key.equals("tvplus")||key.equals("art")||key.equals("ringEnter")||key.equals("ringExit"))return room.equals("frame");
        return true;
    }
    /** The choice in plain words: its friendly name, never the entity ID. */
    private String current(JSONObject config,String key){
        String id=config.optString(key);if(id.isEmpty())return "none chosen";
        if(snapshot!=null&&snapshot.entities!=null)for(HaEntities.Entity e:snapshot.entities)if(e.id.equals(id))return e.name+(e.responding()?"":" (not responding)");
        JSONObject names=config.optJSONObject("names");String name=names==null?"":names.optString(key);
        return name.isEmpty()?HaEntities.pretty(id.substring(id.indexOf('.')+1)):name;
    }

    private void chooseRoom(){
        String[][] rooms=HaEntities.ROOMS;String[] labels=new String[rooms.length];for(int i=0;i<rooms.length;i++)labels[i]=rooms[i][1];
        String now=room();int checked=0;for(int i=0;i<rooms.length;i++)if(rooms[i][0].equals(now))checked=i;
        new AlertDialog.Builder(this).setTitle("Which TV is this?").setSingleChoiceItems(labels,checked,(d,which)->{
            d.dismiss();focusKey="room";getSharedPreferences("unified-tv-preview",0).edit().putString("room",rooms[which][0]).apply();
            note("Saved locally. Restart Home to update room shortcuts.");show();
        }).setNegativeButton("Cancel",null).show();
    }

    /** A remote-friendly list: clear matches, a divider, other entities, None, then the advanced choices. */
    private void choose(String key){
        JSONObject config=config();String label=label(key);
        HaEntities.Choices choices=snapshot==null||snapshot.entities==null?new HaEntities.Choices(new ArrayList<>(),new ArrayList<>(),""):
            HaEntities.choices(key,snapshot.entities,config.optString("door"));
        List<CharSequence> rows=new ArrayList<>();List<HaEntities.Candidate> picks=new ArrayList<>();List<Boolean> enabled=new ArrayList<>();
        for(HaEntities.Candidate c:choices.strong){rows.add(row(c));picks.add(c);enabled.add(true);}
        if(!choices.other.isEmpty()){rows.add("--- "+choices.otherHeading+" ---");picks.add(null);enabled.add(false);
            for(HaEntities.Candidate c:choices.other){rows.add(row(c));picks.add(c);enabled.add(true);}}
        if(choices.isEmpty()&&snapshot!=null&&snapshot.entities!=null){rows.add("Nothing suitable found in Home Assistant");picks.add(null);enabled.add(false);}
        int none=rows.size();rows.add("None");picks.add(null);enabled.add(true);
        int typed=rows.size();rows.add("Type it instead (advanced)");picks.add(null);enabled.add(true);
        int names=rows.size();rows.add(technical?"Hide technical names (advanced)":"Show technical names (advanced)");picks.add(null);enabled.add(true);
        ArrayAdapter<CharSequence> adapter=new ArrayAdapter<CharSequence>(this,android.R.layout.simple_list_item_1,rows){
            @Override public boolean areAllItemsEnabled(){return false;}
            @Override public boolean isEnabled(int position){return enabled.get(position);}
        };
        new AlertDialog.Builder(this).setTitle(label).setAdapter(adapter,(d,which)->{
            if(which==none){pick(key,null,null,false);return;}
            if(which==typed){typeIt(key);return;}
            if(which==names){technical=!technical;choose(key);return;}
            HaEntities.Candidate chosen=picks.get(which);if(chosen==null)return;
            pick(key,chosen.id,chosen.name,!choices.strong.contains(chosen));
        }).setNegativeButton("Cancel",null).show();
    }
    private CharSequence row(HaEntities.Candidate c){
        String title=c.name+(c.responding?"":" (not responding)");if(!technical)return title;
        SpannableStringBuilder s=new SpannableStringBuilder(title).append("\n");int start=s.length();s.append(c.id);
        s.setSpan(new RelativeSizeSpan(0.7f),start,s.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return s;
    }
    private void pick(String key,String id,String name,boolean below){
        focusKey=key;
        try{JSONObject config=config();put(config,key,id==null?"":id,name==null?"":name);new Vault(this).save("household",config);}
        catch(Exception e){note("Couldn't save on this TV. Nothing was changed.");return;}
        found.clear();
        if(id==null)note(label(key)+": none");
        else if(key.equals("person")&&below)note("Saved. Motion isn't person detection: the camera may also appear for cars, pets and passers-by.");
        else note("Saved: "+name);
        show();
    }
    /** Advanced only: the previous screen's typed entry and its exact validation. */
    private void typeIt(String key){
        EditText field=new EditText(this);field.setSingleLine(true);field.setText(config().optString(key));field.setSelectAllOnFocus(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);field.setHint(HaEntities.domain(key)+".");
        new AlertDialog.Builder(this).setTitle(label(key)+" (advanced)").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String v=field.getText().toString().trim();
            if(!HaEntities.valid(key,v)){note("That should start with "+HaEntities.domain(key)+". and use only lower-case letters, numbers and _. Nothing was changed.");return;}
            pick(key,v.isEmpty()?null:v,v.isEmpty()?null:HaEntities.pretty(v.substring(v.indexOf('.')+1)),false);
        }).show();
    }
    private void saveFlag(String key,boolean on){
        try{JSONObject config=config();config.put(key,on);new Vault(this).save("household",config);
            if(key.equals("alarms"))getSharedPreferences("unified-tv-preview",0).edit().putBoolean(AlarmWatch.SHOWN,on).apply();   // Home's notice reads this
        }catch(Exception e){note("Couldn't save on this TV. Nothing was changed.");}
    }
    /** The household record keeps the ID HouseholdService reads, its friendly name for display, and which roles were decided. */
    private static void put(JSONObject config,String key,String id,String name)throws Exception{
        config.put(key,id);JSONObject names=config.optJSONObject("names");if(names==null)names=new JSONObject();names.put(key,name);config.put("names",names);
        JSONArray decided=config.optJSONArray("decided");if(decided==null)decided=new JSONArray();if(!contains(decided,key))decided.put(key);config.put("decided",decided);
    }
    private static boolean contains(JSONArray list,String key){if(list!=null)for(int i=0;i<list.length();i++)if(key.equals(list.optString(i)))return true;return false;}
    private static String label(String key){for(String[] l:LABELS)if(l[0].equals(key))return l[1];return key;}

    private void simulate(String kind,String state,String name){
        if(!Settings.canDrawOverlays(this)){note("Grant overlay permission first, so the alarm screen can appear over other apps.");return;}
        startForegroundService(new Intent(this,HouseholdService.class).setAction("simulate").putExtra("kind",kind).putExtra("state",state).putExtra("name",name));
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
