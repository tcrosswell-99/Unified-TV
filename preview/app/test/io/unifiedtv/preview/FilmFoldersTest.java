package io.unifiedtv.preview;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Owner's TCL, 27 October 2026 (Synology Media Server over DLNA): films kept as a folder per film, often with a sample
 * beside them. A folder that comes down to one main video is that film (one tile, one OK plays it, named from the folder
 * when it says more); samples, trailers and extras never show; seasons and collections stay as they were. Pretend server,
 * no network.
 */
public class FilmFoldersTest {
    static final long GB=1024L*1024*1024,MB=1024L*1024,MIN=60_000L;
    FakeServer server;

    @Before public void nas(){
        server=new FakeServer().folder("0","v","Video").folder("v","f","Films").folder("v","t","TV Show")
            // A folder per film: the main video, a sample by name, one in a Sample folder, a trailer, and a scene-named main.
            .folder("f","sf","Some Film (2019)").video("sf","sf-main","Some.Film.2019.1080p.BluRay.x264-GROUP.mkv",8*GB,128*MIN)
                .video("sf","sf-s1","sample.mkv",40*MB,MIN).video("sf","sf-s2","Some.Film.sample.mkv",60*MB,MIN).video("sf","sf-tr","Some Film - Trailer.mkv",90*MB,2*MIN)
                .folder("sf","sf-sd","Sample").video("sf-sd","sf-s3","grp-sf-1080p.mkv",50*MB,MIN)
            .folder("f","of","Other Film (2020)").video("of","of-main","grp-of1080p.mkv",6*GB,110*MIN)
                .video("of","of-small","of-clip.mkv",120*MB,9*MIN)       // a sample by size only: under a tenth and under 300 MB
                .video("of","of-short","making-of.mkv",900*MB,3*MIN)     // a sample by length only: three minutes beside nearly two hours
            // Several main videos: a collection, each film its own tile under its own name.
            .folder("f","col","Alien Collection").video("col","a1","Alien (1979).mkv",4*GB,117*MIN).video("col","a2","Aliens (1986).mkv",5*GB,137*MIN)
            // A programme in a TV folder, as before; and one called Extras, which is never taken for extras.
            .folder("t","dw","Doctor Who (2005)").folder("dw","dw1","Season 1").video("dw1","rose","Doctor.Who.2005.S01E01.Rose.mkv",GB,45*MIN).video("dw1","eotw","Doctor.Who.2005.S01E02.mkv",GB,44*MIN)
            .folder("t","ex","Extras").folder("ex","ex1","Season 1").video("ex1","ex-e1","Extras S01E01 Ben Stiller.mkv",GB,30*MIN).video("ex1","ex-e2","Extras S01E02 Ross Kemp.mkv",GB,30*MIN);
        LibraryIndex.browser=server;
    }
    @After public void done(){LibraryIndex.browser=DlnaClient::browse;}

    LibraryCatalogue.Catalogue build(String... rootIdName)throws Exception{
        List<LibraryFolders.Folder> folders=new ArrayList<>();for(int i=0;i<rootIdName.length;i+=2)folders.add(new LibraryFolders.Folder(rootIdName[i],rootIdName[i+1],null,false));
        JSONObject household=LibraryFolders.records(FakeServer.source(),folders)[0];LibraryFolders.Library lib=LibraryFolders.library(household,new JSONObject(),false);
        LibraryIndex.Index index=new LibraryIndex.Index();Set<String> all=new HashSet<>();for(LibraryFolders.Folder f:lib.folders)all.add(f.id);
        for(LibraryFolders.Folder f:lib.folders){Set<String> others=new HashSet<>(all);others.remove(f.id);index.roots.put(f.id,LibraryIndex.walk(lib.source,f.id,others,null,null,1000));}
        // Through the saved list, as on the TV: sizes and lengths must survive being written and read back.
        return LibraryCatalogue.build(LibraryIndex.parse(LibraryIndex.json(index)),lib,KidsPolicy.Scheme.UK_BBFC,new ResumeStore(""),5000);
    }
    static Map<String,LibraryCatalogue.Title> byId(LibraryCatalogue.Catalogue c){Map<String,LibraryCatalogue.Title> m=new LinkedHashMap<>();for(LibraryCatalogue.Title t:c.titles)m.put(t.id,t);return m;}

