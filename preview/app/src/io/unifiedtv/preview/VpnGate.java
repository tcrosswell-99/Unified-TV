package io.unifiedtv.preview;

import android.app.*;
import android.content.*;
import android.net.*;
import android.view.*;
import android.widget.*;

/**
 * What Android reports about a VPN, for Home's shield and the Live TV sheet. A VPN being up is not proof of whose it is,
 * of any app's routing, or of a kill switch: whether Live TV opens is LiveTvProtection's one rule (OD-26).
 */
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
    /**
     * The shield's words, sampled off the main thread at most every two seconds: one check at a time on one shared worker
     * (never a thread per redraw), and a changed answer asks Home to redraw the pill (listen), so it never stays on
     * "Checking" after the answer has arrived.
     */
    static synchronized String label(Context c){long now=android.os.SystemClock.elapsedRealtime();
        if(!checking&&(sampledAt==0||now-sampledAt>2000)){int gen=begin();Context app=c.getApplicationContext();
            try{WORKER.execute(()->{State before=sampled;boolean kept=commit(gen,inspect(app),android.os.SystemClock.elapsedRealtime());Runnable l=listener;if(kept&&before!=sampled&&l!=null)l.run();});}
            catch(RuntimeException e){synchronized(VpnGate.class){checking=false;}}}
        return statusLabel(sampled);}
    private static final java.util.concurrent.ExecutorService WORKER=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"vpn-status");t.setDaemon(true);return t;});
    /** Home's redraw while it is on screen (null otherwise); called from the worker, so it must be thread-safe (postInvalidate). */
    static volatile Runnable listener;
    static void listen(Runnable redraw){listener=redraw;}
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
    /**
     * Opens NordVPN at its own main screen (its normal TV entry, never a settings screen it was left on), as a short visit
     * so Back comes back to Unified TV where Android allows (Launch.visit). Kids: NordVPN is another app, so it opens only
     * if an adult approved it (Launch's one Kids check, inside visit). The shield's state still follows Android's own
     * report through watch(), whatever happens in NordVPN.
     */
    static boolean connect(Activity a){
        String why=a.getPackageManager().getLeanbackLaunchIntentForPackage(LiveTvProtection.NORDVPN)==null&&a.getPackageManager().getLaunchIntentForPackage(LiveTvProtection.NORDVPN)==null
            ?"NordVPN is not available on this TV":Launch.visit(a,LiveTvProtection.NORDVPN,"NordVPN");
        if(why!=null&&!why.isEmpty())Toast.makeText(a,why,Toast.LENGTH_LONG).show();
        return why==null;
    }
    static final String TRY_AGAIN="Try again",DEBUG_ACCESS="Turn on access using the TV's debugging prompt",ALLOW_ACCESS="Allow access";
    static void show(Activity a){show(a,null,null);}
    /**
     * The Live TV sheet: what the one rule (LiveTvProtection, OD-26) says now, in plain words, and never a dead end (owner's
     * TCL, build run 99). When only notification access would let Unified TV tell whose VPN is up: "Turn on access using the
     * TV's debugging prompt" when the TV has no notification-access screen, else "Allow access"; otherwise Connect (opens
     * NordVPN). Then Try again and Cancel. {@code livePkg}/{@code label}: the Live TV launch that was refused (null from
     * Settings); Try again runs it once the rule opens, and after Connect or access Unified TV carries on into Live TV by
     * itself (NordConnect, Comeback). A waiting step keeps only those two strings, never a screen.
     */
    static void show(Activity a,String livePkg,String label){
        Runnable retry=livePkg==null?null:()->NordConnect.open(a,livePkg,label);
        Comeback.Step carryOn=on->{NordConnect.open(on,livePkg,label);return true;};
        LiveTvProtection.State now=LiveTvProtection.now(a);
        java.util.List<String> names=new java.util.ArrayList<>();java.util.List<Runnable> actions=new java.util.ArrayList<>();
        Dialog[] dialog=new Dialog[1];TextView[] state=new TextView[1];
        if(now==LiveTvProtection.State.UNSURE){
            android.content.Intent screen=MediaWatch.accessScreen(a);
            if(screen==null){if(NotificationAccessActivity.available(a)){names.add(DEBUG_ACCESS);actions.add(()->{dialog[0].dismiss();if(livePkg!=null)Comeback.arm(a,NotificationAccessActivity.WINDOW_MS,carryOn);NotificationAccessActivity.open(a);});}}
            else{names.add(ALLOW_ACCESS);actions.add(()->{dialog[0].dismiss();if(livePkg!=null)Comeback.arm(a,NotificationAccessActivity.WINDOW_MS,carryOn);Launch.system(a,screen,"This TV doesn't offer notification access");});}
        }else{names.add("Connect");actions.add(()->{dialog[0].dismiss();if(connect(a)&&livePkg!=null)NordConnect.after(a,livePkg,label);});}
        names.add(TRY_AGAIN);actions.add(()->{invalidate();LiveTvProtection.State again=LiveTvProtection.now(a);
            if(LiveTvProtection.opens(again)&&retry!=null){dialog[0].dismiss();retry.run();}
            else state[0].setText(statusLabel(inspect(a))+" · "+LiveTvProtection.message(again));});
        names.add("Cancel");actions.add(()->dialog[0].dismiss());
        dialog[0]=sheet(a,"Live TV",LiveTvProtection.message(now),names,actions,state);
    }
    /**
     * A small glass sheet at the bottom of the screen (the Live TV sheet, AirPlay's panel): a title, plain words and its
     * buttons, the first focused. Buttons sit in a row, or one under another when a name is long. {@code words} gets the
     * text view, so a button can change what it says.
     */
    static Dialog sheet(Activity a,String heading,String text,java.util.List<String> names,java.util.List<Runnable> actions,TextView[] words){
        Dialog dialog=new Dialog(a);LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(40,24,40,24);panel.setBackground(MaterialScene.panel(a));
        TextView title=new TextView(a);title.setText(heading);title.setTextColor(-1);title.setTextSize(24);panel.addView(title);
        TextView state=new TextView(a);state.setText(text);state.setTextColor(0xffe1e9f2);state.setTextSize(18);state.setPadding(0,12,0,18);panel.addView(state);
        if(words!=null&&words.length>0)words[0]=state;
        boolean column=false;for(String n:names)if(n.length()>18)column=true;
        LinearLayout actionRow=new LinearLayout(a);actionRow.setOrientation(column?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);panel.addView(actionRow);
        for(int i=0;i<names.size();i++){Runnable act=actions.get(i);Button b=glassButton(a,names.get(i));
            LinearLayout.LayoutParams size=column?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1);size.setMargins(4,column?4:0,4,column?4:0);actionRow.addView(b,size);
            b.setOnClickListener(v->act.run());}
        dialog.setContentView(panel);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=w.getAttributes();p.dimAmount=.12f;w.setAttributes(p);w.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);}
        dialog.show();if(w!=null){w.setLayout(Math.min(1100,a.getResources().getDisplayMetrics().widthPixels-80),-2);WindowManager.LayoutParams p=w.getAttributes();p.y=36;w.setAttributes(p);w.getDecorView().setSystemUiVisibility(5894);}
        if(actionRow.getChildCount()>0)actionRow.getChildAt(0).requestFocus();
        return dialog;
    }
    /** The sheet's glass button: pale when focused, a frosted chip otherwise (also the Live TV cover's). */
    static Button glassButton(android.content.Context a,String text){
        Button b=new Button(a);b.setAllCaps(false);b.setText(text);b.setTextSize(18);
        android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable focus=new android.graphics.drawable.GradientDrawable();focus.setColor(0xffdce8f6);focus.setCornerRadius(16);
        android.graphics.drawable.GradientDrawable rest=new android.graphics.drawable.GradientDrawable();rest.setColor(0x70445a74);rest.setCornerRadius(16);rest.setStroke(1,0x55cee0f5);
        bg.addState(new int[]{android.R.attr.state_focused},focus);bg.addState(new int[]{},rest);b.setBackground(bg);
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{0xff101827,-1}));
        return b;
    }
}
