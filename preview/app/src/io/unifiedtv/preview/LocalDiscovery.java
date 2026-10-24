package io.unifiedtv.preview;
import android.content.Context;
import android.net.*;
import android.net.nsd.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Finds household servers without anyone typing an address. Home network only, bounded in hosts, time and bytes. */
final class LocalDiscovery {
    static final class Found {final String name,base;Found(String name,String base){this.name=name;this.base=base;}}
    /** The Wi-Fi/Ethernet network under any VPN: discovery must reach the home network, not the tunnel. */
    static Network lan(Context c){
        try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return null;
            for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))return n;}
        }catch(RuntimeException ignored){}return null;
    }
    /** Best effort: a VPN that forbids bypass refuses this, and discovery then uses the default route as before. */
    static void bind(Context c,DatagramSocket socket){Network n=c==null?null:lan(c);if(n!=null)try{n.bindSocket(socket);}catch(Exception ignored){}}
    static HttpURLConnection open(Context c,URL url)throws IOException{Network n=c==null?null:lan(c);return (HttpURLConnection)(n!=null?n.openConnection(url):url.openConnection());}
    /** The TV's own private IPv4 address on the home network, or null. */
    static Inet4Address address(Context c){
        try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=lan(c);LinkProperties link=n==null||cm==null?null:cm.getLinkProperties(n);
            if(link!=null)for(LinkAddress a:link.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&a.getAddress().isSiteLocalAddress())return (Inet4Address)a.getAddress();
        }catch(RuntimeException ignored){}return null;
    }
    /** The other addresses of the TV's /24: at most 253 hosts, never outside private address space. */
    static List<InetAddress> neighbours(Inet4Address self){
        List<InetAddress> out=new ArrayList<>();if(self==null||!self.isSiteLocalAddress())return out;byte[] b=self.getAddress();
        for(int i=1;i<255;i++){if(i==(b[3]&0xff))continue;try{out.add(InetAddress.getByAddress(new byte[]{b[0],b[1],b[2],(byte)i}));}catch(UnknownHostException ignored){}}return out;
    }
    // --- Home Assistant
    /** Home Assistant serves a public web manifest naming itself; nothing else on 8123 is mistaken for it. */
    static boolean homeAssistant(String manifest){try{return "Home Assistant".equals(new JSONObject(manifest).optString("name"));}catch(Exception e){return false;}}
    static String fetch(Context c,String url)throws IOException{
        HttpURLConnection h=open(c,new URL(url));h.setInstanceFollowRedirects(false);h.setConnectTimeout(700);h.setReadTimeout(1500);
        try{if(h.getResponseCode()!=200)throw new IOException();try(InputStream in=h.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Too large");out.write(b,0,n);}return out.toString("UTF-8");}}finally{h.disconnect();}
    }
    /** mDNS, then the standard homeassistant.local name, then (thorough only) port 8123 on the TV's own /24. */
    static List<Found> homeAssistant(Context c,boolean thorough){
        Map<String,Found> found=Collections.synchronizedMap(new LinkedHashMap<>());
        for(Found f:mdns(c,"_home-assistant._tcp.",5000))found.put(f.base,f);if(!found.isEmpty())return new ArrayList<>(found.values());
        for(InetAddress a:named("homeassistant.local"))if(a instanceof Inet4Address&&a.isSiteLocalAddress())probeHa(c,a,found);
        if(!found.isEmpty()||!thorough)return new ArrayList<>(found.values());
        sweep(neighbours(address(c)),a->{try(Socket s=new Socket()){Network n=lan(c);if(n!=null)try{n.bindSocket(s);}catch(Exception ignored){}s.connect(new InetSocketAddress(a,8123),300);}catch(Exception e){return;}probeHa(c,a,found);});
        return new ArrayList<>(found.values());
    }
    /** A .local lookup can hang on firmware without mDNS name resolution: give it three seconds. */
    static List<InetAddress> named(String host){ExecutorService one=Executors.newSingleThreadExecutor();
        try{return Arrays.asList(one.submit(()->InetAddress.getAllByName(host)).get(3,TimeUnit.SECONDS));}catch(Exception e){return Collections.emptyList();}finally{one.shutdownNow();}}
    private static void probeHa(Context c,InetAddress a,Map<String,Found> found){
        try{String base=Endpoint.base(a.getHostAddress(),"8123",false);if(found.size()<4&&homeAssistant(fetch(c,base+"/manifest.json")))found.put(base,new Found("Home Assistant",base));}catch(Exception ignored){}
    }
    /** Standard DNS-SD for a window. Older Android NSD fails overlapping resolves, so they run one at a time, at most eight. */
    static List<Found> mdns(Context c,String type,long window){
        List<Found> out=Collections.synchronizedList(new ArrayList<>());NsdManager nsd=(NsdManager)c.getSystemService(Context.NSD_SERVICE);if(nsd==null)return out;
        ArrayDeque<NsdServiceInfo> pending=new ArrayDeque<>();boolean[] resolving={false};
        Runnable[] next=new Runnable[1];next[0]=()->{NsdServiceInfo info;synchronized(pending){if(resolving[0]||pending.isEmpty())return;resolving[0]=true;info=pending.poll();}
            Runnable done=()->{synchronized(pending){resolving[0]=false;}next[0].run();};
            try{nsd.resolveService(info,new NsdManager.ResolveListener(){
                public void onResolveFailed(NsdServiceInfo s,int error){done.run();}
                public void onServiceResolved(NsdServiceInfo s){try{if(s.getHost()!=null&&s.getHost().isSiteLocalAddress())out.add(new Found(s.getServiceName(),Endpoint.base(s.getHost().getHostAddress(),""+s.getPort(),false)));}catch(Exception ignored){}done.run();}
            });}catch(RuntimeException e){done.run();}};
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}
            public void onServiceFound(NsdServiceInfo info){synchronized(pending){if(pending.size()>=8)return;pending.add(info);}next[0].run();}
            public void onServiceLost(NsdServiceInfo info){}
            public void onDiscoveryStopped(String t){}
            public void onStartDiscoveryFailed(String t,int error){}
            public void onStopDiscoveryFailed(String t,int error){}
        };
        try{nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);Thread.sleep(window);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(RuntimeException ignored){}
        finally{try{nsd.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}}
        synchronized(out){return new ArrayList<>(out);}
    }
    // --- Jellyfin
    /** Jellyfin's own discovery: a UDP question on 7359, a JSON answer naming the server and its address. */
    static Found jellyfinReply(String reply,InetAddress from){
        try{JSONObject j=new JSONObject(reply);URI u=DlnaClient.local(j.getString("Address"));if(!InetAddress.getByName(u.getHost()).equals(from))return null;
            String base=Endpoint.base(u.getHost(),""+(u.getPort()>0?u.getPort():8096),"https".equals(u.getScheme()));String name=j.optString("Name","Media server");return new Found(name.length()>80?name.substring(0,80):name,base);
        }catch(Exception e){return null;}
    }
    static List<Found> jellyfin(Context c){
        Map<String,Found> found=new LinkedHashMap<>();
        try(DatagramSocket socket=new DatagramSocket()){bind(c,socket);socket.setBroadcast(true);socket.setSoTimeout(400);byte[] ask="Who is JellyfinServer?".getBytes(StandardCharsets.US_ASCII);
            List<InetAddress> targets=new ArrayList<>();targets.add(InetAddress.getByName("255.255.255.255"));Inet4Address self=address(c);if(self!=null){byte[] b=self.getAddress();targets.add(InetAddress.getByAddress(new byte[]{b[0],b[1],b[2],(byte)255}));}
            for(InetAddress t:targets)try{socket.send(new DatagramPacket(ask,ask.length,t,7359));}catch(IOException ignored){}
            long end=System.currentTimeMillis()+3000;while(System.currentTimeMillis()<end&&found.size()<8&&!Thread.currentThread().isInterrupted()){try{byte[] b=new byte[4096];DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);Found f=jellyfinReply(new String(b,0,p.getLength(),StandardCharsets.UTF_8),p.getAddress());if(f!=null)found.put(f.base,f);}catch(SocketTimeoutException ignored){}}
        }catch(Exception ignored){}
        return new ArrayList<>(found.values());
    }
    // --- quiet look-ahead: what's on the home network is ready before anyone opens setup
    static volatile List<Found> haFound,mediaFound;static volatile List<DlnaClient.Device> nasFound;private static volatile long foundAt;private static boolean started;
    static boolean fresh(){return foundAt>0&&System.currentTimeMillis()-foundAt<10*60_000;}
    /** Once per process, light methods only (no address sweep), only for what isn't set up yet. Kept in memory, never stored. */
    static synchronized void prefetch(Context context){
        if(started)return;started=true;Context c=context.getApplicationContext();
        Thread look=new Thread(()->{Vault v=new Vault(c);
            try{if(v.read("ha").optString("base").isEmpty())haFound=homeAssistant(c,false);}catch(Exception ignored){}
            try{if(!v.read("dlna").optBoolean("enabled"))nasFound=DlnaClient.discover(c,false);}catch(Exception ignored){}
            try{if(v.read(Vault.mediaKey(c)).optString("base").isEmpty())mediaFound=jellyfin(c);}catch(Exception ignored){}
            foundAt=System.currentTimeMillis();},"home-network");look.setDaemon(true);look.start();
    }
    // --- shared
    interface Probe{void run(InetAddress a);}
    /** 32 at a time, at most 10 seconds for the whole /24; an interrupted search stops promptly. */
    static void sweep(List<InetAddress> hosts,Probe probe){
        if(hosts.isEmpty())return;ExecutorService pool=Executors.newFixedThreadPool(32);
        try{for(InetAddress a:hosts)pool.execute(()->probe.run(a));pool.shutdown();pool.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{pool.shutdownNow();}
    }
}
