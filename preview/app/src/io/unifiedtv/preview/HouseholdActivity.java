package io.unifiedtv.preview;

import android.os.Bundle;
import android.widget.Toast;
import org.json.JSONObject;

/**
 * Household setup now lives inside the Settings glass sheet (HouseholdPage, task "household"; OD-19): this entry only
 * hands over to it. The room actions other screens run (Xbox, and the Frame's scripts for older records) stay here.
 */
public final class HouseholdActivity extends android.app.Activity {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        android.content.Intent screen=screen();
        if(screen==null)screen=new android.content.Intent(this,SettingsActivity.class).putExtra("category",SettingsActivity.NOTIFICATIONS).putExtra("task",SettingsActivity.HOUSEHOLD);
        // HRC-03: Home underneath only when Unified TV wasn't open; otherwise on top of what is (Launch.route).
        android.content.Intent[] go=Launch.route(isTaskRoot(),this,screen);
        try{if(go.length>1)startActivities(go);else startActivity(go[0]);}catch(RuntimeException ignored){}
        finish();overridePendingTransition(0,0);
    }
    /** A notification's screen (Launch.viaHandOver): only one of this app's own screens is ever opened from here. */
    private android.content.Intent screen(){
        android.content.Intent s;
        try{s=android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.TIRAMISU?getIntent().getParcelableExtra(Launch.SCREEN,android.content.Intent.class):legacyScreen();}catch(RuntimeException e){return null;}
        return s!=null&&s.getComponent()!=null&&getPackageName().equals(s.getComponent().getPackageName())?s:null;
    }
    @SuppressWarnings("deprecation") private android.content.Intent legacyScreen(){return getIntent().getParcelableExtra(Launch.SCREEN);}
    static void action(android.app.Activity activity,String name){
        java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();executor.execute(()->{
            boolean ok=false;
            try{JSONObject config=new Vault(activity).read("ha"),house=new Vault(activity).read("household");String entity=house.optString(name);
                if(!entity.matches("script\\.[a-z0-9_]+"))throw new IllegalArgumentException();
                LocalApi.post(config,"/api/services/script/turn_on",new JSONObject().put("entity_id",entity));ok=true;
            }catch(Exception ignored){}
            final boolean sent=ok;activity.runOnUiThread(()->Toast.makeText(activity,sent?"Asked Home Assistant. Check the TV responds.":"That isn't set up yet. An adult can choose it in Settings > Smart Home.",Toast.LENGTH_LONG).show());executor.shutdown();
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
