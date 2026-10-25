package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.JSONObject;
import java.net.URI;
import java.util.concurrent.*;

/**
 * Typing a media server's address and token: the advanced fallback when signing in with the phone (Quick Connect) isn't
 * possible, as the glass sheet over Home (OD-19). Home Assistant no longer uses this screen: it is set up on the phone in
 * Settings > Smart Home, with its own Advanced page there, so "ha" goes straight to that page.
 */
public final class ConnectionsActivity extends GlassScreen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String key; private EditText host,port,token,user; private CheckBox https,httpConsent; private TextView status;
    private boolean busy;
    @Override public void onCreate(Bundle b) {
        String asked=getIntent().getStringExtra("key");
        super.onCreate(b);
        if(asked==null){close();return;}
        if(asked.equals("ha")){startActivity(new Intent(this,SettingsActivity.class).putExtra("category",SettingsActivity.SMART_HOME).putExtra("task",SettingsActivity.HA_ADVANCED));close();return;}
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); key=asked; show();
    }
    private void show() {
        page("Your Films & TV",(key.endsWith("child")?"Kids":"Adults")+" · sign in by typing (advanced)");
        JSONObject old;
        try { old=new Vault(this).read(key); } catch(Exception e) { old=new JSONObject(); note("The saved sign-in can't be read. Forget it and sign in again."); }
        String hostname="",p="8096"; boolean tls=true;
        try { URI u=URI.create(old.getString("base")); hostname=u.getHost(); p=""+u.getPort(); tls=u.getScheme().equals("https"); } catch(Exception ignored) {}
        if(old.length()==0&&getIntent().hasExtra("discoveredBase"))try{URI discovered=URI.create(getIntent().getStringExtra("discoveredBase"));hostname=discovered.getHost();p=""+discovered.getPort();tls="https".equals(discovered.getScheme());}catch(Exception ignored){}
        String found=getIntent().getStringExtra("discoveredName");
        status=text(old.length()==0?"Not signed in yet.":"Signed in earlier on this TV.",19);
        if(found!=null&&!found.isEmpty())text(found+" was found on your home network.",15);
        card();
        host=field("Server name or address",hostname,false);
        port=field("Port",p,false); port.setInputType(InputType.TYPE_CLASS_NUMBER);
        https=check("Encrypted connection (HTTPS)",tls);
        httpConsent=check("Allow an unencrypted connection to this server on my home network",old.optBoolean("httpConsent"));
        token=field(key.endsWith("child")?"The Kids account's access token":"Your account's access token",old.optString("token"),true);
        user=field("Your user ID on the server",old.optString("user"),false);
        card();
        button("Check and save",this::checkAndSave);
        button("Forget this sign-in",()->new AlertDialog.Builder(this).setMessage("Forget this sign-in on this TV?").setNegativeButton("Cancel",null)
            .setPositiveButton("Forget",(d,w)->{new Vault(this).forget(key); show();}).show());
        text("Kept encrypted on this TV. Signing in with your phone from Library & Media is easier.",15);
    }
    private CheckBox check(String words,boolean on){CheckBox c=new CheckBox(this);c.setText(words);c.setTextColor(SettingsGlass.TEXT);c.setChecked(on);c.setFocusable(true);column.addView(c);return c;}
    private JSONObject draft() throws Exception {
        String base=Endpoint.base(host.getText().toString(),port.getText().toString(),https.isChecked());
        if(!https.isChecked() && !httpConsent.isChecked()) throw new IllegalArgumentException("This server uses an unencrypted connection. Tick \"Allow an unencrypted connection\" above to use it on your home network.");
        String t=token.getText().toString().trim(); if(t.contains("\n")||t.contains("\r")) throw new IllegalArgumentException("Check the token");
        return new JSONObject().put("base",base).put("enabled",true).put("kind","media")
            .put("token",t).put("user",user.getText().toString().trim()).put("httpConsent",httpConsent.isChecked());
    }
    /** One step: check with the server, and save only what worked. */
    private void checkAndSave() {
        if(busy)return;
        final JSONObject config;
        try { config=draft(); } catch(Exception e) { status.setText(e instanceof IllegalArgumentException?e.getMessage():"Check the details above."); return; }
        busy=true; status.setText("Checking…");
        worker.execute(()->{
            String result;boolean ok=false;
            try {
                LocalApi.get(config,"/System/Info/Public");
                if(config.optString("token").isEmpty()||config.optString("user").isEmpty()) result="The server answered. Add the access token and user ID to sign in.";
                else {
                    JSONObject account=LocalApi.get(config,"/Users/"+LocalApi.segment(config.optString("user")));
                    if(account.optString("Id").isEmpty()) throw new java.io.IOException();
                    JSONObject me=LocalApi.get(config,"/Users/Me");
                    if(!account.optString("Id").equals(me.optString("Id"))) result="That token belongs to someone else. Use this user's own access token.";
                    else if(key.endsWith("child") && me.optJSONObject("Policy")!=null && me.optJSONObject("Policy").optBoolean("IsAdministrator")) result="The Kids account must not be an administrator.";
                    else { new Vault(this).save(key,config); ok=true; result="Signed in and saved."; }
                }
            } catch(LocalApi.ApiError e) { result=e.code==401||e.code==403?"The server didn't accept that sign-in.":e.code>=300&&e.code<400?"The server sent this TV elsewhere. Enter the server's own address.":"Something answered, but it isn't a media server this TV can use."; }
            catch(Exception e) { result="The server couldn't be reached. Check the address, and that it's on."; }
            final String message=result;final boolean saved=ok; runOnUiThread(()->{busy=false; if(!isFinishing()&&!isDestroyed()){status.setText(message);if(saved)note(message);}});
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
