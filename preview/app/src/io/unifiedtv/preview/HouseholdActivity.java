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
        startActivity(new android.content.Intent(this,SettingsActivity.class).putExtra("category",SettingsActivity.NOTIFICATIONS).putExtra("task",SettingsActivity.HOUSEHOLD));
        finish();overridePendingTransition(0,0);
    }
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
