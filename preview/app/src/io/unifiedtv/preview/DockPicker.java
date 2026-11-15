package io.unifiedtv.preview;
import java.util.*;

/**
 * "Choose an app" (owner's Sony, run 106): the one app picker, as rules. Pure; AppPickerActivity is its glass sidebar.
 *
 * <ul>
 * <li><b>An empty dock place.</b> OK on the focused empty place (the "+" tile, "Add an app") offers the installed apps
 * that are not already on Home, streaming services first, then the rest by name; search narrows it; "Leave empty"
 * changes nothing. A choice goes into exactly that place in the household's saved order (put()).</li>
 * <li><b>Replace with another app</b> (Hold OK on a tile): the same list; the choice takes that tile's place and the old
 * app leaves Home (it stays installed and in Apps).</li>
 * <li><b>The Live TV app</b> (Settings, or Live TV with none recognised): every installed app, the likeliest Live TV apps
 * first. The choice is saved as {@link #LIVE_PREF} and from then on is what LiveTvProtection, Launch's gate, LiveTvGuard
 * and the cover treat as Live TV. Choosing never unlocks it: OD-26's rule and the Kids list still decide.</li>
 * </ul>
 */
final class DockPicker {
    private DockPicker(){}
    /** The household's Live TV app, a package name; absent means Automatic (LiveTvProtection's own recognition). */
    static final String LIVE_PREF="livePackage";
    static final String TITLE="Choose an app for this place",REPLACE_TITLE="Replace with another app",LIVE_TITLE="Choose your Live TV app",LEAVE="Leave empty",AUTOMATIC="Automatic";

    static final class Choice {
        /** The dock id (a known tile id such as "netflix", else the package), the package, and the name shown. */
        final String id,pkg,name;final boolean streaming;
        Choice(String id,String pkg,String name,boolean streaming){this.id=id;this.pkg=pkg;this.name=name;this.streaming=streaming;}
        @Override public String toString(){return name;}
    }