    @Test public void aFolderPerFilmIsThatFilmAndOneOkPlaysIt()throws Exception{
        LibraryCatalogue.Catalogue c=build("f","Films");Map<String,LibraryCatalogue.Title> t=byId(c);
        LibraryCatalogue.Title film=t.get("sf-main");assertNotNull(film);
        assertEquals(LibraryCatalogue.Kind.FILM,film.kind);assertEquals("Some Film",film.name);assertEquals("2019",film.year);
        assertTrue("a film plays straight away: it has its own stream",film.url.endsWith("/v/sf-main.mkv"));
        assertEquals("the scene-named file takes its folder's title and year","Other Film",t.get("of-main").name);assertEquals("2020",t.get("of-main").year);
        LibraryCatalogue.Card card=new LibraryCatalogue.Card(t.get("of-main"));
        assertEquals("posters are looked up by the folder's title","Other Film",card.lookupTitle());assertEquals("2020",card.lookupYear());assertFalse(card.tv());
        // One tile each in Films: no folder tile, no programme, no "Some Film" twice.
        List<String> films=new ArrayList<>();for(LibraryCatalogue.Card k:c.row("films").all)films.add(k.name());
        assertEquals(Arrays.asList("Other Film","Some Film","Aliens","Alien"),films);assertTrue(c.shows.isEmpty());
    }
    @Test public void samplesAndExtrasAreLeftOutByNameSizeAndLength()throws Exception{
        Set<String> ids=byId(build("f","Films")).keySet();
        for(String gone:new String[]{"sf-s1","sf-s2","sf-s3","sf-tr","of-small","of-short"})assertFalse(gone,ids.contains(gone));
        // By name alone, even when the server sends no sizes or lengths.
        LibraryIndex.Entry main=entry("Some Film (2019)","Some Film 2019 1080p BluRay x264-GROUP",0,0),sample=entry("Some Film (2019)","Film-sample",0,0),
            trailer=entry("Some Film (2019)","Some Film 2019 Trailer",0,0);
        assertEquals(Collections.singletonList(main),LibraryCatalogue.mains(Arrays.asList(main,sample,trailer)));
        // Size and length rules need the main video to stand out: two similar files both stay.
        LibraryIndex.Entry part1=entry("Film","Film CD1",700*MB,50*MIN),part2=entry("Film","Film CD2",650*MB,48*MIN);
        assertEquals(2,LibraryCatalogue.mains(Arrays.asList(part1,part2)).size());
        LibraryIndex.Entry shortMain=entry("Shorts","Short film",80*MB,12*MIN),shorter=entry("Shorts","Shorter film",70*MB,4*MIN);
        assertEquals("a four-minute film beside a twelve-minute one is not a sample",2,LibraryCatalogue.mains(Arrays.asList(shortMain,shorter)).size());
        LibraryIndex.Entry big=entry("Film","Film",4*GB,100*MIN),mid=entry("Film","Film part two",350*MB,20*MIN);
        assertEquals("over 300 MB is never a sample by size",2,LibraryCatalogue.mains(Arrays.asList(big,mid)).size());
    }
    @Test public void foldersWithSeveralMainVideosStayAsTheyWere()throws Exception{
        LibraryCatalogue.Catalogue c=build("f","Films","t","TV Show");Map<String,LibraryCatalogue.Title> t=byId(c);
        assertEquals("a collection: each film by its own name","Alien",t.get("a1").name);assertEquals("1979",t.get("a1").year);assertEquals("Aliens",t.get("a2").name);
        LibraryCatalogue.Show dw=c.shows.get("doctor who|2005");assertNotNull(dw);assertEquals(2,dw.episodes.size());
        LibraryCatalogue.Show extras=c.shows.get("extras");assertNotNull("a programme called Extras stays",extras);assertEquals(2,extras.episodes.size());
        assertEquals(Arrays.asList("Doctor Who","Extras"),LibraryCatalogueTest.names(c.row("tv")));
    }
    @Test public void aFilmsFolderInsideATvNamedFolderHoldsFilms()throws Exception{
        server.folder("0","mix","Films and TV Shows").folder("mix","mixf","Films").folder("mixf","hf","Heat (1995)").video("hf","heat","Heat.1995.1080p.mkv",9*GB,170*MIN)
            .folder("mix","lod","Line of Duty").video("lod","pilot","01 - Pilot.mkv",GB,60*MIN).video("lod","two","02 - Two.mkv",GB,60*MIN);
        Map<String,LibraryCatalogue.Title> t=byId(build("mix","Films and TV Shows"));
        assertEquals("not a programme called Films",LibraryCatalogue.Kind.FILM,t.get("heat").kind);assertEquals("Heat",t.get("heat").name);assertEquals("1995",t.get("heat").year);
        assertEquals(LibraryCatalogue.Kind.EPISODE,t.get("pilot").kind);assertEquals("Line of Duty",t.get("pilot").showName);
    }
    @Test public void theFolderNameWinsOnlyWhenItSaysMore(){
        assertTrue(more("movie","Some Film (2019)"));assertTrue(more("Some Film","Some Film (2019)"));assertTrue(more("grp-sf1080p","Some Film"));
        assertTrue(more("01 - Part one","Some Documentary"));assertTrue(more("Some Film 3D","Some Film"));
        assertFalse("a genre folder holding one film",more("Die Hard (1988)","Action"));assertFalse(more("Die Hard","Action"));
        assertFalse("a folder named for films as a kind",more("grp-sf1080p","Films"));assertFalse(more("Iron Man (2008)","Marvel"));
        assertFalse("a different film's year",more("Totally Different (2005)","Some Film (2019)"));assertFalse("21 is a title",more("21","Action"));
    }
    static boolean more(String file,String folder){return LibraryCatalogue.folderSaysMore(MediaName.parse(file),file,MediaName.parse(folder),folder);}
    static LibraryIndex.Entry entry(String folder,String title,long size,long duration){
        LibraryIndex.Entry e=new LibraryIndex.Entry();e.id=title;e.title=title;e.size=size;e.duration=duration;e.pathNames.add(folder);e.pathIds.add(folder);return e;}

