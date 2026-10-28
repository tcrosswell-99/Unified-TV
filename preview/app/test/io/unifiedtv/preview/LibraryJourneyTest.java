package io.unifiedtv.preview;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Films & TV from the remote, against a pretend media server (no network): choosing folders in the glass sheet and
 * marking one for Kids, unselecting a folder while choosing, removing a saved folder, the loading indicator, the rows,
 * "All", search, playing, and Kids seeing only Kids titles.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class LibraryJourneyTest {
    static final class MapStore implements LibraryFolders.Store {
        final Map<String,String> saved=new HashMap<>();
        public JSONObject read(String id)throws Exception{String s=saved.get(id);return s==null?new JSONObject():new JSONObject(s);}
        public void save(String id,JSONObject value){saved.put(id,value.toString());}
        public void forget(String id){saved.remove(id);}
    }
    MapStore store;FakeServer server;Context app;

    @Before public void start(){
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        store=new MapStore();LibraryFolders.testStore=store;
        server=new FakeServer().folder("0","v","Video").folder("0","p","Photo").folder("v","m","Movie").folder("v","k","Kids")
            .video("m","heat","Heat (1995).mkv").video("m","dune","Dune (2021).mkv").video("k","frozen","Frozen (2013).mkv","PG").video("k","scary","Scary (2020).mkv","15");
        for(int i=0;i<40;i++)server.video("m","x"+i,"Film "+i+" ("+(1980+i)+").mkv");
        LibraryIndex.browser=server;LibrarySetupActivity.finder=(c,r)->Collections.singletonList(FakeServer.device());
        Posters.reset();Posters.testKey="";Posters.transport=(u,h,l)->{throw new java.io.IOException("no network in tests");};
        LibraryIndex.clear(app);
    }
    @After public void stop(){CountDownLatch g=server.gate;if(g!=null)g.countDown();LibraryCollector.reset();LibraryFolders.testStore=null;LibraryIndex.browser=DlnaClient::browse;Posters.testKey=null;Posters.transport=null;Posters.reset();LibraryIndex.clear(app);}

    static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    interface Check{boolean ok()throws Exception;}
    /** Lets the background threads answer: the main looper runs while waiting, up to ten seconds. */
    static void until(String what,Check c)throws Exception{long end=System.currentTimeMillis()+10_000;
        while(System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));if(c.ok())return;Thread.sleep(20);}
        idle();assertTrue("timed out waiting: "+what,c.ok());}
    static <T extends View> List<T> all(View v,Class<T> type){List<T> out=new ArrayList<>();collect(v,type,out);return out;}
    private static <T extends View> void collect(View v,Class<T> type,List<T> out){if(type.isInstance(v))out.add(type.cast(v));if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),type,out);}
    static Button button(android.app.Activity a,String text){for(Button b:all(a.getWindow().getDecorView(),Button.class))if(b.getText().toString().equals(text)&&b.isShown())return b;return null;}
    static boolean says(android.app.Activity a,String start){for(TextView t:all(a.getWindow().getDecorView(),TextView.class))if(t.getText().toString().startsWith(start)&&t.isShown())return true;return false;}
    void saveLibrary(boolean withKids)throws Exception{
        List<LibraryFolders.Folder> f=new ArrayList<>(Arrays.asList(new LibraryFolders.Folder("v","Video",null,false)));if(withKids)f.add(new LibraryFolders.Folder("k","Kids",Arrays.asList("v"),true));
        LibraryFolders.save(app,FakeServer.source(),f);
    }
    JSONArray roots(String id)throws Exception{return store.read(id).optJSONArray("roots");}

    // Owner, 25 October 2026: marking a folder for Kids is part of choosing folders, in the glass sheet.
    @Test public void chooseFoldersAndMarkOneForKids()throws Exception{
        try(var c=Robolectric.buildActivity(LibrarySetupActivity.class).setup()){LibrarySetupActivity a=c.get();
            until("the one server is chosen and its folders listed",()->button(a,"Video")!=null);
            assertEquals(LibrarySetupActivity.Page.BROWSE,a.page());assertNull("no address typed or shown",button(a,FakeServer.HOST));
            button(a,"Video").performClick();until("Video's folders",()->button(a,"Kids")!=null);
            button(a,"Kids").performClick();until("inside Kids",()->button(a,"Add this folder for Kids")!=null);
            button(a,"Add this folder for Kids").performClick();idle();
            assertEquals(LibrarySetupActivity.Page.MANAGE,a.page());assertTrue(a.folders().get(0).kids);assertNull("nothing saved before Save",store.saved.get("dlna"));
            // Adding the parent too: said plainly, titles show once.
            button(a,"Add a folder").performClick();until("top",()->button(a,"Video")!=null);button(a,"Video").performClick();until("Video",()->button(a,"Add this folder")!=null);
            button(a,"Add this folder").performClick();idle();
            assertTrue(ShadowToast.getTextOfLatestToast(),ShadowToast.getTextOfLatestToast().contains("contains “Kids”"));
            button(a,"Save changes").performClick();idle();
            assertEquals(LibrarySetupActivity.Page.SAVED,a.page());
            assertEquals(2,roots("dlna").length());assertEquals(1,roots(KidsPolicy.STORE).length());assertEquals("k",roots(KidsPolicy.STORE).getJSONObject(0).getString("id"));
            assertTrue(KidsPolicy.ready(store.read(KidsPolicy.STORE)));
        }
    }
    // Owner: a folder marked "selected" can be unselected the same way, before saving.
    @Test public void aSelectedFolderIsUnselectedTheSameWay()throws Exception{
        try(var c=Robolectric.buildActivity(LibrarySetupActivity.class).setup()){LibrarySetupActivity a=c.get();
            until("folders",()->button(a,"Video")!=null);button(a,"Video").performClick();until("Video",()->button(a,"Add this folder")!=null);
            button(a,"Add this folder").performClick();idle();assertEquals(1,a.folders().size());
            button(a,"Add a folder").performClick();until("top",()->button(a,"Video")!=null);
            Button again=button(a,"Video");assertTrue("the list marks it",again.getContentDescription().toString().contains("Added"));again.performClick();
            until("its row says how to unselect",()->button(a,"Selected — press OK to remove")!=null);
            button(a,"Selected — press OK to remove").performClick();until("the add rows again",()->button(a,"Add this folder")!=null);
            assertTrue(a.folders().isEmpty());assertNull(store.saved.get("dlna"));
        }
    }
    // Owner: a saved folder can be removed from the sidebar (asks first; nothing on the NAS is deleted), and Kids toggled.
    @Test public void aSavedFolderIsRemovedOrUnmarkedFromTheSidebar()throws Exception{
        saveLibrary(true);
        try(var c=Robolectric.buildActivity(LibrarySetupActivity.class).setup()){LibrarySetupActivity a=c.get();idle();
            assertEquals(LibrarySetupActivity.Page.MANAGE,a.page());assertNotNull(button(a,"Video"));
            assertTrue(button(a,"Kids").getContentDescription().toString().contains("For Kids"));
            button(a,"Video").performClick();idle();assertEquals(LibrarySetupActivity.Page.FOLDER,a.page());
            button(a,"Remove from library").performClick();idle();
            AlertDialog ask=ShadowAlertDialog.getLatestAlertDialog();assertNotNull("asks first",ask);
            assertTrue(Shadows.shadowOf(ask).getMessage().toString().contains("Nothing is deleted from the media server"));
            ask.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();
            assertEquals(LibrarySetupActivity.Page.MANAGE,a.page());assertNull(button(a,"Video"));
            button(a,"Kids").performClick();idle();button(a,"This folder is for Kids").performClick();idle();
            a.onBackPressed();idle();button(a,"Save changes").performClick();idle();
            assertEquals(1,roots("dlna").length());assertEquals("k",roots("dlna").getJSONObject(0).getString("id"));
            assertNull("no Kids folders left",store.saved.get(KidsPolicy.STORE));
        }
    }
    // A slow first list says so (spinner and count), then the rows; "All" opens the whole row; search finds; OK plays.
    @Test public void loadingThenRowsAllSearchAndPlay()throws Exception{
        saveLibrary(true);server.gate=new CountDownLatch(1);
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            until("the loading indicator",()->says(a,"Finding your films and TV"));
            View spinner=a.getWindow().getDecorView().findViewWithTag("spinner");assertNotNull(spinner);assertTrue(spinner instanceof ProgressBar);assertTrue(spinner.isShown());
            server.gate.countDown();
            until("rows",()->a.catalogue()!=null&&!all(a.getWindow().getDecorView(),PosterView.class).isEmpty());
            assertEquals(DlnaLibraryActivity.Kind.ROWS,a.page());
            List<String> labels=new ArrayList<>();for(TextView t:all(a.getWindow().getDecorView(),TextView.class))labels.add(t.getText().toString());
            assertTrue(labels.toString(),labels.stream().anyMatch(s->s.startsWith("Films")));assertTrue(labels.stream().anyMatch(s->s.startsWith("Kids")));
            View f=a.getWindow().getDecorView().findFocus();assertTrue("a poster has focus: "+f,f instanceof PosterView);
            PosterView allFilms=null;for(PosterView p:all(a.getWindow().getDecorView(),PosterView.class))if(p.card==null&&"All films".equals(p.action))allFilms=p;
            assertNotNull(allFilms);allFilms.performClick();idle();
            assertEquals(DlnaLibraryActivity.Kind.GRID,a.page());
            int films=0;for(PosterView p:all(a.getWindow().getDecorView(),PosterView.class))if(p.card!=null)films++;assertEquals("every film, Kids titles in their own row",42,films);
            a.onBackPressed();idle();assertEquals(DlnaLibraryActivity.Kind.ROWS,a.page());
            button(a,"Search").performClick();idle();assertEquals(DlnaLibraryActivity.Kind.SEARCH,a.page());
            EditText field=all(a.getWindow().getDecorView(),EditText.class).get(0);field.setText("heat");
            until("a result",()->{for(PosterView p:all(a.getWindow().getDecorView(),PosterView.class))if(p.card!=null&&p.card.name().equals("Heat"))return true;return false;});
            PosterView heat=null;for(PosterView p:all(a.getWindow().getDecorView(),PosterView.class))if(p.card!=null&&p.card.name().equals("Heat"))heat=p;
            heat.performClick();
            Intent play=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(PlayerActivity.class.getName(),play.getComponent().getClassName());
            assertEquals("dlna:uuid:test-nas:heat",play.getStringExtra("stableId"));assertEquals("heat",play.getStringExtra("dlnaItem"));assertEquals("v",play.getStringExtra("root"));
            assertTrue(play.getStringExtra("dlnaUrl").startsWith("http://"+FakeServer.HOST+":50002/"));
        }
    }
    // Kids: only the folders marked for Kids, within the Kids ratings.
    @Test public void kidsSeeOnlyKidsTitles()throws Exception{
        saveLibrary(true);app.getSharedPreferences("unified-tv-preview",0).edit().putString("profile","child").putString(HouseholdSettings.RATINGS,KidsPolicy.Scheme.UK_BBFC.id).commit();
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            until("rows",()->a.catalogue()!=null);
            Set<String> ids=new HashSet<>();for(LibraryCatalogue.Title t:a.catalogue().titles)ids.add(t.id);
            assertEquals(Collections.singleton("frozen"),ids);
            assertNull("Kids don't get the folders button",button(a,"Your folders"));
        }
        LibraryIndex.clear(app);store.forget(KidsPolicy.STORE);
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){idle();assertTrue(says(c.get(),KidsPolicy.ASK_ADULT));}
    }
    // Old "setup" requests (Settings, Launch) open the glass sheet, never a full-screen page.
    @Test public void setupRequestsOpenTheGlassSheet(){
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class,new Intent(app,DlnaLibraryActivity.class).putExtra("setup",true)).setup()){
            Intent next=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals(LibrarySetupActivity.class.getName(),next.getComponent().getClassName());assertTrue(c.get().isFinishing());
        }
    }
}
