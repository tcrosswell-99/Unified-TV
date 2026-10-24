package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
public class DlnaClientTest {
    @Test public void onlyLocalHttpOriginsAccepted()throws Exception{
        assertEquals("192.168.50.20",DlnaClient.local("http://192.168.50.20:50001/directory").getHost());
        for(String url:new String[]{"file:///media/movie.mkv","http://127.0.0.1/video","http://8.8.8.8/video","http://user:pass@192.168.50.20/video","http://192.168.50.20/video#fragment"})assertThrows(Exception.class,()->DlnaClient.local(url));
    }
    @Test public void externalEntitiesAreRejected(){
        String body="<!DOCTYPE root [<!ENTITY secret SYSTEM 'file:///does-not-exist'>]><root>&secret;</root>";
        assertThrows(Exception.class,()->DlnaClient.xml(body.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void namespacedMetadataAndSoapEscaping()throws Exception{
        assertEquals("Films & TV",DlnaClient.value(DlnaClient.xml("<root xmlns:d='urn:example'><d:title>Films &amp; TV</d:title></root>".getBytes(StandardCharsets.UTF_8)).getDocumentElement(),"title"));
        assertEquals("folder&amp;&lt;&gt;&quot;&apos;",DlnaClient.escape("folder&<>\"'"));
    }
    // A Browse response as a media server sends it: DIDL-Lite escaped inside the SOAP Result.
    static byte[] soap(String didl,int returned,int total){
        return ("<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><u:BrowseResponse xmlns:u='urn:schemas-upnp-org:service:ContentDirectory:1'><Result>"+DlnaClient.escape("<DIDL-Lite xmlns='urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/' xmlns:dc='http://purl.org/dc/elements/1.1/' xmlns:upnp='urn:schemas-upnp-org:metadata-1-0/upnp/'>"+didl+"</DIDL-Lite>")+"</Result><NumberReturned>"+returned+"</NumberReturned><TotalMatches>"+total+"</TotalMatches></u:BrowseResponse></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
    }
    static String video(int n){return "<item id='v"+n+"'><dc:title>Film "+n+"</dc:title><dc:date>1999-05-01</dc:date><upnp:albumArtURI>/art/"+n+".jpg</upnp:albumArtURI><res protocolInfo='http-get:*:video/mp4:*'>/media/"+n+".mp4</res></item>";}
    static String song(int n){return "<item id='a"+n+"'><dc:title>Song "+n+"</dc:title><res protocolInfo='http-get:*:audio/mpeg:*'>/media/"+n+".mp3</res></item>";}
    static final java.net.URI ORIGIN=java.net.URI.create("http://192.168.50.20:50001/ctl/ContentDir");
    @Test public void filteredEntriesDoNotEndPagingEarly()throws Exception{
        StringBuilder didl=new StringBuilder();for(int i=0;i<24;i++)didl.append(i%3==0?song(i):video(i));
        DlnaClient.Page page=DlnaClient.parse(soap(didl.toString(),24,60),ORIGIN);
        assertEquals(16,page.items.size());assertTrue(page.more(0));assertEquals(24,page.next(0));
        assertFalse(DlnaClient.parse(soap(video(1),12,60),ORIGIN).more(48));
    }
    @Test public void pageOfOnlyUnsupportedEntriesStillOffersTheNextPage()throws Exception{
        StringBuilder didl=new StringBuilder();for(int i=0;i<24;i++)didl.append(song(i));
        DlnaClient.Page page=DlnaClient.parse(soap(didl.toString(),24,100),ORIGIN);
        assertTrue(page.items.isEmpty());assertTrue(page.more(24));assertEquals(48,page.next(24));
    }
    @Test public void unknownTotalFallsBackToAFullPage()throws Exception{
        StringBuilder didl=new StringBuilder();for(int i=0;i<24;i++)didl.append(video(i));
        assertTrue(DlnaClient.parse(soap(didl.toString(),24,0),ORIGIN).more(0));
        assertFalse(DlnaClient.parse(soap(video(1),1,0),ORIGIN).more(0));
        assertFalse(DlnaClient.parse(soap("",0,0),ORIGIN).more(0));
    }
    @Test public void posterArtworkAndYearComeFromTheSameServerOnly()throws Exception{
        DlnaClient.Item film=DlnaClient.parse(soap(video(7),1,1),ORIGIN).items.get(0);
        assertEquals("http://192.168.50.20:50001/art/7.jpg",film.art);assertEquals("1999",film.year);assertEquals("http://192.168.50.20:50001/media/7.mp4",film.url);
        String foreign="<item id='x'><dc:title>Elsewhere</dc:title><upnp:albumArtURI>http://8.8.8.8/art.jpg</upnp:albumArtURI><res protocolInfo='http-get:*:video/mp4:*'>/media/x.mp4</res></item>";
        assertEquals("",DlnaClient.parse(soap(foreign,1,1),ORIGIN).items.get(0).art);
        String otherNas="<item id='y'><dc:title>Other</dc:title><res protocolInfo='http-get:*:video/mp4:*'>http://192.168.50.21/media/y.mp4</res></item>";
        assertTrue(DlnaClient.parse(soap(otherNas,1,1),ORIGIN).items.isEmpty());
    }
    @Test public void foldersAreKeptWithoutAResource()throws Exception{
        DlnaClient.Page page=DlnaClient.parse(soap("<container id='c1'><dc:title>Kids</dc:title></container>",1,1),ORIGIN);
        assertTrue(page.items.get(0).folder);assertEquals("Kids",page.items.get(0).title);assertEquals("",page.items.get(0).art);
    }
    @Test public void fileNamesBecomeReadableTitles(){
        assertEquals("The Matrix 1999",DlnaClient.title("The.Matrix.1999.mkv"));
        assertEquals("Ocean's Eleven",DlnaClient.title("Ocean's_Eleven.MP4"));
        assertEquals("Mr. Smith Goes to Washington",DlnaClient.title("Mr. Smith Goes to Washington.avi"));
        assertEquals("Back to the Future Part II",DlnaClient.title("Back.to.the.Future.Part.II"));
        assertEquals("Blade Runner",DlnaClient.title("Blade_Runner"));
        assertEquals("Up",DlnaClient.title("Up.m4v"));
        assertEquals("Star Wars",DlnaClient.title("Star Wars.ts"));
        assertEquals("Amélie",DlnaClient.title("Amélie.mov"));
        // Real titles are left alone.
        for(String t:new String[]{"S.W.A.T.","Mr. Smith","Toy Story 4","Se7en","WALL-E","Cats","1917","Film 1"})assertEquals(t,DlnaClient.title(t));
        assertEquals(".mkv",DlnaClient.title(".mkv"));assertEquals("",DlnaClient.title(null));assertEquals("Dune",DlnaClient.title("  Dune  "));
    }
    @Test public void parsedTitlesAreAlreadyClean()throws Exception{
        String item="<item id='m1'><dc:title>The.Matrix.1999.mkv</dc:title><res protocolInfo='http-get:*:video/x-matroska:*'>/media/m1.mkv</res></item>";
        assertEquals("The Matrix 1999",DlnaClient.parse(soap(item,1,1),ORIGIN).items.get(0).title);
    }
    @Test public void failuresAreClassifiedForPlainWording(){
        assertEquals(DlnaClient.Failure.REFUSED,DlnaClient.Failure.classify(403,""));
        assertEquals(DlnaClient.Failure.REFUSED,DlnaClient.Failure.classify(401,""));
        String missing="<s:Envelope><s:Body><s:Fault><detail><UPnPError xmlns='urn:schemas-upnp-org:control-1-0'><errorCode>701</errorCode><errorDescription>No such object</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>";
        assertEquals(DlnaClient.Failure.MISSING,DlnaClient.Failure.classify(500,missing));
        assertEquals(DlnaClient.Failure.MISSING,DlnaClient.Failure.classify(500,missing.replace("701","710")));
        assertEquals(DlnaClient.Failure.REFUSED,DlnaClient.Failure.classify(500,missing.replace("701","606")));
        assertEquals(DlnaClient.Failure.SERVER,DlnaClient.Failure.classify(500,missing.replace("701","720")));
        assertEquals(DlnaClient.Failure.SERVER,DlnaClient.Failure.classify(503,null));
        for(int k=1;k<=4;k++){String m=DlnaClient.Failure.plain(k);assertFalse(m.contains("://")||m.contains("SOAP")||m.contains("UPnP")||m.matches(".*\\d{3}.*"));}
        assertFalse(new DlnaClient.Failure(DlnaClient.Failure.UNREACHABLE).getMessage().contains("192.168"));
    }
    @Test public void noServerAndUnreadableServerAreToldApart(){
        DlnaClient.Report none=new DlnaClient.Report(),silent=new DlnaClient.Report();silent.answered=1;silent.unreadable=1;
        assertTrue(none.plain().startsWith("No media server found"));
        assertTrue(silent.plain().startsWith("A media server answered"));
        assertNotEquals(none.plain(),silent.plain());
    }
}
