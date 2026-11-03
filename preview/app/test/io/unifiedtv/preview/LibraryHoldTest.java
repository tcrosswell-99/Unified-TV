package io.unifiedtv.preview;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.*;

/**
 * Holding OK on a tile in Your Films &amp; TV (pretend media server, no network): SET-032 Add to / Remove from Favourites,
 * per profile, with a Favourites row after Continue Watching; SET-034 "Wrong picture?" for Adults only, opening the
 * glass sidebar for that title.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class LibraryHoldTest {
    LibraryJourneyTest.MapStore store;FakeServer server;Context app;
    @Before public void start()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        store=new LibraryJourneyTest.MapStore();LibraryFolders.testStore=store;
        server=new FakeServer().folder("0","v","Video").folder("v","m","Movie").folder("v","k","Kids")
            .video("m","heat","Heat (1995).mkv").video("m","dune","Dune (2021).mkv").video("k","frozen","Frozen (2013).mkv","PG");
        LibraryIndex.browser=server;Posters.reset();Posters.testKey="";Posters.transport=(u,h,l)->{throw new java.io.IOException("no network in tests");};
        LibraryIndex.clear(app);
        LibraryFolders.save(app,FakeServer.source(),new ArrayList<>(Arrays.asList(new LibraryFolders.Folder("v","Video",null,false),new LibraryFolders.Folder("k","Kids",Arrays.asList("v"),true))));
    }
    @After public void stop(){LibraryFolders.testStore=null;LibraryIndex.browser=DlnaClient::browse;Posters.testKey=null;Posters.transport=null;Posters.reset();LibraryIndex.clear(app);}

    static PosterView tile(DlnaLibraryActivity a,String name,String row){
        // The tile in the named row: rows are a label, then a scrolling line of tiles.
        List<TextView> labels=LibraryJourneyTest.all(a.getWindow().getDecorView(),TextView.class);
        for(PosterView p:LibraryJourneyTest.all(a.getWindow().getDecorView(),PosterView.class)){
            if(p.card==null||!p.card.name().equals(name))continue;
            android.view.ViewGroup line=(android.view.ViewGroup)p.getParent().getParent();android.view.ViewGroup list=(android.view.ViewGroup)line.getParent();
            int at=list.indexOfChild(line);if(row==null||(at>0&&((TextView)list.getChildAt(at-1)).getText().toString().startsWith(row)))return p;
        }
        return null;
    }
    static List<String> rowLabels(DlnaLibraryActivity a){List<String> out=new ArrayList<>();for(LibraryCatalogue.Row r:a.catalogue().rows)out.add(r.label);return out;}
    static List<String> items(AlertDialog d){List<String> out=new ArrayList<>();ListView l=d.getListView();for(int i=0;i<l.getAdapter().getCount();i++)out.add(String.valueOf(l.getAdapter().getItem(i)));return out;}
    /** Holding OK on the tile in focus (focus moves there first, as the remote would have put it). */
    private AlertDialog hold(PosterView p){assertTrue(p.requestFocus());assertTrue("hold OK is handled",p.performLongClick());AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);assertTrue(d.isShowing());return d;}

    @Test public void holdOkAddsAndRemovesAFavouriteForThisProfile()throws Exception{
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            LibraryJourneyTest.until("rows",()->a.catalogue()!=null&&tile(a,"Heat",null)!=null);
            assertFalse(rowLabels(a).contains("Favourites"));
            AlertDialog d=hold(tile(a,"Heat","Films"));
            assertEquals(Arrays.asList("Add to Favourites",PictureFixActivity.ACTION,"Cancel"),items(d));
            Shadows.shadowOf(d).clickOnItem(0);
            LibraryJourneyTest.until("a Favourites row",()->rowLabels(a).contains("Favourites"));
            assertEquals("right after Continue Watching (none here), before Films",0,rowLabels(a).indexOf("Favourites"));
            assertNotNull(tile(a,"Heat","Favourites"));
            assertEquals("kept by the server's id, for Adults","[\"t:dlna:uuid:test-nas:heat\"]",app.getSharedPreferences("unified-tv-preview",0).getString(Favourites.key(Profiles.ADULT),""));
            assertFalse("Kids keep their own",app.getSharedPreferences("unified-tv-preview",0).contains(Favourites.key(Profiles.CHILD)));
            assertNull("holding OK never plays anything",Shadows.shadowOf(a).getNextStartedActivity());
            d=hold(tile(a,"Heat","Favourites"));assertEquals("Remove from Favourites",items(d).get(0));Shadows.shadowOf(d).clickOnItem(0);
            LibraryJourneyTest.until("the row goes when it is empty",()->!rowLabels(a).contains("Favourites"));
            // Cancel changes nothing.
            d=hold(tile(a,"Dune","Films"));Shadows.shadowOf(d).clickOnItem(2);LibraryJourneyTest.idle();
            assertTrue(Favourites.load(app.getSharedPreferences("unified-tv-preview",0)).isEmpty());
        }
    }
    @Test public void kidsKeepTheirOwnFavouritesButNeverChangePictures()throws Exception{
        app.getSharedPreferences("unified-tv-preview",0).edit().putString(Profiles.PREF,Profiles.CHILD).putString(Favourites.key(Profiles.ADULT),"[\"t:dlna:uuid:test-nas:frozen\"]").commit();
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            LibraryJourneyTest.until("rows",()->a.catalogue()!=null&&tile(a,"Frozen",null)!=null);
            assertFalse("the Adults' favourite isn't Kids'",rowLabels(a).contains("Favourites"));
            AlertDialog d=hold(tile(a,"Frozen",null));
            assertEquals("no Wrong picture? in Kids",Arrays.asList("Add to Favourites","Cancel"),items(d));
            Shadows.shadowOf(d).clickOnItem(0);LibraryJourneyTest.until("Kids' Favourites",()->rowLabels(a).contains("Favourites"));
            assertEquals("[\"t:dlna:uuid:test-nas:frozen\"]",app.getSharedPreferences("unified-tv-preview",0).getString(Favourites.key(Profiles.CHILD),""));
        }
    }
    @Test public void wrongPictureOpensTheSidebarForThatTitle()throws Exception{
        try(var c=Robolectric.buildActivity(DlnaLibraryActivity.class).setup()){DlnaLibraryActivity a=c.get();
            LibraryJourneyTest.until("rows",()->a.catalogue()!=null&&tile(a,"Heat",null)!=null);
            AlertDialog d=hold(tile(a,"Heat","Films"));Shadows.shadowOf(d).clickOnItem(1);
            Intent next=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(next);
            assertEquals(PictureFixActivity.class.getName(),next.getComponent().getClassName());
            assertEquals("Heat",next.getStringExtra("title"));assertEquals("1995",next.getStringExtra("year"));assertEquals("t:dlna:uuid:test-nas:heat",next.getStringExtra("key"));
            assertEquals("uuid:test-nas",next.getStringExtra("udn"));assertFalse(next.getBooleanExtra("tv",true));
            // The sidebar closes: the library (only paused under it) asks again for that title's picture, focus where it was.
            c.pause().resume();LibraryJourneyTest.idle();
            View f=a.getWindow().getDecorView().findFocus();assertTrue("focus stays on a tile: "+f,f instanceof PosterView);assertEquals("the same tile","Heat",((PosterView)f).card.name());
        }
    }
}
