package io.unifiedtv.preview;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;

/**
 * The household's folders as the owner set them up on the TCL: "Video" chosen by mistake alongside "TV Show" and "Kids"
 * inside it. Titles show once; Kids see only the Kids folder within the Kids ratings; Adults see everything.
 */
public class LibraryCatalogueTest {
    FakeServer server;JSONObject household,kids;

    @Before public void nas()throws Exception{
        server=new FakeServer().folder("0","v","Video").folder("v","m","Movie").folder("v","t","TV Show").folder("v","k","Kids")
            .video("m","heat","Heat (1995).mkv").video("m","dune","Dune.2021.2160p.UHD.mkv").folder("m","af","Alien (1979)").video("af","alien","movie.mkv")
            .folder("t","dw","Doctor Who (2005)").folder("dw","dw1","Season 1").video("dw1","rose","Doctor.Who.2005.S01E01.Rose.mkv").video("dw1","eotw","Doctor.Who.2005.S01E02.mkv")
            .folder("t","lod","Line of Duty").video("lod","pilot","01 - Pilot.mkv")
            .folder("k","bl","Bluey").video("bl","xylo","Bluey S01E01 Magic Xylophone.mkv").video("k","frozen","Frozen (2013).mkv","PG").video("k","scary","Scary (2020).mkv","15")
            .alias("m","frozen-again","frozen");
        LibraryIndex.browser=server;
        household=LibraryFolders.records(FakeServer.source(),Arrays.asList(new LibraryFolders.Folder("v","Video",null,false),new LibraryFolders.Folder("t","TV Show",Arrays.asList("v"),false),new LibraryFolders.Folder("k","Kids",Arrays.asList("v"),true)))[0];
        kids=LibraryFolders.records(FakeServer.source(),Arrays.asList(new LibraryFolders.Folder("k","Kids",Arrays.asList("v"),true)))[0];
    }
    LibraryIndex.Index walk(LibraryFolders.Library lib,long now)throws Exception{
        LibraryIndex.Index i=new LibraryIndex.Index();Set<String> all=new HashSet<>();for(LibraryFolders.Folder f:lib.folders)all.add(f.id);
        for(LibraryFolders.Folder f:lib.folders){Set<String> others=new HashSet<>(all);others.remove(f.id);i.roots.put(f.id,LibraryIndex.walk(lib.source,f.id,others,null,null,now));}
        return i;
    }
    LibraryCatalogue.Catalogue adults(ResumeStore store)throws Exception{LibraryFolders.Library lib=LibraryFolders.library(household,kids,false);return LibraryCatalogue.build(walk(lib,1000),lib,KidsPolicy.Scheme.UK_BBFC,store,5000);}
    static List<String> names(LibraryCatalogue.Row r){List<String> out=new ArrayList<>();for(LibraryCatalogue.Card c:r.all)out.add(c.name());return out;}
    static List<String> ids(LibraryCatalogue.Row r){List<String> out=new ArrayList<>();for(LibraryCatalogue.Card c:r.all)out.add(c.title!=null?c.title.id:c.show.key);return out;}

