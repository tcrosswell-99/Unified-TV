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
}
