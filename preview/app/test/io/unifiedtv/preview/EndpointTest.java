package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

public class EndpointTest {
    @Test public void acceptsIpv4Ipv6AndMdns(){
        assertEquals("https://media.local:8096",Endpoint.base("media.local","8096",true));
        assertEquals("http://[::1]:8123",Endpoint.base("::1","8123",false));
        assertEquals("https://[2001:db8::1]:443",Endpoint.base("[2001:db8::1]","443",true));
        assertEquals("http://127.0.0.1:80",Endpoint.base("127.0.0.1","80",false));
    }
    @Test public void rejectsAmbiguousHostsAndInvalidPorts(){
        for(String host:new String[]{"", "https://media.local", "a/b", "a@b", "a?b", "a#b", "a b","a%0d.local"})
            assertThrows(IllegalArgumentException.class,()->Endpoint.base(host,"80",false));
        for(String port:new String[]{"0","65536","-1","x",""})
            assertThrows(IllegalArgumentException.class,()->Endpoint.base("media.local",port,false));
    }
    @Test public void forbidsCrossOriginAndCredentialPaths(){
        String base="https://media.local:443";
        assertEquals(base+"/api/",Endpoint.resolve(base,"/api/").toString());
        for(String path:new String[]{"https://evil.example/", "//evil.example/api", "/api/#secret", "api/", "https://media.local:443/api"})
            assertThrows(IllegalArgumentException.class,()->Endpoint.resolve(base,path));
    }
    @Test public void liveGateCoversAliasesAndConfiguredPackage(){
        assertTrue(Endpoint.protectedApp("any.pkg","ReamSmartz",""));
        assertTrue(Endpoint.protectedApp("tv.smarters.app","TV",""));
        assertTrue(Endpoint.protectedApp("owner.package","Custom","owner.package"));
        assertFalse(Endpoint.protectedApp("bbc.iplayer.android","BBC",""));
    }
}
