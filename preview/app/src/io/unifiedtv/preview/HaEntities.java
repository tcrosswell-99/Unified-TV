package io.unifiedtv.preview;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The household's own Home Assistant as plain choices, so nobody types an entity ID unless they ask to.
 * One bounded read of /api/states through LocalApi (same origin, token and redirect rules), then pure parsing.
 * Nothing here is stored or logged; the household record keeps only the chosen IDs and their friendly names.
 *
 * Each role has "strong" candidates (the ID or name clearly says what it is) and "other" candidates listed below a
 * divider. A role is filled automatically only when exactly one strong, responding candidate exists; with two or more,
 * the household always chooses.
 */
final class HaEntities {
    /** Never more than LocalApi already allows, and never more entities than a very large home. */
    static final int MAX_CHARS=LocalApi.LIMIT,MAX_ENTITIES=10_000,MAX_LISTED=100;
    /**
     * HouseholdService treats a doorbell as a binary sensor going from off to on (AlertPolicy.rising). Newer Home
     * Assistant integrations (Ring since 2024, Nest, UniFi Protect) report a press as an event.* entity instead, whose
     * state becomes the press time. Until AlertPolicy listens for that, offering an event entity would save a doorbell
     * that never rings on the TV, so event doorbells are only named, not chosen. Turn on once AlertPolicy supports them.
     */
    static final boolean EVENT_DOORBELLS = true;
    static final String[] ROLES={"door","person","camera","calendar","xbox","tvplus","art","ringEnter","ringExit"};
    static final String[][] ROOMS={{"tcl","TCL"},{"sony","Sony BRAVIA"},{"frame","Samsung The Frame"}};
    /** The rooms this Android app can be in: the Frame runs the separate Tizen app, so it is never offered here. */
    static final String[][] ANDROID_ROOMS={ROOMS[0],ROOMS[1]};

    private HaEntities(){}

    static final class Entity {
        final String id,domain,object,name,deviceClass,state;final Set<String> words;final String phrase;final List<String> eventTypes;
        /** Ring's own entities say "Data provided by Ring.com" (the integration's attribution). */
        final boolean ring;
        /** Home Assistant kept this entity from before, but its integration isn't providing it now (attribute "restored"). */
        final boolean restored;
        Entity(String id,String name,String deviceClass,String state,List<String> eventTypes){this(id,name,deviceClass,state,eventTypes,"",false);}
        Entity(String id,String name,String deviceClass,String state,List<String> eventTypes,String attribution,boolean restored){
            this.id=id;int dot=id.indexOf('.');domain=id.substring(0,dot);object=id.substring(dot+1);
            this.name=name;this.deviceClass=deviceClass;this.state=state;this.eventTypes=eventTypes;
            ring=attribution!=null&&attribution.toLowerCase(Locale.ROOT).contains("ring.com");this.restored=restored;
            List<String> idWords=Arrays.asList(object.split("_")),nameWords=tokens(name);
            Set<String> all=new LinkedHashSet<>(idWords);all.addAll(nameWords);words=Collections.unmodifiableSet(all);
            phrase=" "+String.join(" ",idWords)+" | "+String.join(" ",nameWords)+" ";
        }
        boolean responding(){return !restored&&!"unavailable".equals(state);}
        String lastIdWord(){int u=object.lastIndexOf('_');return u<0?object:object.substring(u+1);}
        String lastNameWord(){List<String> w=tokens(name);return w.isEmpty()?"":w.get(w.size()-1);}
        boolean any(String... options){for(String o:options)if(words.contains(o))return true;return false;}
        boolean typed(String... options){for(String o:options)if(eventTypes.contains(o))return true;return false;}
    }
    static final class Candidate {
        final String id,name;final boolean responding;
        /** Plain words for the list ("Front Door — doorbell press"), the device alone ("Front Door") and the row's value. */
        final String label,device,value;
        /** One of Ring's own; may be filled in by itself when it is the only clear answer. */
        final boolean ring,automatic;
        Candidate(Entity e){this(e,e.name,e.name,e.name,false);}
        Candidate(Entity e,String label,String device,String value,boolean automatic){
            id=e.id;name=e.name;responding=e.responding();this.label=label;this.device=device;this.value=value;ring=e.ring;this.automatic=automatic;
        }
    }
    static final class Choices {
        /** Ring's own (listed first, under "Ring doorbells"), other clear matches, then everything else (only on request). */
        final List<Candidate> ring,strong,other;final String otherHeading;
        Choices(List<Candidate> strong,List<Candidate> other,String otherHeading){this(new ArrayList<>(),strong,other,otherHeading);}
        Choices(List<Candidate> ring,List<Candidate> strong,List<Candidate> other,String otherHeading){this.ring=ring;this.strong=strong;this.other=other;this.otherHeading=otherHeading;}
        /** Ring's first, then the other clear matches: what the list shows before "Show everything". */
        List<Candidate> best(){List<Candidate> out=new ArrayList<>(ring);out.addAll(strong);return out;}
        /** The one clear answer, or null: never a guess between two plausible entities, and never an optional extra. */
        Candidate auto(){List<Candidate> b=best();return b.size()==1&&b.get(0).responding&&b.get(0).automatic?b.get(0):null;}
        boolean isEmpty(){return ring.isEmpty()&&strong.isEmpty()&&other.isEmpty();}
        Candidate find(String id){for(List<Candidate> l:Arrays.asList(ring,strong,other))for(Candidate c:l)if(c.id.equals(id))return c;return null;}
    }
    /** One look at Home Assistant: its status word, and its entities when it shared them. */
    static final class Snapshot {
        final String status;final List<Entity> entities;final boolean tooLarge;
        Snapshot(String status,List<Entity> entities,boolean tooLarge){this.status=status;this.entities=entities;this.tooLarge=tooLarge;}
    }

