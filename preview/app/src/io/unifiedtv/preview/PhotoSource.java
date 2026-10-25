package io.unifiedtv.preview;
import org.json.*;
import org.w3c.dom.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * My Photos, Phase 1 (docs/design/MY_PHOTOS.md; spec 33.2–33.4): pictures from the owner's chosen DLNA folders, read
 * with the same client as the film folders. Pure rules only, so they can be tested without a NAS: which DIDL-Lite
 * entries are pictures, which of a picture's renditions to fetch, and a walk that never leaves the chosen folders.
 * DLNA has no accounts: anything here is visible to the whole home network, which Settings says on screen.
 */
final class PhotoSource {
    private PhotoSource(){}
    /** Never fetch or keep more than a TV screen's worth of picture: 1920 × 1080, either way up. */
    static final int MAX_LONG=1920,MAX_SHORT=1080;
    /** A rendition this big already fills the screen well; smaller ones (thumbnails) look soft at TV size. */
    static final int GOOD_LONG=960;
    /** A file larger than this is never downloaded (a raw or panorama original): the picture is skipped. */
    static final long MAX_BYTES=48L*1024*1024;
    /** Walk limits: enough for a long slideshow, small enough that a huge library can't keep the NAS busy. */
    static final int PAGE=100,MAX_PHOTOS=500,MAX_REQUESTS=60,MAX_DEPTH=6;
    /** Formats the TV's own decoder reads (HEIF from Android 9, the minimum here). */
    static final Set<String> MIMES=new HashSet<>(Arrays.asList("image/jpeg","image/jpg","image/png","image/webp","image/bmp","image/gif","image/heif","image/heic"));

    static final class Rendition {
        final String url,mime;final int w,h;final long bytes;
        Rendition(String url,String mime,int w,int h,long bytes){this.url=url;this.mime=mime;this.w=Math.max(0,w);this.h=Math.max(0,h);this.bytes=Math.max(0,bytes);}
        boolean known(){return w>0&&h>0;}
        long area(){return (long)w*h;}
    }
    static final class Photo {
        final String id,url;final int w,h;
        Photo(String id,Rendition r){this.id=id;this.url=r.url;this.w=r.w;this.h=r.h;}
    }
    /** One Browse response: picture folders to open and pictures to show. Paging follows the server's counts, as for films. */
    static final class Page {
        final List<DlnaClient.Item> folders=new ArrayList<>();final List<Photo> photos=new ArrayList<>();int returned,total;
        boolean more(int offset,int requested){return returned>0&&(total>0?offset+returned<total:returned>=requested);}
        int next(int offset){return Math.max(0,offset)+returned;}
    }

    static boolean fits(int w,int h){return Math.max(w,h)<=MAX_LONG&&Math.min(w,h)<=MAX_SHORT;}
    /** The decoder's power-of-two step that brings a picture inside the screen, so memory stays at most one screen's worth. */
    static int sampleSize(int w,int h){int s=1;while(w>0&&h>0&&!fits(w/s,h/s))s*=2;return s;}

    /**
     * Which rendition to fetch. Media servers often list the original and smaller copies (DLNA JPEG_SM, JPEG_MED…):
     * 1. the largest copy that fits the screen and is big enough to fill it;
     * 2. otherwise the smallest larger copy, decoded down (less to download than the biggest original);
     * 3. otherwise one of unknown size, usually the original, decoded down;
     * 4. otherwise the largest small copy, rather than nothing.
     * Copies over MAX_BYTES are never chosen.
     */
    static Rendition choose(List<Rendition> all){
        List<Rendition> ok=new ArrayList<>();for(Rendition r:all)if(r!=null&&!r.url.isEmpty()&&r.bytes<=MAX_BYTES)ok.add(r);
        Rendition best=null;
        for(Rendition r:ok)if(r.known()&&fits(r.w,r.h)&&Math.max(r.w,r.h)>=GOOD_LONG&&(best==null||r.area()>best.area()))best=r;
        if(best!=null)return best;
        for(Rendition r:ok)if(r.known()&&!fits(r.w,r.h)&&(best==null||r.area()<best.area()))best=r;
        if(best!=null)return best;
        for(Rendition r:ok)if(!r.known())return r;
        for(Rendition r:ok)if(best==null||r.area()>best.area())best=r;
        return best;
    }

