package io.unifiedtv.preview;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A tiny HTTP server on 127.0.0.1 for tests that need a real socket (a fake Home Assistant, a fake TMDB). Android's
 * unit-test classpath has no com.sun.net.httpserver, so this is plain sockets: one request per connection, then close.
 */
final class MiniHttp implements Closeable {
    static final class Request {String method,path,query,body;final Map<String,String> headers=new HashMap<>();String header(String n){return headers.get(n.toLowerCase(Locale.ROOT));}}
    static final class Response {final int status;final String body;final Map<String,String> headers=new LinkedHashMap<>();Response(int status,String body){this.status=status;this.body=body==null?"":body;}Response header(String n,String v){headers.put(n,v);return this;}}
    interface Handler {Response handle(Request r)throws Exception;}
    private final ServerSocket socket;private final Thread thread;
    MiniHttp(Handler handler)throws IOException{
        socket=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
        thread=new Thread(()->{while(!socket.isClosed())try(Socket s=socket.accept()){serve(s,handler);}catch(Exception ignored){}},"mini-http");thread.setDaemon(true);thread.start();
    }
    String base(){return "http://127.0.0.1:"+socket.getLocalPort();}
    private static void serve(Socket s,Handler handler)throws Exception{
        s.setSoTimeout(5000);InputStream in=new BufferedInputStream(s.getInputStream());Request r=new Request();
        String[] first=line(in).split(" ");r.method=first[0];String target=first.length>1?first[1]:"/";int q=target.indexOf('?');r.path=q<0?target:target.substring(0,q);r.query=q<0?null:target.substring(q+1);
        for(String l;!(l=line(in)).isEmpty();){int c=l.indexOf(':');if(c>0)r.headers.put(l.substring(0,c).trim().toLowerCase(Locale.ROOT),l.substring(c+1).trim());}
        int length=r.header("content-length")==null?0:Integer.parseInt(r.header("content-length"));byte[] b=new byte[length];for(int n=0;n<length;){int k=in.read(b,n,length-n);if(k<0)break;n+=k;}r.body=new String(b,StandardCharsets.UTF_8);
        Response out=handler.handle(r);byte[] body=out.body.getBytes(StandardCharsets.UTF_8);
        StringBuilder h=new StringBuilder("HTTP/1.1 ").append(out.status).append(" X\r\nContent-Length: ").append(body.length).append("\r\nConnection: close\r\n");
        for(Map.Entry<String,String> e:out.headers.entrySet())h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        OutputStream o=s.getOutputStream();o.write(h.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII));o.write(body);o.flush();
    }
    private static String line(InputStream in)throws IOException{StringBuilder b=new StringBuilder();for(int c;(c=in.read())!=-1&&c!='\n';)if(c!='\r')b.append((char)c);return b.toString();}
    @Override public void close(){try{socket.close();}catch(IOException ignored){}}
}