    // --- network (call off the UI thread)

    static Snapshot fetch(JSONObject ha){
        String early=HaStatus.before(ha);if(early!=null)return new Snapshot(early,null,false);
        try{
            List<Entity> list=parse(new String(LocalApi.bytes(ha,"/api/states"),StandardCharsets.UTF_8));
            return list==null?new Snapshot(HaStatus.REACHABLE,null,false):new Snapshot(HaStatus.AUTHORISED,list,false);
        }catch(Exception e){
            // LocalApi stops reading past its limit with exactly this message: the server answered with a 2xx, so it is authorised.
            if(e instanceof IOException&&!(e instanceof LocalApi.ApiError)&&"Response too large".equals(e.getMessage()))return new Snapshot(HaStatus.AUTHORISED,null,true);
            return new Snapshot(HaStatus.after(e),null,false);
        }
    }

    // --- parsing

    /**
     * The states array as entities, or null when it is too large or not a states array. Restored ghosts are kept, marked,
     * so the page can say "Home Assistant isn't hearing from Ring" (its integration failed to load), but they are never offered.
     */
    static List<Entity> parse(String json){
        if(json==null||json.length()>MAX_CHARS)return null;
        JSONArray states;try{states=new JSONArray(json);}catch(JSONException e){return null;}
        List<Entity> out=new ArrayList<>();
        for(int i=0;i<states.length()&&out.size()<MAX_ENTITIES;i++){
            JSONObject s=states.optJSONObject(i);if(s==null)continue;
            String id=s.optString("entity_id");if(!id.matches("[a-z0-9_]+\\.[a-z0-9_]+"))continue;
            JSONObject attr=s.optJSONObject("attributes");
            String name=AlertPolicy.name(attr).replaceAll("\\s+"," ").trim();if(name.isEmpty())name=pretty(id.substring(id.indexOf('.')+1));
            List<String> types=new ArrayList<>();JSONArray t=attr==null?null:attr.optJSONArray("event_types");
            if(t!=null)for(int n=0;n<t.length()&&n<20;n++)types.add(t.optString(n).toLowerCase(Locale.ROOT));
            out.add(new Entity(id,name,attr==null?"":attr.optString("device_class").toLowerCase(Locale.ROOT),s.optString("state"),types,
                attr==null?"":attr.optString("attribution"),attr!=null&&attr.optBoolean("restored")));
        }
        return out;
    }

    // --- roles

    static Choices choices(String role,List<Entity> all,String door){return choices(role,all,door,EVENT_DOORBELLS);}

