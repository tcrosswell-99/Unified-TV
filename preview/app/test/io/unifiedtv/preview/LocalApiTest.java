package io.unifiedtv.preview;
import org.junit.*;
import static org.junit.Assert.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalApiTest {
    private java.net.ServerSocket server;private Thread serving;private JSONObject config;private AtomicInteger redirected;
    @Before public void start()throws Exception{
        server=new java.net.ServerSocket(0,10,InetAddress.getByName("127.0.0.1")); redirected=new AtomicInteger();
        serving=new Thread(()->{
            while(!server.isClosed())try(java.net.Socket socket=server.accept()){
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                String request=reader.readLine(),line,token="";
                while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("x-emby-token:"))token=line.substring(line.indexOf(':')+1).trim();
                String path=request.split(" ")[1];int code=200;String headers="";byte[] body="{}".getBytes(StandardCharsets.UTF_8);
                if(path.equals("/auth"))body=("{\"ok\":"+"private-test-token".equals(token)+"}").getBytes(StandardCharsets.UTF_8);
                if(path.equals("/redirect")){code=302;headers="Location: /target\r\n";}
                if(path.equals("/target"))redirected.incrementAndGet();
                if(path.equals("/unauthorised"))code=401;
                if(path.equals("/large"))body=new byte[LocalApi.LIMIT+1];
                OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+code+" Result\r\n"+headers+"Content-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(body);out.flush();
            }catch(Exception ignored){}
        });serving.setDaemon(true);serving.start();
        config=new JSONObject().put("base","http://127.0.0.1:"+server.getLocalPort()).put("enabled",true).put("httpConsent",true).put("token","private-test-token").put("kind","media");
    }
    @After public void stop()throws Exception{server.close();serving.join(2000);}
    @Test public void sendsAuthOnlyAsHeader()throws Exception{assertTrue(LocalApi.get(config,"/auth").getBoolean("ok"));}
    @Test public void refusesRedirectWithoutFollowing()throws Exception{LocalApi.ApiError e=assertThrows(LocalApi.ApiError.class,()->LocalApi.get(config,"/redirect"));assertEquals(302,e.code);assertEquals(0,redirected.get());}
    @Test public void consentIsRequiredOnEveryRequest()throws Exception{config.put("httpConsent",false);assertThrows(java.io.IOException.class,()->LocalApi.get(config,"/auth"));}
    @Test public void disabledConnectionCannotMakeRequests()throws Exception{config.put("enabled",false);assertThrows(java.io.IOException.class,()->LocalApi.get(config,"/auth"));}
    @Test public void preservesAuthErrorWithoutLeakingToken(){LocalApi.ApiError e=assertThrows(LocalApi.ApiError.class,()->LocalApi.get(config,"/unauthorised"));assertEquals(401,e.code);assertFalse(e.toString().contains("private-test-token"));}
    @Test public void responseIsBounded(){assertThrows(java.io.IOException.class,()->LocalApi.bytes(config,"/large"));}
}
