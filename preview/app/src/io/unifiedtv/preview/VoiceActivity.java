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
        text("Open an app, find a title, show the camera or switch profile. Speech recognition uses the TV's installed provider and may use its cloud service.",20);
        query=field("Your request","",false);answer=text("Ready",22);
        button("Use the TV microphone",()->{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-GB").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);try{startActivityForResult(i,9);}catch(Exception e){answer.setText("Voice input unavailable. Type your request with the remote keyboard.");}});
        button("Ask / Open",this::ask);button("Back",this::finish);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==9&&result==RESULT_OK&&data!=null){java.util.ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words!=null&&!words.isEmpty())query.setText(words.get(0));}}
    private void ask(){
        if(busy)return;String text=query.getText().toString().trim();if(text.isEmpty())return;
        String command=VoiceCommand.classify(text);
        if(command.equals("blocked")){answer.setText("Live TV stays blocked until VPN traffic protection is verified.");return;}
        if(command.equals("profile")){SettingsActivity.chooseProfile(this);return;}
        if(command.equals("camera")){startService(new Intent(this,HouseholdService.class).setAction("camera"));answer.setText("Camera requested. Household alerts must be configured with overlay permission.");return;}
        if(command.equals("find")){startActivity(new Intent(this,LibraryActivity.class).putExtra("search",text.substring(5).trim()));return;}
        String pkg=command.equals("bbc")?"bbc.iplayer.android.tv":command.equals("netflix")?"com.netflix.ninja":command.equals("disney")?"com.disney.disneyplus":"";
        if(!pkg.isEmpty()){
            Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch!=null){try{startActivity(launch);}catch(Exception e){answer.setText("App unavailable");}}else answer.setText("That app is not installed under its expected TV identity. Use Apps to find it.");return;
        }
        answer.setText("Try Open BBC, Open Netflix, Show Camera, Switch Profile, or Find followed by a title. General voice discovery is not connected; it needs a scoped read-only relay before it can be enabled safely.");
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
