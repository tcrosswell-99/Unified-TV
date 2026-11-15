package io.unifiedtv.preview;
import org.json.JSONObject;
import java.util.*;

/**
 * A pretend DLNA media server for tests: folders and videos by object id, paged 24 at a time with TotalMatches, as
 * DlnaClient.browse returns them. No network. Addresses use a reserved test name, never a real address.
 */
final class FakeServer implements LibraryIndex.Browser {
    static final String HOST="nas.test",CONTROL="http://"+HOST+":50001/ctl";
    final Map<String,List<DlnaClient.Item>> children=new LinkedHashMap<>();
    final List<String> calls=Collections.synchronizedList(new ArrayList<>());
    final Set<String> broken=new HashSet<>();
    /** Set to hold every browse until released (a slow server). */
    volatile java.util.concurrent.CountDownLatch gate;
    /** With a gate: browses wait only once this many have been answered in all (a server that slows down part-way). */
    volatile int holdAfter;

    static JSONObject source(){try{return new JSONObject().put("name","Synology Media Server").put("control",CONTROL).put("service","urn:schemas-upnp-org:service:ContentDirectory:1").put("udn","uuid:test-nas").put("location","http://"+HOST+":50001/desc.xml").put("enabled",true);}catch(Exception e){throw new IllegalStateException(e);}}
    static DlnaClient.Device device(){DlnaClient.Device d=new DlnaClient.Device();d.name="Synology Media Server";d.control=CONTROL;d.service="urn:schemas-upnp-org:service:ContentDirectory:1";d.udn="uuid:test-nas";d.location="http://"+HOST+":50001/desc.xml";return d;}

    FakeServer folder(String parent,String id,String title){DlnaClient.Item i=new DlnaClient.Item();i.id=id;i.title=title;i.folder=true;i.parent=parent;children.computeIfAbsent(parent,k->new ArrayList<>()).add(i);children.computeIfAbsent(id,k->new ArrayList<>());return this;}
    FakeServer video(String parent,String id,String title,String... ratings){
        DlnaClient.Item i=new DlnaClient.Item();i.id=id;i.title=DlnaClient.title(title);i.parent=parent;i.url="http://"+HOST+":50002/v/"+id+".mkv";i.ratings.addAll(Arrays.asList(ratings));
        children.computeIfAbsent(parent,k->new ArrayList<>()).add(i);return this;}
    /** A video with the server's res@size (bytes) and res@duration (ms), as Synology Media Server sends them. */
    FakeServer video(String parent,String id,String title,long size,long durationMs){video(parent,id,title);DlnaClient.Item i=find(id);i.size=size;i.duration=durationMs;return this;}
    /** The same file seen in another folder (a server's virtual view or a nested chosen folder): same stream. */
    FakeServer alias(String parent,String id,String sameAs){
        DlnaClient.Item from=find(sameAs);DlnaClient.Item i=new DlnaClient.Item();i.id=id;i.title=from.title;i.parent=parent;i.url=from.url;i.ratings.addAll(from.ratings);
        children.computeIfAbsent(parent,k->new ArrayList<>()).add(i);return this;}
    DlnaClient.Item find(String id){for(List<DlnaClient.Item> l:children.values())for(DlnaClient.Item i:l)if(i.id.equals(id))return i;return null;}

    @Override public DlnaClient.Page browse(JSONObject source,String id,int offset)throws Exception{
        java.util.concurrent.CountDownLatch g=gate;if(g!=null&&calls.size()>=holdAfter)g.await(20,java.util.concurrent.TimeUnit.SECONDS);
        calls.add(id+"@"+offset);
        if(broken.contains(id)||!children.containsKey(id))throw new DlnaClient.Failure(DlnaClient.Failure.MISSING);
        List<DlnaClient.Item> all=children.get(id);DlnaClient.Page p=new DlnaClient.Page();
        for(int i=offset;i<Math.min(all.size(),offset+DlnaClient.PAGE);i++)p.items.add(all.get(i));
        p.returned=p.items.size();p.total=all.size();return p;
    }
}
