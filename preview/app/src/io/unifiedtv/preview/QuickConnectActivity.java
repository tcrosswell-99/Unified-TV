package io.unifiedtv.preview;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.concurrent.*;

/** Sign in with your phone: a short code on the TV, approved in the Jellyfin app. Typing stays an advanced option. The glass sheet over Home (OD-11). */
public final class QuickConnectActivity extends GlassScreen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler timer=new Handler(Looper.getMainLooper());
    private String key,base,name;private JSONObject pending;private QuickConnect.Start start;private TextView code,state;private long until;private boolean active;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);active=true;
        key=getIntent().getStringExtra("key");base=getIntent().getStringExtra("base");name=getIntent().getStringExtra("name");if(key==null||base==null){finish();return;}
        page(name==null||name.isEmpty()?"Your Films & TV":name);
        code=text("",64);code.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,textPx(64));code.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD));code.setLetterSpacing(.25f);
        state=text("One moment...",21);
        button("Sign in another way (advanced)",()->{startActivity(new Intent(this,ConnectionsActivity.class).putExtra("key",key).putExtra("discoveredBase",base).putExtra("discoveredName",name));finish();});
        button("Cancel",this::finish);
        try{pending=QuickConnect.pending(base,QuickConnect.device(this));}catch(Exception e){finish();return;}
        // Spec 27.4: trusted-HTTP consent is never implied. An unencrypted home server needs an explicit Continue first.
        if(base.startsWith("http:")){state.setText("This server is on your home network and doesn't use encryption. Continue only if you trust your home network.");Button go=button("Continue",()->{});go.setOnClickListener(v->{go.setVisibility(android.view.View.GONE);ask();});go.requestFocus();return;}
        ask();
    }
    private void ask(){
        state.setText("One moment...");
        worker.execute(()->{try{QuickConnect.Start s=QuickConnect.initiate(pending);runOnUiThread(()->{if(!active)return;start=s;code.setText(s.code);
                state.setText("On your phone, open the Jellyfin app, go to Quick Connect and enter this code.");until=SystemClock.elapsedRealtime()+5*60_000;poll();});}
            catch(Exception e){runOnUiThread(()->{if(active)state.setText("Signing in with your phone isn't switched on for this server. Ask whoever looks after it to turn on Quick Connect, or sign in another way.");});}});
    }
    private void poll(){
        if(!active)return;if(SystemClock.elapsedRealtime()>until){state.setText("The code has expired.");code.setText("");button("Get a new code",this::recreate);return;}
        timer.postDelayed(()->worker.execute(()->{
            try{if(!QuickConnect.approved(pending,start.secret)){runOnUiThread(this::poll);return;}
                JSONObject account=QuickConnect.account(pending,QuickConnect.authenticate(pending,start.secret),key.endsWith("child"));new Vault(this).save(key,account);
                runOnUiThread(()->{if(!active)return;code.setText("");state.setText(account.optString("name").isEmpty()?"You're signed in.":"You're signed in as "+account.optString("name")+".");button("Done",this::finish).requestFocus();});}
            catch(SecurityException e){runOnUiThread(()->{if(active){code.setText("");state.setText("That's an administrator account. Kids needs a restricted account: approve the code from a child's account instead.");}});}
            catch(Exception e){runOnUiThread(this::poll);}
        }),3000);
    }
    @Override protected void onDestroy(){active=false;timer.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
