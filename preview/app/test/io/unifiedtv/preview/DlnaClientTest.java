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
}