    /**
     * What each row's list offers. Ring's own come first, then other clear matches; everything else waits behind "Show
     * everything" (owner, Sony run 106: Alexa's "Amazon Kids" switches filled the doorbell list). Restored ghosts never appear.
     */
    static Choices choices(String role,List<Entity> all,String door,boolean events){
        List<Entity> strong=new ArrayList<>(),other=new ArrayList<>();String heading;
        if(all==null)all=Collections.emptyList();
        List<Entity> live=new ArrayList<>();for(Entity e:all)if(!e.restored)live.add(e);all=live;
        String prefix=devicePrefix(door);boolean automatic=true;
        switch(role){
            case "door":
                heading="Other sensors";
                for(Entity e:all){if(doorbell(e,events)){if(e.responding()||e.ring)strong.add(e);else other.add(e);}
                    else if(e.domain.equals("binary_sensor")&&GENERIC.contains(e.deviceClass))other.add(e);}
                // Older Home Assistant had both a Ring ding sensor and a ding event for one doorbell: the event is the one.
                for(Iterator<Entity> it=strong.iterator();it.hasNext();){Entity e=it.next();
                    if(e.domain.equals("binary_sensor")&&e.ring)for(Entity x:strong)if(x.domain.equals("event")&&x.ring&&sameDevice(x,e)){it.remove();other.add(e);break;}}
                break;
            case "person":
                // Optional, and never filled in by itself: the household chooses a Ring motion or person event, or a person
                // sensor. Plain motion sensors wait behind "Show everything".
                heading="Other sensors";automatic=false;
                for(Entity e:all){
                    if(e.domain.equals("event")){if(eventDoorbell(e))continue;
                        boolean person=e.typed("person","human","people","pedestrian"),motion=e.typed("motion")||"motion".equals(e.deviceClass);
                        if(person||(motion&&e.ring))strong.add(e);else if(motion)other.add(e);continue;}
                    if(!e.domain.equals("binary_sensor")||!PERSON_DC.contains(e.deviceClass)||doorbell(e,true))continue;
                    if(e.responding()&&(e.any("person","people","human","pedestrian")||(e.ring&&e.any("motion"))))strong.add(e);else other.add(e);}
                break;
            case "camera":
                // A camera beside the chosen doorbell (same device prefix) is the clear answer; recordings and snapshots are
                // stills and are never offered as the live camera (spec 10.6: never label a still as live).
                heading="Other cameras";List<Entity> moving=new ArrayList<>(),beside=new ArrayList<>();
                for(Entity e:all){if(!e.domain.equals("camera"))continue;
                    if((e.responding()||e.ring)&&!still(e)){moving.add(e);if(prefix!=null&&near(e,prefix))beside.add(e);}else other.add(e);}
                List<Entity> chosen=beside.isEmpty()?moving:beside;strong.addAll(chosen);for(Entity e:moving)if(!chosen.contains(e))other.add(e);
                break;
            case "calendar":
                heading="Other calendars";
                for(Entity e:all){if(!e.domain.equals("calendar"))continue;
                    if(e.responding()&&e.any("tottenham","spurs","thfc","hotspur","fixtures","fixture"))strong.add(e);else other.add(e);}
                break;
            case "xbox":
                // A script, scene or button that turns the Xbox on; ones that say Xbox first.
                heading="Other scripts, scenes and buttons";
                for(Entity e:all){if(!ACTIONS.contains(e.domain))continue;if(e.responding()&&scriptFits(role,e))strong.add(e);else other.add(e);}
                break;
            case "tvplus":case "art":case "ringEnter":case "ringExit":
                heading="Other Home Assistant actions";
                for(Entity e:all){if(!e.domain.equals("script"))continue;if(e.responding()&&scriptFits(role,e))strong.add(e);else other.add(e);}
                break;
            default:throw new IllegalArgumentException(role);
        }
        Comparator<Entity> order=Comparator.comparing((Entity e)->prefix!=null&&near(e,prefix)?0:1)
            .thenComparing(e->e.responding()?0:1).thenComparing(e->e.name,String.CASE_INSENSITIVE_ORDER).thenComparing(e->e.id);
        strong.sort(order);other.sort(order);
        List<Entity> ring=new ArrayList<>();for(Iterator<Entity> it=strong.iterator();it.hasNext();){Entity e=it.next();if(e.ring){ring.add(e);it.remove();}}
        return new Choices(listed(role,ring,automatic),listed(role,strong,automatic),listed(role,other,false),heading);
    }
    /** What the Xbox row may run: Home Assistant's script, scene and button kinds (HouseholdActivity.service). */
    static final Set<String> ACTIONS=new HashSet<>(Arrays.asList("script","scene","button","input_button"));
    private static boolean sameDevice(Entity a,Entity b){String p=devicePrefix(a.id);return p!=null&&p.equals(devicePrefix(b.id));}

    /** Binary sensor classes a doorbell or generic sensor may use; never smoke, CO, doors, windows, batteries or connectivity. */
    private static final Set<String> GENERIC=new HashSet<>(Arrays.asList("","occupancy","sound","vibration","motion","presence"));
    private static final Set<String> DOOR_DC=new HashSet<>(Arrays.asList("","occupancy","sound","vibration","doorbell"));
    private static final Set<String> PERSON_DC=new HashSet<>(Arrays.asList("","occupancy","motion","presence"));
    private static final Set<String> PRESS_LAST=new HashSet<>(Arrays.asList("ding","doorbell","visitor"));

