package io.unifiedtv.preview;
import org.json.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded local SSDP discovery and read-only ContentDirectory browsing. */
final class DlnaClient {
    static final class Device {String name,location,control,service,udn;JSONObject json()throws Exception{return new JSONObject().put("name",name).put("location",location).put("control",control).put("service",service).put("udn",udn).put("enabled",true);}}
    /** One entry. parent is the server's own parentID; ratings are its content ratings (upnp:rating), as sent. */
    static final class Item {String id,title,url="",art="",year="",parent="",upnpClass="";boolean folder;final List<String> ratings=new ArrayList<>();}
    /** One Browse response. Paging follows the server's own counts: filtered entries must not end the list early. */
    static final class Page {final List<Item> items=new ArrayList<>();int returned,total;
        boolean more(int offset){return returned>0&&(total>0?offset+returned<total:returned>=PAGE);}
        int next(int offset){return Math.max(0,offset)+returned;}
    }
    static final int PAGE=24;
    static URI local(String url)throws Exception{
        URI u=URI.create(url);if(!Arrays.asList("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null)throw new IOException("Invalid local service");
        InetAddress a=InetAddress.getByName(u.getHost());byte[] raw=a.getAddress();boolean ula=raw.length==16&&(raw[0]&0xfe)==0xfc;
        if(!(a.isSiteLocalAddress()||a.isLinkLocalAddress()||ula)||a.isLoopbackAddress()||a.isAnyLocalAddress())throw new IOException("Not a local server");return u;
    }
    /**
     * UPnP descriptions and DIDL-Lite never need a DOCTYPE, so any document declaring one (or an entity) is refused here,
     * before any parser sees it. This is the rule on every TV: Android's own parser rejects the Xerces hardening features
     * below with ParserConfigurationException, which made every media server look unreadable on the TCL (25 October 2026).
     */
    static Document xml(byte[] bytes)throws Exception{
        if(declaresDoctype(bytes))throw new IOException("DOCTYPE not allowed");
        return harden(DocumentBuilderFactory.newInstance()).newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    /** Namespace-aware, plus each extra protection the platform's parser supports; an unsupported one is skipped, never fatal. */
    static DocumentBuilderFactory harden(DocumentBuilderFactory f){
        f.setNamespaceAware(true);
        String[][] features={{"http://apache.org/xml/features/disallow-doctype-decl","true"},{"http://xml.org/sax/features/external-general-entities","false"},{"http://xml.org/sax/features/external-parameter-entities","false"}};
        for(String[] x:features)try{f.setFeature(x[0],Boolean.parseBoolean(x[1]));}catch(ParserConfigurationException|RuntimeException unsupported){}
        try{f.setExpandEntityReferences(false);}catch(RuntimeException unsupported){}
        return f;
    }
    /** "<!DOCTYPE" or "<!ENTITY" anywhere, in any case, with zero bytes dropped so UTF-16 text is caught too. */
    static boolean declaresDoctype(byte[] bytes){
        StringBuilder b=new StringBuilder(bytes.length);for(byte x:bytes)if(x!=0)b.append((char)(x&0xff));
        String s=b.toString().toLowerCase(Locale.ROOT);return s.contains("<!doctype")||s.contains("<!entity");
    }
    static String value(Element e,String name){NodeList n=e.getElementsByTagNameNS("*",name);return n.getLength()==0?"":n.item(0).getTextContent();}
    /** The home network under any VPN, set by the library screen; null uses the default route. */
    static volatile android.net.Network lan;
    /** Why a media server request failed, in words a viewer can act on. The message never carries an address. */
    static final class Failure extends IOException {
        static final int UNREACHABLE=1,REFUSED=2,MISSING=3,SERVER=4;
        final int kind;
        Failure(int kind){super("Media server request failed: "+kind);this.kind=kind;}
        /** HTTP status and, for a SOAP fault, the UPnP error code: 701/710 mean the object or folder no longer exists. */
        static int classify(int http,String fault){
            if(http==401||http==403)return REFUSED;
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("errorCode>\\s*(\\d+)").matcher(fault==null?"":fault);
            if(m.find()){int upnp=count(m.group(1));if(upnp==701||upnp==710)return MISSING;if(upnp==606)return REFUSED;}
            return SERVER;
        }
        static String plain(int kind){
            switch(kind){
                case UNREACHABLE:return "The media server isn't answering. Check that it is switched on and on your home network, then try again.";
                case REFUSED:return "The media server refused this TV. Check that sharing with this TV is allowed in the server's media settings.";
                case MISSING:return "This folder is no longer on the media server. It may have been moved, or the server was reset. An adult can choose the folders again.";
                default:return "The media server couldn't show this folder just now. Try again in a moment.";
            }
        }
    }
    static byte[] request(String url,String action,String body)throws Exception{
        URI uri=local(url);android.net.Network net=lan;HttpURLConnection c=(HttpURLConnection)(net!=null?net.openConnection(uri.toURL()):uri.toURL().openConnection());c.setInstanceFollowRedirects(false);c.setConnectTimeout(4000);c.setReadTimeout(6000);
        try{if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","text/xml; charset=utf-8");c.setRequestProperty("SOAPAction","\""+action+"\"");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();
            if(code!=200)throw new Failure(Failure.classify(code,code==500?fault(c):""));
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>4*1024*1024)throw new Failure(Failure.SERVER);out.write(b,0,n);}return out.toByteArray();}
        }catch(Failure f){throw f;}catch(IOException e){throw new Failure(Failure.UNREACHABLE);}
        finally{c.disconnect();}
    }
    /** The start of a SOAP fault body, enough to read its UPnP error code. */
    private static String fault(HttpURLConnection c){
        try(InputStream in=c.getErrorStream()){if(in==null)return "";byte[] b=new byte[4096];int total=0,n;while(total<b.length&&(n=in.read(b,total,b.length-total))!=-1)total+=n;return new String(b,0,total,StandardCharsets.UTF_8);}catch(IOException e){return "";}
    }
    static Device describe(String location)throws Exception{
        URI base=local(location);Element root=xml(request(location,null,null)).getDocumentElement();NodeList services=root.getElementsByTagNameNS("*","service");
        for(int i=0;i<services.getLength();i++){Element service=(Element)services.item(i);String type=value(service,"serviceType");if(type.matches("urn:schemas-upnp-org:service:ContentDirectory:[1-3]")){
            URI control=base.resolve(value(service,"controlURL"));local(control.toString());if(!base.getHost().equalsIgnoreCase(control.getHost()))throw new IOException("Different server");
            Device d=new Device();d.name=value(root,"friendlyName");if(d.name.length()>80)d.name=d.name.substring(0,80);d.control=control.toString();d.location=location;d.service=type;d.udn=value(root,"UDN");return d;
        }}throw new IOException("No media directory");
    }
    /**
     * What a search heard. Servers that answered but whose description could not be read, and servers whose top level
     * showed no folders at all (nothing to browse), are counted, never offered.
     */
    static final class Report {int answered,unreadable,empty;
        /** "Nobody answered", "a server answered but wouldn't talk to this TV" and "nothing shared" need different fixes. */
        String plain(){
            if(unreadable>0)return "A media server answered, but didn't let the TV see its library. Check that its media service is running and that this TV is allowed, then search again.";
            if(empty>0)return "A media server answered, but isn't sharing any folders yet. Check its media server settings, then search again.";
            return "No media server found. Check that the NAS media service is on and the TV is on the same home network. If NordVPN is on, allow devices on your home network.";
        }
    }
    /** One server that answered a search, and how far it got: a readable description, then a top level with folders. */
    static final class Check {final String location;Device device;int folders=-1;Check(String location){this.location=location;}}
    /** Zero-configuration rule: offered only when its description validated AND its top level shows at least one folder. */
    static List<Device> offered(List<Check> all,Report report){
        List<Device> out=new ArrayList<>();Set<String> ids=new HashSet<>();if(report!=null)report.answered=all.size();
        for(Check c:all){
            if(c.device==null){if(report!=null)report.unreadable++;continue;}
            if(c.folders<=0){if(report!=null)report.empty++;continue;}
            if(ids.add(c.device.udn.isEmpty()?c.device.control:c.device.udn))out.add(c.device);
        }
        return out;
    }
    /** Folders in a Browse page: the catalogue check counts what a viewer could open. */
    static int folders(Page page){int n=0;for(Item i:page.items)if(i.folder)n++;return n;}
    /** Friendly names only, never an address; two servers with one name are told apart by number. */
    static List<String> labels(List<Device> devices){
        List<String> out=new ArrayList<>();Map<String,Integer> seen=new HashMap<>();
        for(Device d:devices){String name=d.name==null||d.name.trim().isEmpty()?"Media server":d.name.trim();int n=seen.merge(name.toLowerCase(Locale.ROOT),1,Integer::sum);out.add(n==1?name:name+" ("+n+")");}
        return out;
    }
    static List<Device> discover(android.content.Context context,boolean thorough)throws Exception{return discover(context,thorough,null);}
    static List<Device> discover(android.content.Context context,boolean thorough,Report report)throws Exception{return offered(survey(context,thorough),report);}
    /** Every server that answered, each described and given a root Browse (bounded: eight servers, fifteen seconds). */
    static List<Check> survey(android.content.Context context,boolean thorough)throws Exception{
        Set<String> locations=new LinkedHashSet<>();try(DatagramSocket socket=new DatagramSocket()){LocalDiscovery.bind(context,socket);
            socket.setSoTimeout(500);byte[] message=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(message,message.length,InetAddress.getByName("239.255.255.250"),1900));listen(socket,locations,4500);
            // Multicast can be lost to a VPN or a router setting; UPnP devices also answer a search sent straight to them.
            if(locations.isEmpty()&&thorough&&context!=null){for(InetAddress a:LocalDiscovery.neighbours(LocalDiscovery.address(context)))try{socket.send(new DatagramPacket(message,message.length,a,1900));}catch(IOException ignored){}listen(socket,locations,3000);}
        }
        List<Check> out=new ArrayList<>();long deadline=System.currentTimeMillis()+15000;
        for(String location:locations){if(Thread.currentThread().isInterrupted()||System.currentTimeMillis()>deadline)break;Check c=new Check(location);out.add(c);
            try{c.device=describe(location);}catch(Exception ignored){continue;}
            try{c.folders=folders(browse(c.device.json(),"0",0));}catch(Exception ignored){c.folders=-1;}}
        return out;
    }
    private static void listen(DatagramSocket socket,Set<String> locations,long window){
        long end=System.currentTimeMillis()+window;while(System.currentTimeMillis()<end&&!Thread.currentThread().isInterrupted()){try{byte[] b=new byte[16384];DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);String location=location(new String(b,0,p.getLength(),StandardCharsets.UTF_8),p.getAddress());if(location!=null&&locations.size()<8)locations.add(location);}catch(SocketTimeoutException ignored){}catch(Exception ignored){}}
    }
    /** The LOCATION header of an SSDP answer, accepted only from the local host that sent it. */
    static String location(String reply,InetAddress from){
        for(String line:reply.split("\r?\n"))if(line.toLowerCase(Locale.ROOT).startsWith("location:")){try{String location=line.substring(9).trim();URI u=local(location);return InetAddress.getByName(u.getHost()).equals(from)?location:null;}catch(Exception e){return null;}}return null;
    }
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    static Page browse(JSONObject source,String id,int offset)throws Exception{return parse(browseResult(source,id,offset,PAGE),local(source.getString("control")));}
    /** One raw Browse response, for callers that read other kinds of item (the Wallpaper's pictures: PhotoSource). */
    static byte[] browseResult(JSONObject source,String id,int offset,int count)throws Exception{return browseResult(source,id,offset,count,"BrowseDirectChildren");}
    /**
     * One object's own entry (BrowseMetadata): its parentID, ratings and stream. The Kids check at play time walks these
     * up to a Kids folder. Null when the server returns nothing playable or browsable for that id.
     */
    static Item metadata(JSONObject source,String id)throws Exception{
        Page p=parse(browseResult(source,id,0,0,"BrowseMetadata"),local(source.getString("control")));return p.items.isEmpty()?null:p.items.get(0);
    }
    private static byte[] browseResult(JSONObject source,String id,int offset,int count,String flag)throws Exception{
        String service=source.getString("service");if(!service.matches("urn:schemas-upnp-org:service:ContentDirectory:[1-3]"))throw new IOException("Unsupported media service");
        String body="<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:Browse xmlns:u=\""+service+"\"><ObjectID>"+escape(id)+"</ObjectID><BrowseFlag>"+flag+"</BrowseFlag><Filter>*</Filter><StartingIndex>"+Math.max(0,offset)+"</StartingIndex><RequestedCount>"+(flag.equals("BrowseMetadata")?0:Math.max(1,Math.min(200,count)))+"</RequestedCount><SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>";
        return request(source.getString("control"),service+"#Browse",body);
    }
    /**
     * A picture straight into a file, never into memory: camera originals can be far larger than request()'s 4 MB. Local
     * servers only, no redirects, and a hard byte limit; a partial file is deleted.
     */
    static void download(String url,File out,long max)throws Exception{
        URI uri=local(url);android.net.Network net=lan;HttpURLConnection c=(HttpURLConnection)(net!=null?net.openConnection(uri.toURL()):uri.toURL().openConnection());c.setInstanceFollowRedirects(false);c.setConnectTimeout(4000);c.setReadTimeout(8000);
        boolean done=false;
        try{int code=c.getResponseCode();if(code!=200)throw new Failure(Failure.classify(code,""));
            long length=c.getContentLengthLong();if(length>max)throw new Failure(Failure.SERVER);
            try(InputStream in=c.getInputStream();OutputStream o=new FileOutputStream(out)){byte[] b=new byte[16384];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>max)throw new Failure(Failure.SERVER);if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();o.write(b,0,n);}}
            done=true;
        }catch(Failure f){throw f;}catch(IOException e){throw new Failure(Failure.UNREACHABLE);}
        finally{c.disconnect();if(!done)out.delete();}
    }
    /** The same server (matched by its UDN, never by name) at a new local address, or null. */
    static JSONObject relocate(android.content.Context context,JSONObject from)throws Exception{
        String udn=from.optString("udn");if(udn.isEmpty())return null;
        for(Device d:discover(context,false)){
            if(udn.equals(d.udn)&&!d.control.equals(from.optString("control"))&&d.service.equals(from.optString("service"))){JSONObject copy=new JSONObject(from.toString());copy.put("control",d.control).put("location",d.location);return copy;}
        }
        return null;
    }
    /** Same-host local video resources and artwork only; everything else on the page is counted but not shown. */
    static Page parse(byte[] soap,URI origin)throws Exception{
        Element envelope=xml(soap).getDocumentElement();Page page=new Page();page.returned=count(value(envelope,"NumberReturned"));page.total=count(value(envelope,"TotalMatches"));
        String result=value(envelope,"Result");if(result.isEmpty())return page;Element didl=xml(result.getBytes(StandardCharsets.UTF_8)).getDocumentElement();
        for(Node node=didl.getFirstChild();node!=null&&page.items.size()<PAGE;node=node.getNextSibling()){if(!(node instanceof Element))continue;Element e=(Element)node;Item item=new Item();item.id=e.getAttribute("id");item.title=title(value(e,"title"));item.folder="container".equals(e.getLocalName());if(item.id.isEmpty()||item.id.length()>1024)continue;
            if(!item.folder){NodeList resources=e.getElementsByTagNameNS("*","res");for(int i=0;i<resources.getLength();i++){Element res=(Element)resources.item(i);String protocol=res.getAttribute("protocolInfo");if(protocol.startsWith("http-get:")&&protocol.contains("video/")){String u=sameHost(origin,res.getTextContent());if(!u.isEmpty()){item.url=u;break;}}}if(item.url.isEmpty())continue;}
            item.art=sameHost(origin,value(e,"albumArtURI"));String date=value(e,"date");if(date.matches("\\d{4}.*"))item.year=date.substring(0,4);
            item.parent=e.getAttribute("parentID");if(item.parent.length()>1024)item.parent="";item.upnpClass=value(e,"class").trim();
            // upnp:rating is the content rating (UPnP ContentDirectory); star scores live in other fields and are not read.
            NodeList ratings=e.getElementsByTagNameNS("*","rating");for(int i=0;i<ratings.getLength()&&item.ratings.size()<4;i++){String r=ratings.item(i).getTextContent().trim();if(!r.isEmpty())item.ratings.add(r.length()>32?r.substring(0,32):r);}
            page.items.add(item);
        }if(page.returned==0)page.returned=page.items.size();return page;
    }
    private static final java.util.regex.Pattern VIDEO_FILE=java.util.regex.Pattern.compile("(?i)\\.(mkv|mp4|m4v|avi|ts|m2ts|mov|wmv|mpg|mpeg|webm)$");
    /**
     * Servers often send the file name as the title. A trailing video extension is removed, and dots or underscores become spaces,
     * only when the title looks like a file name (an extension, or no spaces with words joined by dots or underscores). "Mr. Smith" and "S.W.A.T." are left alone.
     */
    static String title(String raw){
        String t=raw==null?"":raw.trim();if(t.isEmpty())return t;
        java.util.regex.Matcher ext=VIDEO_FILE.matcher(t);boolean file=ext.find()&&ext.start()>0;
        String stem=file?t.substring(0,ext.start()):t;
        boolean joined=!stem.contains(" ")&&stem.matches(".*[\\p{L}\\d]{2,}[._][\\p{L}\\d].*");
        if(file||joined){if(!stem.contains(" ")||stem.contains("_"))stem=stem.replaceAll("[._]+"," ");stem=stem.replaceAll("\\s+"," ").trim();}
        return stem.isEmpty()?t:stem;
    }
    static int count(String s){try{return Math.max(0,Integer.parseInt(s.trim()));}catch(Exception e){return 0;}}
    static String sameHost(URI origin,String ref){try{if(ref==null||ref.trim().isEmpty())return "";URI u=origin.resolve(ref.trim());local(u.toString());return origin.getHost().equalsIgnoreCase(u.getHost())?u.toString():"";}catch(Exception e){return "";}}
}