    @Test public void adultsSeeEverythingOnceInSensibleRows()throws Exception{
        LibraryCatalogue.Catalogue c=adults(new ResumeStore(""));
        assertEquals("Heat, Dune, Alien, two Doctor Who, Line of Duty, Bluey, Frozen (once), Scary",9,c.titles.size());
        List<String> rows=new ArrayList<>();for(LibraryCatalogue.Row r:c.rows)rows.add(r.id);assertEquals(Arrays.asList("films","tv","kids"),rows);
        assertEquals("newest first",Arrays.asList("Dune","Heat","Alien"),names(c.row("films")));
        assertEquals(Arrays.asList("Doctor Who","Line of Duty"),names(c.row("tv")));
        assertEquals(Arrays.asList("Bluey","Frozen","Scary"),names(c.row("kids")));
        LibraryCatalogue.Show dw=c.shows.get("doctor who|2005");assertEquals(2,dw.episodes.size());assertEquals("2005",dw.year);
        assertEquals("S1 E1 · Rose",dw.episodes.get(0).label());assertEquals(2,dw.episodes.get(1).episode);
        LibraryCatalogue.Title pilot=c.shows.get("line of duty").episodes.get(0);assertEquals(1,pilot.episode);assertEquals("Pilot",pilot.episodeName);
        for(LibraryCatalogue.Title t:c.titles){assertTrue(t.url,t.url.startsWith("http://"+FakeServer.HOST+":50002/v/"));assertTrue(t.stableId.startsWith("dlna:uuid:test-nas:"));}
    }
    @Test public void kidsSeeOnlyTheKidsFolderWithinTheirRatings()throws Exception{
        LibraryFolders.Library lib=LibraryFolders.library(household,kids,true);assertTrue(lib.ready);assertEquals(1,lib.folders.size());
        LibraryCatalogue.Catalogue c=LibraryCatalogue.build(walk(lib,1000),lib,KidsPolicy.Scheme.UK_BBFC,new ResumeStore(""),5000);
        Set<String> seen=new HashSet<>();for(LibraryCatalogue.Title t:c.titles)seen.add(t.id);
        assertEquals("no adult title, and not the 15",new HashSet<>(Arrays.asList("xylo","frozen")),seen);
        List<String> rows=new ArrayList<>();for(LibraryCatalogue.Row r:c.rows)rows.add(r.id);assertEquals("no Kids row inside Kids",Arrays.asList("films","tv"),rows);
        assertFalse("nothing for Kids until an adult marks a folder",LibraryFolders.library(household,new JSONObject(),true).ready);
    }
    @Test public void continueWatchingIsMostRecentFirstAndFinishedTitlesDropOff()throws Exception{
        LibraryCatalogue.Catalogue all=adults(new ResumeStore(""));Map<String,LibraryCatalogue.Title> by=new HashMap<>();for(LibraryCatalogue.Title t:all.titles)by.put(t.id,t);
        ResumeStore store=new ResumeStore("");
        store.record(ResumeStore.key(by.get("heat").stableId),600_000,6_000_000,1000);
        store.record(ResumeStore.key(by.get("rose").stableId),300_000,2_700_000,3000);
        store.record(ResumeStore.key(by.get("alien").url),900_000,7_000_000,2000);   // an earlier place, kept by stream
        LibraryCatalogue.Catalogue c=adults(store);
        assertEquals("continue",c.rows.get(0).id);assertEquals(Arrays.asList("rose","alien","heat"),ids(c.rows.get(0)));
        assertEquals(300_000,c.rows.get(0).all.get(0).resume);assertEquals("Doctor Who",c.rows.get(0).all.get(0).name());
        store.record(ResumeStore.key(by.get("rose").stableId),2_699_000,2_700_000,4000);   // watched to the end
        assertEquals(Arrays.asList("alien","heat"),ids(adults(store).rows.get(0)));
        assertNotEquals("continue",adults(new ResumeStore("")).rows.get(0).id);
    }
    @Test public void recentlyAddedAppearsAfterTheFirstList()throws Exception{
        LibraryFolders.Library lib=LibraryFolders.library(household,kids,false);LibraryIndex.Index first=walk(lib,1000);
        assertNull(LibraryCatalogue.build(first,lib,KidsPolicy.Scheme.UK_BBFC,null,5000).row("recent"));
        server.video("m","new","Wicked (2024).mkv").video("dw1","new-ep","Doctor.Who.2005.S01E03.mkv");
        LibraryIndex.Index next=new LibraryIndex.Index();for(LibraryFolders.Folder f:lib.folders){Set<String> o=new HashSet<>();for(LibraryFolders.Folder x:lib.folders)if(!x.id.equals(f.id))o.add(x.id);
            next.roots.put(f.id,LibraryIndex.walk(lib.source,f.id,o,first.roots.get(f.id),null,f.id.equals("v")?9_000_000:9_500_000));}
        LibraryCatalogue.Row recent=LibraryCatalogue.build(next,lib,KidsPolicy.Scheme.UK_BBFC,null,9_600_000).row("recent");
        assertEquals("newest first; the programme's tile, not the episode",Arrays.asList("Doctor Who","Wicked"),names(recent));
    }
    @Test public void searchFindsFilmsProgrammesAndEpisodesLocally()throws Exception{
        LibraryCatalogue.Catalogue c=adults(new ResumeStore(""));
        List<LibraryCatalogue.Card> doc=LibraryCatalogue.search(c,"doc");assertEquals(1,doc.size());assertNotNull(doc.get(0).show);
        assertEquals("rose",LibraryCatalogue.search(c,"Rose").get(0).title.id);
        assertEquals("Heat",LibraryCatalogue.search(c,"he").get(0).name());
        assertEquals("word starts, any order","Line of Duty",LibraryCatalogue.search(c,"duty line").get(0).name());
        assertTrue(LibraryCatalogue.search(c,"xyz").isEmpty());assertTrue(LibraryCatalogue.search(c,"  ").isEmpty());
        assertEquals("accents and case don't matter",1,LibraryCatalogue.search(c,"FROZEN").size());
    }
    @Test public void anAllViewHoldsTheWholeRow()throws Exception{
        LibraryFolders.Library lib=LibraryFolders.library(household,kids,false);
        for(int i=0;i<LibraryCatalogue.ROW_MAX+12;i++)server.video("m","x"+i,"Film "+i+" ("+(1980+i%40)+").mkv");
        LibraryCatalogue.Row films=LibraryCatalogue.build(walk(lib,1000),lib,KidsPolicy.Scheme.UK_BBFC,null,5000).row("films");
        assertEquals(LibraryCatalogue.ROW_MAX,films.shown().size());assertEquals(LibraryCatalogue.ROW_MAX+15,films.all.size());
    }
    // SET-032: Favourites, after Continue Watching, only when the profile has some in its own catalogue; kept by server id.
    @Test public void favouritesFollowContinueWatchingOnlyWhenThereAreSome()throws Exception{
        LibraryFolders.Library lib=LibraryFolders.library(household,kids,false);LibraryIndex.Index index=walk(lib,1000);
        List<String> fav=Arrays.asList("s:doctor who|2005","t:dlna:uuid:test-nas:heat","t:dlna:uuid:other-nas:heat","t:dlna:uuid:test-nas:gone");
        LibraryCatalogue.Catalogue c=LibraryCatalogue.build(index,lib,KidsPolicy.Scheme.UK_BBFC,new ResumeStore(""),fav,5000);
        List<String> rows=new ArrayList<>();for(LibraryCatalogue.Row r:c.rows)rows.add(r.id);assertEquals(Arrays.asList("favourites","films","tv","kids"),rows);
        assertEquals("saved order, newest first; another server's title of the same name and a title gone from the NAS are left out",Arrays.asList("Doctor Who","Heat"),names(c.row("favourites")));
        assertEquals("Favourites",c.row("favourites").label);
        ResumeStore store=new ResumeStore("");store.record(ResumeStore.key("dlna:uuid:test-nas:alien"),900_000,7_000_000,2000);
        rows.clear();for(LibraryCatalogue.Row r:LibraryCatalogue.build(index,lib,KidsPolicy.Scheme.UK_BBFC,store,fav,5000).rows)rows.add(r.id);
        assertEquals("after Continue Watching",Arrays.asList("continue","favourites","films","tv","kids"),rows);
        assertNull("none chosen: no row",LibraryCatalogue.build(index,lib,KidsPolicy.Scheme.UK_BBFC,store,new ArrayList<>(),5000).row("favourites"));
        assertNull("none of them here: no row",LibraryCatalogue.build(index,lib,KidsPolicy.Scheme.UK_BBFC,store,Arrays.asList("t:dlna:uuid:test-nas:gone"),5000).row("favourites"));
    }
    @Test public void kidsFavouritesStayInsideKids()throws Exception{
        LibraryFolders.Library lib=LibraryFolders.library(household,kids,true);
        LibraryCatalogue.Catalogue c=LibraryCatalogue.build(walk(lib,1000),lib,KidsPolicy.Scheme.UK_BBFC,new ResumeStore(""),Arrays.asList("t:dlna:uuid:test-nas:heat","t:dlna:uuid:test-nas:scary","t:dlna:uuid:test-nas:frozen"),5000);
        assertEquals("an adult title and a 15 never show in Kids, even as favourites",Arrays.asList("Frozen"),names(c.row("favourites")));
    }
    @Test public void favouritesAreAListPerProfile(){
        List<String> one=Favourites.toggle(new ArrayList<>(),"t:a");List<String> two=Favourites.toggle(one,"s:b");
        assertEquals("newest first",Arrays.asList("s:b","t:a"),two);assertEquals("again removes it",Arrays.asList("s:b"),Favourites.toggle(two,"t:a"));
        assertEquals(two,Favourites.parse(Favourites.write(two)));assertTrue(Favourites.parse("not json").isEmpty());assertTrue(Favourites.parse(null).isEmpty());
        List<String> many=new ArrayList<>();for(int i=0;i<Favourites.MAX+5;i++)many=Favourites.toggle(many,"t:"+i);
        assertEquals(Favourites.MAX,many.size());assertEquals("the oldest goes first","t:"+(Favourites.MAX+4),many.get(0));
        assertNotEquals(Favourites.key(Profiles.ADULT),Favourites.key(Profiles.CHILD));assertEquals(Favourites.key("anything"),Favourites.key(Profiles.ADULT));
        assertEquals("Add to Favourites",Favourites.action(false));
        MapPrefs p=new MapPrefs();Favourites.toggle(p,"t:x");assertEquals(1,Favourites.count(p,Profiles.ADULT));assertEquals(0,Favourites.count(p,Profiles.CHILD));
        Favourites.clear(p,Profiles.ADULT);assertEquals(0,Favourites.count(p,Profiles.ADULT));assertEquals("Remove from Favourites",Favourites.action(true));
    }
    @Test public void namesAreReadFromFoldersWhenTheFileSaysLittle(){
        LibraryIndex.Entry e=new LibraryIndex.Entry();e.id="1";e.root="v";e.title="movie.mkv";e.url="http|80|/1.mkv";e.pathNames.add("Arrival (2016)");e.pathIds.add("a");
        LibraryCatalogue.Title t=LibraryCatalogue.classify(e,"Video",null,"u");assertEquals(LibraryCatalogue.Kind.FILM,t.kind);assertEquals("Arrival",t.name);assertEquals("2016",t.year);
        e.title="Arrival.mkv";t=LibraryCatalogue.classify(e,"Video",null,"u");assertEquals("2016",t.year);
        e.title="Episode 4.mkv";e.pathNames.clear();e.pathIds.clear();e.pathNames.addAll(Arrays.asList("Taskmaster","Series 2"));e.pathIds.addAll(Arrays.asList("tm","s2"));
        t=LibraryCatalogue.classify(e,"Video",null,"u");assertEquals(LibraryCatalogue.Kind.EPISODE,t.kind);assertEquals("Taskmaster",t.showName);assertEquals(2,t.season);assertEquals(4,t.episode);
        assertTrue(LibraryCatalogue.tvRoot("TV Show"));assertTrue(LibraryCatalogue.tvRoot("Programmes"));assertFalse(LibraryCatalogue.tvRoot("Video"));assertFalse(LibraryCatalogue.tvRoot("Movies"));
    }
}