    /**
     * A doorbell press, by what integrations actually publish:
     * - Ring (older Home Assistant) and ring-mqtt: binary_sensor.<device>_ding ("Front Door Ding"), class occupancy or none.
     * - UniFi Protect (older): binary_sensor.<device>_doorbell. Reolink: binary_sensor.<device>_visitor.
     * - Generic: an ID or name ending in "press"/"pressed" that also mentions a door or bell.
     * The last word decides, so "G4 Doorbell Is dark", "Front Door Motion" and battery or online sensors on a doorbell
     * device are not presses. Door contact sensors (class door) are excluded by class.
     * - Newer Home Assistant: an event entity. Ring's is event.<device>_ding, class doorbell, with event type "ding" up to
     *   Home Assistant 2026.4 and the standard "ring" from 2026.5 (DoorbellEventType.RING); Nest and UniFi use class
     *   doorbell too. Offered only when {@code events} is true (see EVENT_DOORBELLS).
     */
    static boolean doorbell(Entity e,boolean events){
        if(e.domain.equals("event"))return events&&eventDoorbell(e);
        if(!e.domain.equals("binary_sensor")||!DOOR_DC.contains(e.deviceClass))return false;
        String last=e.lastIdWord(),nameLast=e.lastNameWord();
        if(PRESS_LAST.contains(last)||PRESS_LAST.contains(nameLast))return true;
        boolean press=last.equals("press")||last.equals("pressed")||nameLast.equals("press")||nameLast.equals("pressed");
        return press&&e.any("door","doorbell","bell");
    }
    static boolean eventDoorbell(Entity e){
        if(!e.domain.equals("event"))return false;
        if("doorbell".equals(e.deviceClass)||e.typed("ding","ring"))return true;
        for(String t:e.eventTypes)if(t.contains("doorbell"))return true;
        // A generic button event that says it is a doorbell ("Porch bell", event type "press").
        return e.typed("press","pressed","single_press","short_press","initial_press")&&e.any("doorbell","bell","door");
    }

    /** Event-entity doorbells the TV can't listen to yet: named on screen so the household knows why the list is short. */
    static List<String> waitingDoorbells(List<Entity> all){
        List<String> out=new ArrayList<>();if(EVENT_DOORBELLS||all==null)return out;
        for(Entity e:all)if(e.responding()&&eventDoorbell(e)&&out.size()<5)out.add(e.name);
        return out;
    }

    /** Owner scripts, matched on whole words of the ID and friendly name (so "morning" is not "ring"). */
    static boolean scriptFits(String role,Entity e){
        boolean ring=e.any("ring","doorbell");
        boolean enter=e.any("enter","entry","start","show","begin","open"),exit=e.any("exit","return","restore","end","finish","close","back","done","leave");
        switch(role){
            case "xbox":return e.any("xbox");
            case "tvplus":return e.any("tvplus")||e.phrase.contains(" tv plus ");
            case "art":return !ring&&(e.any("artmode")||e.phrase.contains(" art mode "));
            case "ringEnter":return ring&&enter&&!exit;
            case "ringExit":return ring&&exit&&!enter;
            default:return false;
        }
    }
    /** Ring's "Last recording", snapshot and event-image cameras show a still or a clip, not live video. */
    static boolean still(Entity e){
        return e.phrase.contains(" last recording ")||e.any("snapshot","snapshots","thumbnail","still","image","recordings");
    }
    /** The device part of a doorbell ID: binary_sensor.front_door_ding gives front_door; null when nothing is chosen. */
    static String devicePrefix(String door){
        if(door==null||door.indexOf('.')<0)return null;String o=door.substring(door.indexOf('.')+1);int u=o.lastIndexOf('_');
        return u>0?o.substring(0,u):null;
    }
    private static boolean near(Entity e,String prefix){return e.object.equals(prefix)||e.object.startsWith(prefix+"_");}
    private static List<Candidate> listed(String role,List<Entity> list,boolean automatic){
        List<Candidate> out=new ArrayList<>();
        for(Entity e:list){if(out.size()>=MAX_LISTED)break;String[] w=words(role,e);out.add(new Candidate(e,w[0],w[1],w[2],automatic));}
        return out;
    }

    // --- plain words

