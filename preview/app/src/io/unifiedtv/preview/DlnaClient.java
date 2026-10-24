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
    static final class Item {String id,title,url="",art="",year="";boolean folder;}
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
    static Document xml(byte[] bytes)throws Exception{
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setExpandEntityReferences(false);return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    static String value(Element e,String name){NodeList n=e.getElementsByTagNameNS("*",name);return n.getLength()==0?"":n.item(0).getTextContent();}
    /** The home network under any VPN, set by the library screen; null uses the default route. */
    static volatile android.net.Network lan;
    static byte[] request(String url,String action,String body)throws Exception{
        URI uri=local(url);android.net.Network net=lan;HttpURLConnection c=(HttpURLConnection)(net!=null?net.openConnection(uri.toURL()):uri.toURL().openConnection());c.setInstanceFollowRedirects(false);c.setConnectTimeout(4000);c.setReadTimeout(6000);
        try{if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","text/xml; charset=utf-8");c.setRequestProperty("SOAPAction","\""+action+"\"");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
            if(c.getResponseCode()!=200)throw new IOException("Local service unavailable");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("Response too large");out.write(b,0,n);}return out.toByteArray();}
        }finally{c.disconnect();}
    }
    static Device describe(String location)throws Exception{
        URI base=local(location);Element root=xml(request(location,null,null)).getDocumentElement();NodeList services=root.getElementsByTagNameNS("*","service");
        for(int i=0;i<services.getLength();i++){Element service=(Element)services.item(i);String type=value(service,"serviceType");if(type.matches("urn:schemas-upnp-org:service:ContentDirectory:[1-3]")){
            URI control=base.resolve(value(service,"controlURL"));local(control.toString());if(!base.getHost().equalsIgnoreCase(control.getHost()))throw new IOException("Different server");
            Device d=new Device();d.name=value(root,"friendlyName");if(d.name.length()>80)d.name=d.name.substring(0,80);d.control=control.toString();d.location=location;d.service=type;d.udn=value(root,"UDN");return d;
        }}throw new IOException("No media directory");
    }
    static List<Device> discover(android.content.Context context,boolean thorough)throws Exception{
        Set<String> locations=new LinkedHashSet<>();try(DatagramSocket socket=new DatagramSocket()){LocalDiscovery.bind(context,socket);
            socket.setSoTimeout(500);byte[] message=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(message,message.length,InetAddress.getByName("239.255.255.250"),1900));listen(socket,locations,4500);
            // Multicast can be lost to a VPN or a router setting; UPnP devices also answer a search sent straight to them.
            if(locations.isEmpty()&&thorough&&context!=null){for(InetAddress a:LocalDiscovery.neighbours(LocalDiscovery.address(context)))try{socket.send(new DatagramPacket(message,message.length,a,1900));}catch(IOException ignored){}listen(socket,locations,3000);}
        }
        List<Device> devices=new ArrayList<>();Set<String> ids=new HashSet<>();long deadline=System.currentTimeMillis()+15000;for(String location:locations){if(Thread.currentThread().isInterrupted()||System.currentTimeMillis()>deadline)break;try{Device d=describe(location);if(ids.add(d.udn.isEmpty()?d.control:d.udn))devices.add(d);}catch(Exception ignored){}}return devices;
    }
    private static void listen(DatagramSocket socket,Set<String> locations,long window){
        long end=System.currentTimeMillis()+window;while(System.currentTimeMillis()<end&&!Thread.currentThread().isInterrupted()){try{byte[] b=new byte[16384];DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);String location=location(new String(b,0,p.getLength(),StandardCharsets.UTF_8),p.getAddress());if(location!=null&&locations.size()<8)locations.add(location);}catch(SocketTimeoutException ignored){}catch(Exception ignored){}}
    }
    /** The LOCATION header of an SSDP answer, accepted only from the local host that sent it. */
    static String location(String reply,InetAddress from){
        for(String line:reply.split("\r?\n"))if(line.toLowerCase(Locale.ROOT).startsWith("location:")){try{String location=line.substring(9).trim();URI u=local(location);return InetAddress.getByName(u.getHost()).equals(from)?location:null;}catch(Exception e){return null;}}return null;
    }
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    static Page browse(JSONObject source,String id,int offset)throws Exception{
        String service=source.getString("service");if(!service.matches("urn:schemas-upnp-org:service:ContentDirectory:[1-3]"))throw new IOException("Unsupported media service");
        String body="<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:Browse xmlns:u=\""+service+"\"><ObjectID>"+escape(id)+"</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter><StartingIndex>"+Math.max(0,offset)+"</StartingIndex><RequestedCount>"+PAGE+"</RequestedCount><SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>";
        return parse(request(source.getString("control"),service+"#Browse",body),local(source.getString("control")));
    }
    /** Same-host local video resources and artwork only; everything else on the page is counted but not shown. */
    static Page parse(byte[] soap,URI origin)throws Exception{
        Element envelope=xml(soap).getDocumentElement();Page page=new Page();page.returned=count(value(envelope,"NumberReturned"));page.total=count(value(envelope,"TotalMatches"));
        String result=value(envelope,"Result");if(result.isEmpty())return page;Element didl=xml(result.getBytes(StandardCharsets.UTF_8)).getDocumentElement();
        for(Node node=didl.getFirstChild();node!=null&&page.items.size()<PAGE;node=node.getNextSibling()){if(!(node instanceof Element))continue;Element e=(Element)node;Item item=new Item();item.id=e.getAttribute("id");item.title=value(e,"title");item.folder="container".equals(e.getLocalName());if(item.id.isEmpty()||item.id.length()>1024)continue;
            if(!item.folder){NodeList resources=e.getElementsByTagNameNS("*","res");for(int i=0;i<resources.getLength();i++){Element res=(Element)resources.item(i);String protocol=res.getAttribute("protocolInfo");if(protocol.startsWith("http-get:")&&protocol.contains("video/")){String u=sameHost(origin,res.getTextContent());if(!u.isEmpty()){item.url=u;break;}}}if(item.url.isEmpty())continue;}
            item.art=sameHost(origin,value(e,"albumArtURI"));String date=value(e,"date");if(date.matches("\\d{4}.*"))item.year=date.substring(0,4);
            page.items.add(item);
        }if(page.returned==0)page.returned=page.items.size();return page;
    }
    static int count(String s){try{return Math.max(0,Integer.parseInt(s.trim()));}catch(Exception e){return 0;}}
    static String sameHost(URI origin,String ref){try{if(ref==null||ref.trim().isEmpty())return "";URI u=origin.resolve(ref.trim());local(u.toString());return origin.getHost().equalsIgnoreCase(u.getHost())?u.toString():"";}catch(Exception e){return "";}}
}
