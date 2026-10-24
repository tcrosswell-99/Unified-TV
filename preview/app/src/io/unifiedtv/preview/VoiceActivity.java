package io.unifiedtv.preview;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;
import java.util.concurrent.*;

/** Explicit request only. No microphone listener or bundled cloud API key. */
public final class VoiceActivity extends Screen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText query;private TextView answer;private boolean busy;
    @Override public void onCreate(Bundle b){super.onCreate(b);page("Ask your TV");
        text("Open an app, show the camera, or ask your configured household assistant. Speech recognition uses the TV's installed provider and may use its cloud service.",20);
        query=field("Your request","",false);answer=text("Ready",22);
        button("Use the TV microphone",()->{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-GB").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);try{startActivityForResult(i,9);}catch(Exception e){answer.setText("Voice input unavailable. Type your request with the remote keyboard.");}});
        button("Ask / Open",this::ask);button("Back",this::finish);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==9&&result==RESULT_OK&&data!=null){java.util.ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words!=null&&!words.isEmpty())query.setText(words.get(0));}}
    private void ask(){
        if(busy)return;String text=query.getText().toString().trim();if(text.isEmpty())return;
        String command=text.toLowerCase(Locale.UK);
        if(command.contains("live tv")||command.contains("reamsmart")){answer.setText("Live TV stays blocked until VPN traffic protection is verified.");return;}
        if(command.equals("switch profile")){SettingsActivity.chooseProfile(this);return;}
        if(command.equals("show camera")||command.equals("show ring camera")){startService(new Intent(this,HouseholdService.class).setAction("camera"));answer.setText("Camera requested. Household alerts must be configured with overlay permission.");return;}
        String pkg=command.equals("open bbc")?"bbc.iplayer.android.tv":command.equals("open netflix")?"com.netflix.ninja":command.equals("open disney")||command.equals("open disney plus")?"com.disney.disneyplus":"";
        if(!pkg.isEmpty()){
            Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch!=null){try{startActivity(launch);}catch(Exception e){answer.setText("App unavailable");}}else answer.setText("That app is not installed under its expected TV identity. Use Apps to find it.");return;
        }
        busy=true;answer.setText("Asking your household assistant…");
        worker.execute(()->{String response;
            try{JSONObject house=new Vault(this).read("household");if(!house.optBoolean("assist"))throw new IllegalStateException();
                JSONObject config=new Vault(this).read("ha");JSONObject result=LocalApi.postJson(config,"/api/conversation/process",new JSONObject().put("text",text).put("language","en-GB"));
                response=result.getJSONObject("response").getJSONObject("speech").getJSONObject("plain").getString("speech");
            }catch(Exception e){response="Assistant unavailable. Enable household Assist and check its connection. OpenAI realtime discovery needs a separate trusted relay and is not connected.";}
            final String reply=response;runOnUiThread(()->{busy=false;if(!isDestroyed()&&!isFinishing())answer.setText(reply);});
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
