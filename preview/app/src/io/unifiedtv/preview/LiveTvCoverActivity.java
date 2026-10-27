package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * OD-26's notice when NordVPN drops while Live TV is on (LiveTvGuard): a full-screen, opaque glass page over Live TV,
 * "NordVPN disconnected — Live TV is covered. Reconnect NordVPN", with Reconnect (opens NordVPN, as Home's shield does)
 * and Back to Home. It covers the picture; it is not a kill switch (Android does not stop Live TV's connection, and Live
 * TV's sound may carry on behind it until Live TV pauses itself).
 *
 * <p>When NordVPN is connected again (back from NordVPN, say), it says so and offers Watch Live TV, which goes through the
 * same gate as every other route (Launch.open: the rule, then the Kids list). Back goes to Home, never back to Live TV
 * uncovered. Only its own buttons and Back are handled; no key is read anywhere else (module 03).
 */
public final class LiveTvCoverActivity extends Activity {
    static final String NOTICE="NordVPN disconnected — Live TV is covered. Reconnect NordVPN.";
    static final String AGAIN="NordVPN is connected again.";
    private TextView title,more;private Button primary;private String live="";

    static Intent intent(Context c,String live){
        return new Intent(c,LiveTvCoverActivity.class).putExtra(LiveTvGuard.LIVE,live==null?"":live).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        String pkg=getIntent().getStringExtra(LiveTvGuard.LIVE);live=pkg==null||pkg.isEmpty()?LiveTvProtection.livePackage(this):pkg;
        FrameLayout root=new FrameLayout(this);root.setBackground(MaterialScene.backdrop(this));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(56,40,56,40);panel.setBackground(MaterialScene.panel(this));
        title=new TextView(this);title.setTextColor(-1);title.setTextSize(30);panel.addView(title);
        more=new TextView(this);more.setTextColor(0xffe1e9f2);more.setTextSize(19);more.setPadding(0,14,0,24);panel.addView(more);
        LinearLayout actions=new LinearLayout(this);panel.addView(actions);
        primary=VpnGate.glassButton(this,"Reconnect");
        Button home=VpnGate.glassButton(this,"Back to Home");home.setOnClickListener(v->home());
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.setMargins(0,0,8,0);actions.addView(primary,size);
        LinearLayout.LayoutParams size2=new LinearLayout.LayoutParams(0,-2,1);size2.setMargins(8,0,0,0);actions.addView(home,size2);
        int w=Math.min(1100,getResources().getDisplayMetrics().widthPixels-80);
        root.addView(panel,new FrameLayout.LayoutParams(w,-2,Gravity.CENTER));
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        primary.requestFocus();
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);String pkg=i.getStringExtra(LiveTvGuard.LIVE);if(pkg!=null&&!pkg.isEmpty())live=pkg;}
    /** Every time it shows (and back from NordVPN): what the rule says now. */
    @Override protected void onResume(){super.onResume();refresh();}
    void refresh(){
        boolean back=LiveTvProtection.opens(LiveTvProtection.now(this));
        title.setText(back?AGAIN:NOTICE);
        more.setText(back?LiveTvProtection.COVERS+" "+LiveTvProtection.NOT_A_KILL_SWITCH:LiveTvProtection.NOT_A_KILL_SWITCH);
        if(back&&!live.isEmpty()){
            primary.setText("Watch Live TV");primary.setOnClickListener(v->{String why=Launch.open(this,live,"Live TV");if(why==null)finish();else if(!why.isEmpty())more.setText(why);});
        }else{primary.setText("Reconnect");primary.setOnClickListener(v->VpnGate.connect(this));}
        primary.requestFocus();
    }
    /** Unified TV's Home, never Live TV uncovered underneath. */
    private void home(){
        try{startActivity(new Intent(this,UnifiedPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}catch(RuntimeException ignored){}
        finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
    }
    @Override public void onBackPressed(){home();}
    /** For tests: what the notice says now. */
    String words(){return title.getText()+" "+more.getText();}
}
