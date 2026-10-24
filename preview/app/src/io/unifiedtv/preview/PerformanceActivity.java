package io.unifiedtv.preview;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Build;
public final class PerformanceActivity extends Screen {
    @Override public void onCreate(Bundle state){super.onCreate(state);page("TV Performance & Cleanup");
        text("Read-only baseline · no TV packages will be changed",22);
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        text("Android "+Build.VERSION.RELEASE+" · "+Build.MANUFACTURER+" "+Build.MODEL,20);
        text("Available memory: "+memory.availMem/1048576+" MB of "+memory.totalMem/1048576+" MB",20);
        text("Available app storage: "+getFilesDir().getUsableSpace()/1048576+" MB",20);
        text("Launcher input-to-draw sample: "+InputStats.report(),20);
        text("These are local, partial measurements. They do not measure another app's decoder, HDMI input delay or network performance. Run the same owner test on each TV before comparing.",20);
        text("Cleanup is unavailable in this preview. Any later cleanup needs exact model/firmware rules, a proposed change list, separate owner approval and independent rollback. Native Tizen and the Frame's Android streamer are separate targets.",20);
        button("Refresh baseline",this::recreate);button("Back",this::finish);
    }
}