    /** What Ring and others add after the device's name in a friendly name; longest first. */
    private static final String[] SUFFIXES={"last recording","live view","intercom unlock","person detected","doorbell press","ding","motion","doorbell","visitor","chime","pressed","press","person","snapshot"};
    /** "Front Door Ding" gives "Front Door": the device's own name, else the whole name. */
    static String device(String name){
        if(name==null)return "";String n=name.trim(),lower=n.toLowerCase(Locale.ROOT);
        for(String s:SUFFIXES)if(lower.endsWith(" "+s)&&lower.length()>s.length()+1)return n.substring(0,n.length()-s.length()-1).trim();
        return n;
    }
    /**
     * {label in a list, device, the row's value}: "Front Door — doorbell press", "Front Door", "Front Door". Never the
     * technical name: that only appears under "Show everything".
     */
    static String[] words(String role,Entity e){
        String d=device(e.name);
        switch(role){
            case "door":return new String[]{d+" — doorbell press",d,d};
            case "person":{boolean person=e.typed("person","human","people","pedestrian")||(!e.domain.equals("event")&&e.any("person","people","human","pedestrian"));
                return new String[]{d+(person?" — person":" — motion"),d,d};}
            case "camera":{String kind=still(e)?(e.phrase.contains(" last recording ")?"last recording":"still picture"):d.equals(e.name)?null:"live view";
                return kind==null?new String[]{e.name,e.name,e.name}:new String[]{d+" — "+kind,d,d+" "+kind};}
            default:return new String[]{e.name,e.name,e.name};
        }
    }

    /** Ring doorbells Home Assistant knows but isn't hearing from (the Ring integration didn't load): their device names. */
    static List<String> ringNotAnswering(List<Entity> all){
        List<String> out=new ArrayList<>();if(all==null)return out;
        for(Entity e:all){boolean ding=e.domain.equals("event")&&(e.lastIdWord().equals("ding")||eventDoorbell(e));
            if(e.restored&&ding){String d=device(e.name);if(!out.contains(d)&&out.size()<5)out.add(d);}}
        return out;
    }
    /** Smoke and carbon monoxide alarms Home Assistant reports, by their own names (the same rule AlarmWatch uses). */
    static List<String> alarms(List<Entity> all){
        List<String> out=new ArrayList<>();if(all==null)return out;
        for(Entity e:all)if(!e.restored&&AlertPolicy.alarmKind(e.id,e.deviceClass)!=null&&out.size()<MAX_LISTED)out.add(e.name);
        return out;
    }

    // --- typed fallback (advanced) and display

    /** The Home Assistant domain a role accepts. */
    static String domain(String role){
        switch(role){
            case "door":case "person":return "binary_sensor";   // an event entity is also accepted (valid)
            case "camera":return "camera";
            case "calendar":return "calendar";
            case "xbox":case "tvplus":case "art":case "ringEnter":case "ringExit":return "script";
            default:throw new IllegalArgumentException(role);
        }
    }
    /** The previous screen's rules exactly: empty, or domain.object in lower case. */
    static boolean valid(String role,String id){
        if(id==null||id.isEmpty())return true;
        if(id.matches(domain(role)+"\\.[a-z0-9_]+"))return true;
        if(role.equals("xbox"))return id.matches("(script|scene|button|input_button)\\.[a-z0-9_]+");
        return EVENT_DOORBELLS&&(role.equals("door")||role.equals("person"))&&id.matches("event\\.[a-z0-9_]+");
    }
    /** The Home Assistant action that runs a chosen script, scene or button; null for anything else. */
    static String service(String entity){
        if(entity==null||!entity.matches("[a-z_]+\\.[a-z0-9_]+"))return null;
        switch(entity.substring(0,entity.indexOf('.'))){
            case "script":return "/api/services/script/turn_on";
            case "scene":return "/api/services/scene/turn_on";
            case "button":return "/api/services/button/press";
            case "input_button":return "/api/services/input_button/press";
            default:return null;
        }
    }
    static String roomName(String id){for(String[] r:ROOMS)if(r[0].equals(id))return r[1];return ROOMS[0][1];}
    static boolean room(String id){for(String[] r:ROOMS)if(r[0].equals(id))return true;return false;}
    /** front_door_ding becomes "Front door ding": a readable fallback when Home Assistant has no friendly name. */
    static String pretty(String object){
        String s=object.replace('_',' ').trim();if(s.isEmpty())return s;s=s.length()>60?s.substring(0,60):s;
        return Character.toUpperCase(s.charAt(0))+s.substring(1);
    }
    static List<String> tokens(String s){
        List<String> out=new ArrayList<>();if(s==null)return out;
        for(String w:s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))if(!w.isEmpty())out.add(w);return out;
    }
}
