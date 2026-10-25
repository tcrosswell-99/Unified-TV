package io.unifiedtv.preview;

import android.app.*;
import android.content.*;
import android.net.*;
import android.view.*;
import android.widget.*;

/** A tunnel signal is not proof of provider ownership, app routing or a kill switch. */
final class VpnGate {
    enum State {CONNECTED,DISCONNECTED,CHECKING,UNKNOWN}
    private static volatile long sampledAt;private static volatile State sampled=State.CHECKING;private static boolean checking;private static int generation;
    /** A network change voids any check already in flight: its answer describes the old network. */
    static synchronized void invalidate(){generation++;sampledAt=0;sampled=State.CHECKING;}
    static synchronized int begin(){checking=true;return generation;}
    static synchronized boolean commit(int gen,State state,long at){checking=false;if(gen!=generation)return false;sampled=state;sampledAt=at;return true;}
    static State current(){return sampled;}
    static State inspect(Context c){
        try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return State.UNKNOWN;
            for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return State.CONNECTED;}return State.DISCONNECTED;
        }catch(RuntimeException e){return State.UNKNOWN;}
    }
    static boolean tunnel(Context c){return inspect(c)==State.CONNECTED;}
    /** Whether this app's own default traffic uses the VPN. With split tunnelling it may not, and it says nothing about Live TV's app. */
    static boolean routed(Context c){
        try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=cm==null?null:cm.getActiveNetwork();NetworkCapabilities caps=n==null?null:cm.getNetworkCapabilities(n);return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);}
        catch(RuntimeException e){return false;}
    }
    static String statusLabel(State state){switch(state){case CONNECTED:return "VPN ● Connected";case DISCONNECTED:return "VPN ○ Disconnected";case CHECKING:return "VPN ◌ Checking";default:return "VPN ◇ Unknown";}}
    static synchronized String label(Context c){long now=android.os.SystemClock.elapsedRealtime();if(!checking&&(sampledAt==0||now-sampledAt>2000)){int gen=begin();Context app=c.getApplicationContext();new Thread(()->commit(gen,inspect(app),android.os.SystemClock.elapsedRealtime()),"vpn-status").start();}return statusLabel(sampled);}
    static ConnectivityManager.NetworkCallback watch(Activity a,Runnable changed){
        ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
            private void refresh(){a.runOnUiThread(()->{invalidate();changed.run();});}
            @Override public void onAvailable(Network n){refresh();}
            @Override public void onLost(Network n){refresh();}
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities caps){refresh();}
        };
        try{((ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE)).registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),callback);return callback;}
        catch(RuntimeException e){return null;}
    }
    static void unwatch(Context c,ConnectivityManager.NetworkCallback callback){if(callback!=null)try{((ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(callback);}catch(RuntimeException ignored){}}
    static String explanation(boolean tunnel,boolean routed){
        if(!tunnel)return "Connect NordVPN before watching Live TV. Protected playback is not yet enabled in this preview.";
        return routed?"NordVPN is on for this TV. Live TV stays locked until its protection has been checked on this TV."
            :"NordVPN is connected but isn't covering this app (it may be set to leave some apps out). Live TV stays locked.";
    }
    static void connect(Activity a){
        // Kids: NordVPN is another app, so it opens only if an adult approved it (Launch's one Kids check).
        String no=Launch.kidsRefusal(a,"com.nordvpn.android","NordVPN");if(no!=null){Toast.makeText(a,no,Toast.LENGTH_LONG).show();return;}
        android.content.pm.PackageManager pm=a.getPackageManager();Intent launch=pm.getLeanbackLaunchIntentForPackage("com.nordvpn.android");
        if(launch==null)launch=pm.getLaunchIntentForPackage("com.nordvpn.android");
        if(launch!=null){try{a.startActivity(launch);return;}catch(RuntimeException ignored){}}
        Toast.makeText(a,"NordVPN is not available on this TV",Toast.LENGTH_LONG).show();
    }
    static void show(Activity a){show(a,null);}
    /** The Live TV sheet; a reason from the protection decision replaces the connection explanation. */
    static void show(Activity a,String reason){
        Dialog dialog=new Dialog(a);LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(40,24,40,24);panel.setBackground(MaterialScene.panel(a));
        TextView title=new TextView(a);title.setText("Live TV");title.setTextColor(-1);title.setTextSize(24);panel.addView(title);
        TextView state=new TextView(a);state.setText(reason!=null&&!reason.isEmpty()?reason:explanation(tunnel(a),routed(a)));state.setTextColor(0xffe1e9f2);state.setTextSize(18);state.setPadding(0,12,0,18);panel.addView(state);
        LinearLayout actions=new LinearLayout(a);panel.addView(actions);
        String[] names={"Connect","Retry","Cancel"};
        for(int i=0;i<names.length;i++){final int action=i;Button b=new Button(a);b.setAllCaps(false);b.setText(names[i]);b.setTextSize(18);
            android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();
            android.graphics.drawable.GradientDrawable focus=new android.graphics.drawable.GradientDrawable();focus.setColor(0xffdce8f6);focus.setCornerRadius(16);
            android.graphics.drawable.GradientDrawable rest=new android.graphics.drawable.GradientDrawable();rest.setColor(0x70445a74);rest.setCornerRadius(16);rest.setStroke(1,0x55cee0f5);
            bg.addState(new int[]{android.R.attr.state_focused},focus);bg.addState(new int[]{},rest);b.setBackground(bg);
            b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{0xff101827,-1}));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.setMargins(4,0,4,0);actions.addView(b,size);b.setOnClickListener(v->{if(action==0){dialog.dismiss();connect(a);}else if(action==1)state.setText(statusLabel(inspect(a))+" · "+explanation(tunnel(a),routed(a)));else dialog.dismiss();});}
        dialog.setContentView(panel);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=w.getAttributes();p.dimAmount=.12f;w.setAttributes(p);w.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);}
        dialog.show();if(w!=null){w.setLayout(Math.min(1100,a.getResources().getDisplayMetrics().widthPixels-80),-2);WindowManager.LayoutParams p=w.getAttributes();p.y=36;w.setAttributes(p);w.getDecorView().setSystemUiVisibility(5894);}actions.getChildAt(0).requestFocus();
    }
}
