package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.JSONObject;
import java.net.URI;
import java.util.concurrent.*;

public final class ConnectionsActivity extends Screen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String key; private EditText host,port,token,user; private CheckBox enabled,https,httpConsent; private TextView status;
    private boolean busy;
    @Override public void onCreate(Bundle b) { super.onCreate(b); getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); key=getIntent().getStringExtra("key"); if(key==null) { finish(); return; } show(); }
    private void show() {
        boolean ha=key.equals("ha"); page(ha?"Home Assistant":"Your Films & TV · "+(key.endsWith("child")?"Child":"Adult"));
        JSONObject old;
        try { old=new Vault(this).read(key); } catch(Exception e) { old=new JSONObject(); note("Stored connection cannot be unlocked. Forget and configure it again."); }
        enabled=new CheckBox(this); enabled.setText("Enable connection"); enabled.setTextColor(-1); enabled.setChecked(old.optBoolean("enabled")); column.addView(enabled);
        String hostname="",p=ha?"8123":"8096"; boolean tls=true;
        try { URI u=URI.create(old.getString("base")); hostname=u.getHost(); p=""+u.getPort(); tls=u.getScheme().equals("https"); } catch(Exception ignored) {}
        host=field("Hostname or IP (IPv4, IPv6 or local hostname)",hostname,false);
        port=field("Port",p,false); port.setInputType(InputType.TYPE_CLASS_NUMBER);
        https=new CheckBox(this); https.setText("Use HTTPS"); https.setTextColor(-1); https.setChecked(tls); column.addView(https);
        httpConsent=new CheckBox(this); httpConsent.setText("I trust this LAN server and allow unencrypted HTTP to this host only"); httpConsent.setTextColor(-1); httpConsent.setChecked(old.optBoolean("httpConsent")); column.addView(httpConsent);
        token=field(ha?"Long-lived access token":"User access token (not a server-wide API key for Child)",old.optString("token"),true);
        user=ha?null:field("Server user ID",old.optString("user"),false);
        text("Credentials stay encrypted on this TV. Test does not save changes. Reachability does not verify playback or automations.",18);
        status=text(old.length()==0?"Not configured":"Saved · not tested this session",22);
        button("Test connection",this::test);
        button("Save",()->{
            try { JSONObject draft=draft(); new Vault(this).save(key,draft); status.setText("Saved · test to verify authorisation"); }
            catch(IllegalArgumentException e) { status.setText(e.getMessage()); }
            catch(Exception e) { status.setText("Unable to save securely. Nothing was saved."); }
        });
        button("Forget connection",()->new AlertDialog.Builder(this).setMessage("Forget this connection and its credentials on this TV?").setNegativeButton("Cancel",null)
            .setPositiveButton("Forget",(d,w)->{new Vault(this).forget(key); show();}).show());
        button("Back",this::finish);
    }
    private JSONObject draft() throws Exception {
        if(!enabled.isChecked()) return new JSONObject().put("enabled",false);
        String base=Endpoint.base(host.getText().toString(),port.getText().toString(),https.isChecked());
        if(!https.isChecked() && !httpConsent.isChecked()) throw new IllegalArgumentException("Allow trusted-LAN HTTP explicitly, or use HTTPS");
        String t=token.getText().toString().trim(); if(t.contains("\n")||t.contains("\r")) throw new IllegalArgumentException("Check the token");
        return new JSONObject().put("base",base).put("enabled",true).put("kind",key.equals("ha")?"ha":"media")
            .put("token",t).put("user",user==null?"":user.getText().toString().trim()).put("httpConsent",httpConsent.isChecked());
    }
    private void test() {
        if(busy)return;
        final JSONObject config;
        try { config=draft(); } catch(Exception e) { status.setText(e instanceof IllegalArgumentException?e.getMessage():"Check connection fields"); return; }
        if(!config.optBoolean("enabled")){status.setText("Not configured · disabled");return;}
        busy=true; status.setText("Testing…");
        worker.execute(()->{
            String result;
            try {
                if(key.equals("ha")) { LocalApi.get(config,"/api/"); result=config.optString("token").isEmpty()?"Reachable · authentication required":"Authorised · automation capabilities not tested"; }
                else {
                    LocalApi.get(config,"/System/Info/Public");
                    if(config.optString("token").isEmpty()||config.optString("user").isEmpty()) result="Reachable · authentication required";
                    else {
                        JSONObject account=LocalApi.get(config,"/Users/"+LocalApi.segment(config.optString("user")));
                        if(account.optString("Id").isEmpty()) throw new java.io.IOException();
                        JSONObject me=LocalApi.get(config,"/Users/Me");
                        if(!account.optString("Id").equals(me.optString("Id"))) result="Authentication required · use this user's own access token";
                        else if(key.endsWith("child") && me.optJSONObject("Policy")!=null && me.optJSONObject("Policy").optBoolean("IsAdministrator")) result="Child account must not be an administrator";
                        else result="Authorised · media playback not tested";
                    }
                }
            } catch(LocalApi.ApiError e) { result=e.code==401||e.code==403?"Authentication required":e.code>=300&&e.code<400?"Redirect refused · enter the final server host":"Reachable · incompatible service or path"; }
            catch(Exception e) { result="Offline · check host, port, certificate and connection"; }
            final String message=result; runOnUiThread(()->{busy=false; if(!isFinishing()&&!isDestroyed())status.setText(message);});
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
