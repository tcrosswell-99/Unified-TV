package io.unifiedtv.preview;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import static org.junit.Assert.*;

/** SET-081 on the phone page (OD-17): the settings file downloads as an attachment and comes back as a checked upload. */
public class BackupPhoneTest {
    private final List<String> tv=new ArrayList<>();
    private final AtomicReference<SettingsBackup.Incoming> got=new AtomicReference<>();
    private final String file=SettingsBackup.export(Collections.singletonMap("reduceMotion",true),"2026-10-25");
    private PhoneFlows.Backup flow(){return new PhoneFlows.Backup(()->file,got::set,tv::add);}
    private static PhoneSetupServer.Request req(String method,Map<String,String> form){
        PhoneSetupServer.Request r=new PhoneSetupServer.Request(method,"/",new HashMap<>(),new HashMap<>(),form==null?new HashMap<>():form,null);r.origin="http://192.168.1.20:50000";r.session=r.origin+"/token";return r;
    }

    @Test public void theFirstPageOffersDownloadAndUploadWithoutScript()throws Exception{
        String html=flow().handle("",req("GET",null)).html;
        assertTrue(html.contains("/token/backup/file"));assertTrue(html.contains("enctype=\"multipart/form-data\""));assertTrue(html.contains("type=\"file\" name=\"file\""));
        assertFalse(html.contains("<script"));assertTrue(html.contains("never contains passwords, keys, the adult PIN"));
    }
    @Test public void downloadIsTheFileAndKeepsTheSessionOpen()throws Exception{
        PhoneSetupServer.Response r=flow().handle("backup/file",req("GET",null));
        assertEquals(file,r.html);assertEquals(SettingsBackup.FILE,r.filename);assertTrue(r.type.startsWith("application/json"));assertFalse("downloading never ends the session",r.success);
        assertEquals(1,tv.size());
    }
    @Test public void anUploadIsCheckedThenHandedToTheTv()throws Exception{
        PhoneSetupServer.Response bad=flow().handle("backup/restore",req("POST",Collections.singletonMap("file","{\"format\":\"nope\"}")));
        assertFalse(bad.success);assertTrue(bad.html.contains("isn't a Unified TV settings file"));assertNull(got.get());
        PhoneSetupServer.Response ok=flow().handle("backup/restore",req("POST",Collections.singletonMap("file",file)));
        assertTrue(ok.success);assertTrue("the phone never says restored",ok.html.contains("Nothing changes until an adult confirms"));
        assertEquals(true,got.get().values.get("reduceMotion"));
        assertEquals(303,flow().handle("backup/restore",req("GET",null)).status);
    }
    @Test public void multipartIsParsedStrictly(){
        String body="--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"unified-tv-settings.json\"\r\nContent-Type: application/json\r\n\r\n{\"a\":\"é\"}\r\n--XyZ\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\nhi\r\n--XyZ--\r\n";
        Map<String,String> m=PhoneSetupServer.multipart(body.getBytes(StandardCharsets.UTF_8),PhoneSetupServer.boundary("multipart/form-data; boundary=XyZ"));
        assertEquals("{\"a\":\"é\"}",m.get("file"));assertEquals("hi",m.get("note"));
        assertEquals("abc",PhoneSetupServer.boundary("multipart/form-data; boundary=\"abc\""));
        for(String broken:new String[]{"no delimiter","--XyZ\r\nContent-Disposition: form-data\r\n\r\nx\r\n--XyZ--","--XyZ\r\nContent-Disposition: form-data; name=\"f\"\r\n\r\nunterminated"})
            try{PhoneSetupServer.multipart(broken.getBytes(StandardCharsets.UTF_8),"XyZ");fail(broken);}catch(IllegalArgumentException expected){}
        try{PhoneSetupServer.boundary("multipart/form-data");fail();}catch(IllegalArgumentException expected){}
        try{PhoneSetupServer.Response.file("x","text/html","../../etc");fail("a path is never a file name");}catch(IllegalArgumentException expected){}
    }

    // --- through the real server on this machine
    private PhoneSetupServer server;
    @After public void stop(){if(server!=null)server.stop();}
    @Test public void overTheNetworkTheFileIsAnAttachmentAndTheUploadArrives()throws Exception{
        InetAddress loopback=InetAddress.getByName("127.0.0.1");
        server=new PhoneSetupServer(loopback,a->true,flow(),null,System::currentTimeMillis,new SecureRandom());server.start();
        String path=URI.create(server.link()).getPath(),host="127.0.0.1:"+server.port();
        String reply=send(loopback,"GET "+path+"backup/file HTTP/1.1\r\nHost: "+host+"\r\n\r\n",new byte[0]);
        assertTrue(reply,reply.startsWith("HTTP/1.1 200"));assertTrue(reply.contains("Content-Disposition: attachment; filename=\"unified-tv-settings.json\""));
        assertTrue(reply.contains("Content-Type: application/json; charset=utf-8"));assertTrue(reply.contains("Content-Security-Policy: default-src 'none'"));assertTrue(reply.endsWith(file));
        byte[] body=("--b0undary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"s.json\"\r\nContent-Type: application/json\r\n\r\n"+file+"\r\n--b0undary--\r\n").getBytes(StandardCharsets.UTF_8);
        String up=send(loopback,"POST "+path+"backup/restore HTTP/1.1\r\nHost: "+host+"\r\nContent-Type: multipart/form-data; boundary=b0undary\r\nContent-Length: "+body.length+"\r\n\r\n",body);
        assertTrue(up,up.startsWith("HTTP/1.1 200"));assertNotNull(got.get());
        server.await(3000);assertEquals("a usable file ends the session",PhoneSetupServer.End.SUCCEEDED,server.ended());
    }
    private String send(InetAddress to,String head,byte[] body)throws IOException{
        try(Socket s=new Socket()){s.connect(new InetSocketAddress(to,server.port()),2000);s.setSoTimeout(3000);
            OutputStream o=s.getOutputStream();o.write(head.getBytes(StandardCharsets.ISO_8859_1));o.write(body);o.flush();
            ByteArrayOutputStream all=new ByteArrayOutputStream();InputStream in=s.getInputStream();byte[] b=new byte[4096];int n;
            try{while((n=in.read(b))!=-1)all.write(b,0,n);}catch(SocketException|SocketTimeoutException ignored){}
            return all.toString("UTF-8");}
    }
}
