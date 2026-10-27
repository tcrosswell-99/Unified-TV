package io.unifiedtv.preview;
import android.content.Context;
import android.view.KeyEvent;
import android.widget.Button;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import java.util.*;

/**
 * My Photos' folders made simple (owner's TCL, build run 99): "Use all my photos" in one OK; or one list of ticks where
 * OK ticks, Right opens a folder and Done saves; Back saves what is ticked and says so, never losing a choice silently.
 * A pretend media server (no network); the Vault with a test key (JVM tests have no Android Keystore).
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class PhotoFoldersTest {
    Context app;final PhotoFoldersActivity.Lister original=PhotoFoldersActivity.lister;
    final Map<String,PhotoSource.Page> pages=new HashMap<>();

    static DlnaClient.Item folder(String id,String title){DlnaClient.Item i=new DlnaClient.Item();i.id=id;i.title=title;i.folder=true;return i;}
    @Before public void start()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        Vault.testKey=javax.crypto.KeyGenerator.getInstance("AES").generateKey();
        new Vault(app).save("dlna",FakeServer.source());   // the film library's server, found without a search
        PhotoSource.Page top=new PhotoSource.Page();top.folders.addAll(Arrays.asList(folder("p","Photo"),folder("mu","Music"),folder("v","Video")));top.counts.put("p",2);top.returned=3;top.total=3;
        PhotoSource.Page photo=new PhotoSource.Page();photo.folders.addAll(Arrays.asList(folder("h","Holidays"),folder("f","Family")));photo.counts.put("h",1200);photo.counts.put("f",2200);photo.returned=2;photo.total=2;
        PhotoSource.Page empty=new PhotoSource.Page();
        pages.put("0",top);pages.put("p",photo);pages.put("h",empty);pages.put("f",empty);
        PhotoFoldersActivity.lister=(server,id,offset)->{PhotoSource.Page p=pages.get(id);if(p==null)throw new DlnaClient.Failure(DlnaClient.Failure.MISSING);return p;};
    }
    @After public void stop(){PhotoFoldersActivity.lister=original;Vault.testKey=null;}
    JSONArray roots()throws Exception{JSONArray r=new Vault(app).read("photos").optJSONArray("roots");return r==null?new JSONArray():r;}
    List<String> ids(JSONArray roots){List<String> out=new ArrayList<>();for(int i=0;i<roots.length();i++)out.add(roots.optJSONObject(i).optString("id"));return out;}
    static void right(Button b){b.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));b.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));}

    @Test public void useAllMyPhotosIsOneOk()throws Exception{
        try(var c=Robolectric.buildActivity(PhotoFoldersActivity.class).setup()){PhotoFoldersActivity a=c.get();
            LibraryJourneyTest.until("the choice at the top",()->LibraryJourneyTest.button(a,"Use all my photos")!=null);
            assertTrue("it says what that means",LibraryJourneyTest.says(a,"Everything in Photo, in one go."));
            LibraryJourneyTest.button(a,"Use all my photos").performClick();LibraryJourneyTest.idle();
            assertEquals("the server's picture folder, never its music or video, never its top",Collections.singletonList("p"),ids(roots()));
            assertEquals("All your photos are on the Wallpaper.",ShadowToast.getTextOfLatestToast());
            assertFalse("back on the summary",a.choosing());assertTrue(LibraryJourneyTest.says(a,"1 folder, about 2 photos on the Wallpaper."));
        }
    }
    @Test public void oneListOfTicksOkTicksRightOpensDoneSaves()throws Exception{
        try(var c=Robolectric.buildActivity(PhotoFoldersActivity.class).setup()){PhotoFoldersActivity a=c.get();
            LibraryJourneyTest.until("folders",()->LibraryJourneyTest.button(a,"Photo")!=null);
            right(LibraryJourneyTest.button(a,"Photo"));
            LibraryJourneyTest.until("inside Photo",()->LibraryJourneyTest.button(a,"Holidays")!=null);
            assertTrue(LibraryJourneyTest.says(a,"No folders ticked yet."));
            LibraryJourneyTest.button(a,"Holidays").performClick();LibraryJourneyTest.button(a,"Family").performClick();
            assertEquals(Arrays.asList("h","f"),a.ticked());
            assertTrue("the row shows its tick",LibraryJourneyTest.button(a,"Family").getContentDescription().toString().contains("Ticked"));
            assertTrue("one line says how much",LibraryJourneyTest.says(a,"2 folders ticked, about 3,400 photos."));
            LibraryJourneyTest.button(a,"Family").performClick();assertEquals("OK again unticks",Collections.singletonList("h"),a.ticked());
            assertTrue(LibraryJourneyTest.says(a,"1 folder ticked, about 1,200 photos."));
            LibraryJourneyTest.button(a,"Family").performClick();
            assertEquals("nothing saved before Done or Back",0,roots().length());
            LibraryJourneyTest.button(a,"Done").performClick();LibraryJourneyTest.idle();
            assertEquals(Arrays.asList("h","f"),ids(roots()));assertEquals(1200,roots().getJSONObject(0).getInt("count"));
            assertEquals("Saved: 2 folders on the Wallpaper.",ShadowToast.getTextOfLatestToast());assertTrue(a.isFinishing());
        }
    }
    @Test public void backKeepsWhatIsTickedAndSaysSo()throws Exception{
        try(var c=Robolectric.buildActivity(PhotoFoldersActivity.class).setup()){PhotoFoldersActivity a=c.get();
            LibraryJourneyTest.until("folders",()->LibraryJourneyTest.button(a,"Photo")!=null);
            LibraryJourneyTest.button(a,"Photo").performClick();   // a tick at the top
            right(LibraryJourneyTest.button(a,"Photo"));LibraryJourneyTest.until("inside",()->LibraryJourneyTest.button(a,"Holidays")!=null);
            assertTrue("a tick made at the top still shows in the count",LibraryJourneyTest.says(a,"1 folder ticked, about 2 photos."));
            LibraryJourneyTest.button(a,"Holidays").performClick();
            a.onBackPressed();LibraryJourneyTest.until("up a folder",()->LibraryJourneyTest.button(a,"Photo")!=null);
            assertTrue("ticks kept going up",a.ticked().containsAll(Arrays.asList("p","h")));
            assertTrue(LibraryJourneyTest.button(a,"Photo").getContentDescription().toString().contains("Ticked"));
            a.onBackPressed();LibraryJourneyTest.idle();
            assertEquals("Back from the top saves what is ticked",Arrays.asList("p","h"),ids(roots()));
            assertEquals("Saved: 2 folders on the Wallpaper.",ShadowToast.getTextOfLatestToast());
            assertFalse(a.choosing());assertTrue("the summary, with both",LibraryJourneyTest.button(a,"Holidays")!=null&&LibraryJourneyTest.button(a,"Photo")!=null);
        }
        // Coming back: the chooser starts with the saved folders ticked.
        try(var c=Robolectric.buildActivity(PhotoFoldersActivity.class).setup()){PhotoFoldersActivity a=c.get();LibraryJourneyTest.idle();
            LibraryJourneyTest.button(a,"Change folders").performClick();
            LibraryJourneyTest.until("folders",()->LibraryJourneyTest.button(a,"Photo")!=null);
            assertEquals(Arrays.asList("p","h"),a.ticked());
        }
    }
}