    @Test public void sceneStyleNamesAreCleaned(){
        clean("Some.Film.2019.1080p.BluRay.x264-GROUP","Some Film","2019");
        clean("www.Example.org - Some.Film.2019.1080p","Some Film","2019");
        clean("[ www.example.org ] Some Film (2019)","Some Film","2019");
        clean("Some-Film-2019-1080p","Some Film","2019");
        clean("Some Film - Extended Edition (2019)","Some Film","2019");
        clean("Some.Film.Directors.Cut.2019.1080p","Some Film","2019");
        clean("Some.Film.2019.1080p.AMZN.WEB-DL.DDP5.1.H.264-GROUP","Some Film","2019");
        clean("Spider-Man (2002)","Spider-Man","2002");
        clean("The Final Cut (2004)","The Final Cut","2004");
        assertTrue(MediaName.sample("Film-sample.mkv"));assertTrue(MediaName.sample("SAMPLE.mkv"));assertFalse(MediaName.sample("The Sampler (2018).mkv"));
        assertTrue(MediaName.extra("trailer.mkv"));assertTrue(MediaName.extra("Some.Film.2019.Featurette.mkv"));
        assertFalse("a title that starts with the word",MediaName.extra("Trailer Park Boys The Movie (2006).mkv"));assertFalse(MediaName.extra("Extras S01E01.mkv"));
        assertTrue(MediaName.extrasFolder("Behind The Scenes"));assertTrue(MediaName.filmsFolder("My Films"));assertFalse(MediaName.filmsFolder("Films and TV"));
    }
    static void clean(String raw,String title,String year){MediaName.Parsed p=MediaName.parse(DlnaClient.title(raw));assertEquals(raw,title,p.title);assertEquals(raw,year,p.year);}

    @Test public void sizeAndLengthAreReadFromTheServersResource()throws Exception{
        String didl="<item id='m1' parentID='f1'><dc:title>Some.Film.2019.1080p</dc:title><res protocolInfo='http-get:*:video/x-matroska:*' size='8589934592' duration='2:08:03.250'>/media/m1.mkv</res></item>"
            +"<item id='m2' parentID='f1'><dc:title>sample</dc:title><res protocolInfo='http-get:*:video/x-matroska:*'>/media/m2.mkv</res></item>";
        DlnaClient.Page p=DlnaClient.parse(DlnaClientTest.soap(didl,2,2),DlnaClientTest.ORIGIN);
        assertEquals(8589934592L,p.items.get(0).size);assertEquals((2*3600+8*60+3)*1000L+250,p.items.get(0).duration);
        assertEquals("none sent: unknown",0,p.items.get(1).size);assertEquals(0,p.items.get(1).duration);
        assertEquals(0,DlnaClient.durationMs("soon"));assertEquals(5000,DlnaClient.durationMs("0:00:05"));assertEquals(0,DlnaClient.bytes("-5"));
        LibraryIndex.Entry e=new LibraryIndex.Entry();e.id="m1";e.size=42;e.duration=7;LibraryIndex.Entry back=LibraryIndex.Entry.of(e.json(),"r");
        assertEquals(42,back.size);assertEquals(7,back.duration);
        assertEquals("older saved lists have neither",0,LibraryIndex.Entry.of(new JSONObject().put("i","x"),"r").size);
    }
}