    /** "1920x1080" from a res element's resolution attribute; {0,0} when absent or nonsense. */
    static int[] resolution(String s){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\s*(\\d{1,5})\\s*[xX×]\\s*(\\d{1,5})\\s*").matcher(s==null?"":s);
        return m.matches()?new int[]{DlnaClient.count(m.group(1)),DlnaClient.count(m.group(2))}:new int[]{0,0};
    }
    /** DLNA's image profiles state a maximum size, which is enough to know a copy fits; LRG and unknown profiles don't. */
    static int[] profile(String protocolInfo){
        String pn=protocolInfo==null?"":protocolInfo.toUpperCase(Locale.ROOT);
        if(pn.contains("DLNA.ORG_PN=JPEG_TN")||pn.contains("DLNA.ORG_PN=PNG_TN"))return new int[]{160,160};
        if(pn.contains("DLNA.ORG_PN=JPEG_SM"))return new int[]{640,480};
        if(pn.contains("DLNA.ORG_PN=JPEG_MED"))return new int[]{1024,768};
        return new int[]{0,0};
    }
    /** A folder that may hold pictures. Music and video groupings that some servers add at the top are left out. */
    static boolean pictureFolder(String upnpClass){
        String c=upnpClass==null?"":upnpClass.toLowerCase(Locale.ROOT);
        return !(c.contains("music")||c.contains("audio")||c.contains("video")||c.contains("movie")||c.contains("playlist"));
    }

    /**
     * Pictures and folders from one Browse response. A picture is an item of class object.item.imageItem (or with no class,
     * as some servers send) with at least one http-get image resource on the same local server; everything else is left out.
     */
    static Page parse(byte[] soap,URI origin)throws Exception{return parse(soap,ref->DlnaClient.sameHost(origin,ref));}
    /** As above, with the address check passed in: the app always passes DlnaClient.sameHost (same local server only). */
    static Page parse(byte[] soap,java.util.function.UnaryOperator<String> sameHost)throws Exception{
        Element envelope=DlnaClient.xml(soap).getDocumentElement();Page page=new Page();
        page.returned=DlnaClient.count(DlnaClient.value(envelope,"NumberReturned"));page.total=DlnaClient.count(DlnaClient.value(envelope,"TotalMatches"));
        String result=DlnaClient.value(envelope,"Result");if(result.isEmpty())return page;
        Element didl=DlnaClient.xml(result.getBytes(StandardCharsets.UTF_8)).getDocumentElement();int seen=0;
        for(Node node=didl.getFirstChild();node!=null&&seen<PAGE*2;node=node.getNextSibling()){
            if(!(node instanceof Element))continue;Element e=(Element)node;seen++;
            String id=e.getAttribute("id"),cls=DlnaClient.value(e,"class").trim();if(id.isEmpty()||id.length()>1024)continue;
            if("container".equals(e.getLocalName())){
                if(!pictureFolder(cls))continue;
                DlnaClient.Item f=new DlnaClient.Item();f.id=id;f.folder=true;f.title=DlnaClient.value(e,"title").trim();if(f.title.length()>80)f.title=f.title.substring(0,80);
                page.folders.add(f);continue;
            }
            if(!"item".equals(e.getLocalName())||!(cls.isEmpty()||cls.startsWith("object.item.imageItem")))continue;
            List<Rendition> found=new ArrayList<>();NodeList res=e.getElementsByTagNameNS("*","res");
            for(int i=0;i<res.getLength()&&i<16;i++){
                Element r=(Element)res.item(i);String[] info=r.getAttribute("protocolInfo").split(":",4);
                if(info.length<3||!"http-get".equals(info[0]))continue;
                String mime=info[2].trim().toLowerCase(Locale.ROOT);if(!MIMES.contains(mime))continue;
                String url=sameHost.apply(r.getTextContent());if(url==null)continue;if(url.isEmpty())continue;
                int[] size=resolution(r.getAttribute("resolution"));if(size[0]==0)size=profile(info.length>3?info[3]:"");
                found.add(new Rendition(url,mime,size[0],size[1],parseLong(r.getAttribute("size"))));
            }
            Rendition pick=choose(found);if(pick!=null)page.photos.add(new Photo(id,pick));
        }
        if(page.returned==0)page.returned=page.folders.size()+page.photos.size();
        return page;
    }
    private static long parseLong(String s){try{return Math.max(0,Long.parseLong(s.trim()));}catch(Exception e){return 0;}}

