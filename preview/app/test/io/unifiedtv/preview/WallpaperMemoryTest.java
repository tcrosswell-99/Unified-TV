package io.unifiedtv.preview;
import android.graphics.Bitmap;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.time.Duration;
import static org.junit.Assert.*;

/** The TCL froze during the Wallpaper (25 Oct 2026): pictures are freed as soon as they can no longer be seen. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class WallpaperMemoryTest {
    private static WallpaperActivity.Slide slide(){WallpaperActivity.Slide s=new WallpaperActivity.Slide();s.image=Bitmap.createBitmap(64,36,Bitmap.Config.RGB_565);s.clock=WallpaperPolicy.NONE;s.spot=new int[]{0,0,1,1};return s;}
    private static void idle(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    @Test public void aFadedOrSkippedPictureIsFreed(){
        try(var c=Robolectric.buildActivity(android.app.Activity.class).setup()){
            WallpaperActivity.Scene scene=new WallpaperActivity.Scene(c.get());c.get().setContentView(scene);
            WallpaperActivity.Slide a=slide(),b=slide(),d=slide();
            scene.show(a,false,10_000);scene.show(b,false,10_000);
            scene.show(d,false,10_000);idle(50);
            assertTrue("a picture replaced mid-fade is freed",a.image==null);
            assertNotNull("the picture fading out is kept until the fade ends",b.image);
            for(int i=0;i<200&&b.image!=null;i++){scene.draw(new android.graphics.Canvas(Bitmap.createBitmap(64,36,Bitmap.Config.ARGB_8888)));idle(16);}
            assertNull("after the fade the old picture is freed",b.image);
            assertNotNull("the picture on screen is kept",d.image);
            scene.clear();idle(50);assertNull("leaving frees everything",d.image);
        }
    }
    @Test public void releaseIsSafeTwice(){WallpaperActivity.Slide s=slide();Bitmap b=s.image;s.release();s.release();assertTrue(b.isRecycled());}
}