    /** Streaming services, music and video alike: the dock's well-known apps and Build My TV's catalogue. */
    static int rank(String pkg){
        String id=DockPolicy.canonical(pkg);
        for(int i=0;i<DockPolicy.SUGGESTION_ORDER.length;i++)if(DockPolicy.SUGGESTION_ORDER[i].equals(id)||DockPolicy.SUGGESTION_ORDER[i].equals(pkg))return i;
        int i=0;for(AppCatalogue.Entry e:AppCatalogue.ENTRIES){i++;
            if(!AppCatalogue.VIDEO.equals(e.category)&&!AppCatalogue.YOUTUBE.equals(e.category)&&!AppCatalogue.MUSIC.equals(e.category))continue;
            for(String p:e.packages)if(p.equals(pkg))return 100+i;}
        return -1;
    }
    static boolean streaming(String pkg){return rank(pkg)>=0;}
    /** Whether an app matches what was typed: its name or package, ignoring case; nothing typed matches everything. */
    static boolean matches(String name,String pkg,String query){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return true;
        return (name!=null&&name.toLowerCase(Locale.ROOT).contains(q))||(pkg!=null&&pkg.toLowerCase(Locale.ROOT).contains(q));
    }
    private static Comparator<Choice> byName(){return Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT));}

    /**
     * For a dock place: the installed apps ({package: name}) not already on Home ({@code onHome}, dock ids), one entry per
     * dock id, streaming first (in the dock's suggestion order), then the rest by name, narrowed by {@code query}.
     */
    static List<Choice> choices(String room,Map<String,String> launchable,Collection<String> onHome,String query){
        Set<String> home=new HashSet<>();if(onHome!=null)for(String id:onHome)home.add(DockPolicy.canonical(id));
        boolean youtube=DockLabels.youtube(launchable.keySet());
        Map<String,Choice> byId=new LinkedHashMap<>();
        for(Map.Entry<String,String> e:launchable.entrySet()){
            String pkg=e.getKey(),id=DockPolicy.canonical(pkg);if(home.contains(id)||byId.containsKey(id)||!DockPolicy.valid(room,id))continue;
            String own=DockLabels.name(id,youtube),name=own!=null?own:e.getValue()==null||e.getValue().trim().isEmpty()?pkg:e.getValue().trim();
            if(!matches(name,pkg,query))continue;
            byId.put(id,new Choice(id,pkg,name,streaming(pkg)));
        }
        List<Choice> first=new ArrayList<>(),rest=new ArrayList<>();
        for(Choice c:byId.values())(c.streaming?first:rest).add(c);
        first.sort(Comparator.comparingInt((Choice c)->rank(c.pkg)).thenComparing(byName()));rest.sort(byName());
        first.addAll(rest);return first;
    }
    /** Whether an app looks like a Live TV app by its name or package (LiveTvProtection's recognition, or "tv" apps). */
    static boolean liveLike(String pkg,String name){
        String s=((pkg==null?"":pkg)+" "+(name==null?"":name)).toLowerCase(Locale.ROOT);
        return Endpoint.protectedApp(pkg==null?"":pkg,name==null?"":name,"")||s.contains("iptv")||s.contains("live");
    }
    /** Settings apps that are never a Live TV app. */
    private static final Set<String> SETTINGS=new HashSet<>(Arrays.asList("com.android.tv.settings","com.android.settings","com.google.android.tv.settings","com.sony.dtv.settings","com.tcl.settings"));
    /**
     * Pure: apps that are never the household's Live TV app (review of a615a38: choosing NordVPN looped on "Connect NordVPN
     * first"): NordVPN and any VPN app, the TV's settings, and Unified TV itself ({@code own}).
     */
    static boolean notLiveTv(String pkg,String name,String own){
        String p=pkg==null?"":pkg.toLowerCase(Locale.ROOT),n=name==null?"":name.trim().toLowerCase(Locale.ROOT);
        return p.isEmpty()||p.equals(own)||p.equals(LiveTvProtection.NORDVPN)||p.contains("vpn")||n.contains("vpn")
            ||SETTINGS.contains(p)||p.endsWith(".settings")||n.equals("settings")||n.endsWith(" settings");
    }
    /** For the Live TV app (see the four-argument form), with no VPN apps known beyond the names. */
    static List<Choice> liveChoices(Map<String,String> launchable,String query){return liveChoices(launchable,query,Collections.<String>emptySet(),null);}
    /**
     * For the Live TV app: the installed apps that could be it (not notLiveTv(), not one of {@code vpnApps}, the apps that
     * can run a VPN), the likeliest Live TV apps first, then by name, narrowed by {@code query}.
     */
    static List<Choice> liveChoices(Map<String,String> launchable,String query,Set<String> vpnApps,String own){
        List<Choice> first=new ArrayList<>(),rest=new ArrayList<>();
        for(Map.Entry<String,String> e:launchable.entrySet()){String pkg=e.getKey(),name=e.getValue()==null||e.getValue().trim().isEmpty()?pkg:e.getValue().trim();
            if(notLiveTv(pkg,name,own)||(vpnApps!=null&&vpnApps.contains(pkg))||!matches(name,pkg,query))continue;Choice c=new Choice(pkg,pkg,name,false);(liveLike(pkg,name)?first:rest).add(c);}
        first.sort(byName());rest.sort(byName());first.addAll(rest);return first;
    }

    /**
     * The household's dock with {@code id} in place {@code place}: an empty place (the first free one, {@code place} ==
     * the dock's length, within {@code capacity}) gains it; a filled place is replaced by it (the old app leaves Home).
     * An app already elsewhere on the dock moves rather than appearing twice. Null when the place can't take it (no free
     * place there, or not an app this room can show): nothing is saved then.
     */
    /**
     * An empty place: the household's saved dock with {@code id} right after {@code after}, the last tile Home shows (null:
     * Home shows none, so first). The saved list may hold ids Home hides (an app since uninstalled), so a position counted
     * on Home is never used as an index into it (review of a615a38: that replaced a tile). {@code place} is the empty
     * place's number on Home, which must be within {@code capacity}. Null when the place can't take it ({@code after} is no
     * longer on the dock, no room, or not an app this room can show): nothing is saved then.
     */
    static List<String> insertAfter(String room,List<String> dock,String after,String id,int place,int capacity){
        String c=DockPolicy.canonical(id==null?"":id);if(c.isEmpty()||!DockPolicy.valid(room,c)||place<0||place>=capacity)return null;
        List<String> next=new ArrayList<>(dock);next.remove(c);
        int at=0;if(after!=null){at=next.indexOf(DockPolicy.canonical(after))+1;if(at==0)return null;}
        next.add(at,c);return next;
    }
    /** A filled place, by its index in the saved list (see insertAfter for an empty one). */
    static List<String> put(String room,List<String> dock,int place,String id,int capacity){
        String c=DockPolicy.canonical(id==null?"":id);if(c.isEmpty()||!DockPolicy.valid(room,c)||place<0||place>=capacity)return null;
        List<String> next=new ArrayList<>(dock);
        if(place<next.size()){String old=next.get(place);if(old.equals(c))return next;next.remove(c);next.set(next.indexOf(old),c);return next;}
        if(place!=next.size())return null;   // places fill from the left: only the first free one can be chosen
        next.remove(c);next.add(Math.min(place,next.size()),c);return next;
    }
}