    /** One folder page from the media server; the Wallpaper passes DlnaClient, tests pass a fake. */
    interface Browser{Page browse(String id,int offset)throws Exception;}

    /**
     * Every picture in the chosen folders and their subfolders, and nothing else (WP-03). The walk starts only at saved
     * roots (never the server's top level "0") and only opens folders the server listed inside them, so it can't wander
     * into the rest of the NAS; repeated or looping folders are opened once. Bounded in pictures, requests and depth.
     * Throws only when not one folder could be read, so the caller knows the NAS is away.
     */
    static List<Photo> collect(JSONArray roots,Browser browser)throws Exception{
        List<Photo> out=new ArrayList<>();Set<String> opened=new HashSet<>(),photos=new HashSet<>();
        ArrayDeque<String[]> queue=new ArrayDeque<>();   // {id, depth}
        if(roots!=null)for(int i=0;i<roots.length();i++){JSONObject r=roots.optJSONObject(i);String id=r==null?"":r.optString("id");if(!id.isEmpty()&&!"0".equals(id)&&id.length()<=1024)queue.add(new String[]{id,"0"});}
        int requests=0,answered=0;Exception first=null;
        while(!queue.isEmpty()&&out.size()<MAX_PHOTOS&&requests<MAX_REQUESTS){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            String[] next=queue.poll();String id=next[0];int depth=Integer.parseInt(next[1]);if(!opened.add(id))continue;
            int offset=0;
            while(requests<MAX_REQUESTS&&out.size()<MAX_PHOTOS){
                Page page;requests++;
                try{page=browser.browse(id,offset);answered++;}catch(Exception e){if(first==null)first=e;break;}
                for(Photo p:page.photos)if(out.size()<MAX_PHOTOS&&photos.add(p.id))out.add(p);
                if(depth+1<MAX_DEPTH)for(DlnaClient.Item f:page.folders)if(!"0".equals(f.id)&&!opened.contains(f.id))queue.add(new String[]{f.id,String.valueOf(depth+1)});
                if(!page.more(offset,PAGE))break;offset=page.next(offset);
            }
        }
        if(answered==0&&first!=null)throw first;
        return out;
    }

    /**
     * EXIF orientation as {clockwise degrees, mirror}: rotate first, then mirror left to right. Cameras record how the
     * phone was held instead of turning the pixels; without this, portrait photos lie on their side (spec 33.4).
     */
    static int[] orientation(int tag){
        switch(tag){
            case 2:return new int[]{0,1};case 3:return new int[]{180,0};case 4:return new int[]{180,1};
            case 5:return new int[]{90,1};case 6:return new int[]{90,0};case 7:return new int[]{270,1};case 8:return new int[]{270,0};
            default:return new int[]{0,0};
        }
    }
    /** The cache key for a picture: its server and its id there, never its title or folder name. */
    static String key(String udn,String id){return (udn==null?"":udn)+"\n"+(id==null?"":id);}
}
