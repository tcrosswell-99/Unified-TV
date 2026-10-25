package io.unifiedtv.preview;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
public final class PerformanceActivity extends Screen {
    static final String REPORT_FILE="home-measurement.txt";
    @Override public void onCreate(Bundle state){super.onCreate(state);page("TV Performance & Cleanup");
        text("Read-only baseline · no TV packages will be changed",22);
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        text("Android "+Build.VERSION.RELEASE+" · "+Build.MANUFACTURER+" "+Build.MODEL,20);
        text("Available memory: "+memory.availMem/1048576+" MB of "+memory.totalMem/1048576+" MB",20);
        text("Available app storage: "+getFilesDir().getUsableSpace()/1048576+" MB",20);
        text("Launcher input-to-draw sample: "+InputStats.report(),20);
        button(Measure.running?"Measuring Home…":"Measure Home (10 seconds)",this::measure);
        text("Measure Home opens Home and records for 10 seconds while you move left and right along the dock: frame pacing, focus response and memory. The results come back here and stay on this TV.",18);
        if(Measure.result!=null){text(Measure.result,17);if(Measure.savedTo!=null)text("Also saved on the TV as "+Measure.savedTo,16);}
        if(RemoteCheckActivity.allowed(this))button("Check your remote's buttons",()->startActivity(new Intent(this,RemoteCheckActivity.class)));
        text("These are local, partial measurements. They do not measure another app's decoder, HDMI input delay or network performance. Run the same owner test on each TV before comparing.",20);
        text("Cleanup is unavailable in this preview. Any later cleanup needs exact model/firmware rules, a proposed change list, separate owner approval and independent rollback. Native Tizen and the Frame's Android streamer are separate targets.",20);
        button("Refresh baseline",this::recreate);button("Back",this::finish);
    }
    private void measure(){
        if(Measure.running){note("Already measuring. Move along the dock on Home.");return;}
        Measure.start(getApplicationContext(),header(this));
        note("Measuring Home for 10 seconds. Move left and right along the dock now.");
        // Home is singleTask: this brings it to the front; the results reopen this screen when the run ends.
        startActivity(new Intent(this,UnifiedPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }
    /** App and TV lines for owner reports: model, Android version and build only; never serials, addresses or accounts. */
    static String header(Context c){
        String version="";try{version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception ignored){}
        return "Unified TV "+version+" ("+c.getPackageName()+")\nTV: "+Build.MANUFACTURER+" "+Build.MODEL+", Android "+Build.VERSION.RELEASE+", build "+Build.DISPLAY;
    }
    /** Writes a report into this app's own folder on the TV (readable with adb shell cat); null if it can't. Nothing is sent anywhere. */
    static String saveReport(Context c,String name,String text){
        File dir=c.getExternalFilesDir(null);if(dir==null)dir=c.getFilesDir();
        File file=new File(dir,name);
        try(FileOutputStream out=new FileOutputStream(file)){out.write(text.getBytes(StandardCharsets.UTF_8));return file.getAbsolutePath();}catch(Exception e){return null;}
    }
    /** One 10-second run on the main thread's Choreographer while Home is in front. */
    static final class Measure implements Choreographer.FrameCallback{
        static final long SECONDS=10;
        static boolean running;static String result,savedTo;
        private final Context app;private final String header;private final FrameStats frames=new FrameStats();
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final int inputMark=InputStats.total();private final long pssBefore=Debug.getPss();private long startNanos=-1;private boolean done;
        private Measure(Context app,String header){this.app=app;this.header=header;}
        static void start(Context app,String header){
            running=true;Measure m=new Measure(app,header);
            Choreographer.getInstance().postFrameCallback(m);
            m.handler.postDelayed(m::stop,SECONDS*1000+1500);   // safety net if frames stop arriving
        }
        @Override public void doFrame(long frameTimeNanos){
            if(done)return;
            if(startNanos<0)startNanos=frameTimeNanos;
            frames.frame(frameTimeNanos);
            if(frameTimeNanos-startNanos>=SECONDS*1_000_000_000L)stop();else Choreographer.getInstance().postFrameCallback(this);
        }
        private void stop(){
            if(done)return;done=true;running=false;handler.removeCallbacksAndMessages(null);Choreographer.getInstance().removeFrameCallback(this);
            Runtime rt=Runtime.getRuntime();
            result=FrameStats.report(header,frames.spanSeconds(),frames,InputStats.since(inputMark),pssBefore,Debug.getPss(),(rt.totalMemory()-rt.freeMemory())/1024,rt.maxMemory()/1024);
            savedTo=saveReport(app,REPORT_FILE,result);
            // Reopens the results over Home. If the owner has left for another app, Android may block this; the results
            // then wait on this screen for next time.
            try{app.startActivity(new Intent(app,PerformanceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException ignored){}
        }
    }
}
