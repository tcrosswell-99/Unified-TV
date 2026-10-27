package io.unifiedtv.preview;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

/**
 * Owner principle OD-19 (SPEC §3; DESIGN_SYSTEM "Settings and setup always use the sidebar"), enforced: every settings or
 * setup screen is the glass sidebar over a still Home, and a full-screen page has to be listed here, on purpose, as content.
 *
 * Rules, read from preview/app/AndroidManifest.xml and the classes themselves:
 * <ol>
 * <li>Every activity in the manifest is in exactly one list below: SETUP, OWN_SHEET or CONTENT. A new activity fails until
 *     someone decides which it is, and a list entry with no activity behind it fails too.</li>
 * <li>Every SETUP and OWN_SHEET activity uses the translucent sidebar theme, {@code @style/SettingsGlassTheme}.</li>
 * <li>Every SETUP activity extends {@link GlassScreen}, the one sidebar base; every GlassScreen activity is in SETUP.</li>
 * <li>Every class that extends the plain full-screen {@link Screen} (and isn't a GlassScreen) is in CONTENT, or in OWN_SHEET
 *     when it draws the module 13 sheet itself. So a new full-screen settings screen can't slip in: it has to be listed
 *     here as content, with a comment saying why, where a reviewer sees it.</li>
 * </ol>
 * SidebarWidthTest checks under Robolectric that the sheet really is about half the screen (three quarters when wide).
 */
public class SidebarRuleTest {
    static final String THEME="@style/SettingsGlassTheme";

    /** Settings and setup: the GlassScreen sidebar over a still Home (half width; wide() for about three quarters). */
    static final Set<String> SETUP=new TreeSet<>(Arrays.asList(
        "BuildMyTvActivity",         // Build My TV: choosing services and arranging Home (wide)
        "CleanupActivity",           // Updates & About > Advanced > Optional clean-up (wide for its list)
        "ConnectionsActivity",       // signing in to a media server by typing (advanced)
        "FirstRunActivity",          // the guided setup (SET-010): Welcome, remote, apps, media server, finish
        "GalleryActivity",           // Gallery settings (its slideshow is shown full screen in place of the sheet)
        "KidsActivity",              // Profiles & Family > Kids settings
        "LibrarySetupActivity",      // choosing the NAS folders for Your Films & TV
        "MusicActivity",             // Music: a choice page (Amazon Music, the store or Now Playing), no content of its own
        "OwnerDiagnosticsActivity",  // Updates & About > Advanced > Owner diagnostics (wide)
        "PerformanceActivity",       // TV Performance & Cleanup: read-only measurements
        "PhotoFoldersActivity",      // Wallpaper > My Photos: choosing picture folders
        "PictureFixActivity",        // Your Films & TV > hold OK > Wrong picture?: choosing a title's poster (SET-034)
        "QuickConnectActivity",      // signing in with your phone
        "RemoteCheckActivity",       // Check your remote's buttons
        "ServiceDiscoveryActivity"));// finding the media server on the home network

    /** Settings that draw the module 13 sheet themselves, or have no page at all; still the sidebar theme. */
    static final Set<String> OWN_SHEET=new TreeSet<>(Arrays.asList(
        "SettingsActivity",          // the module 13 Settings sheet itself (SettingsOverlay); SettingsJourneyTest
        "HouseholdActivity"));       // no page: hands over to Settings' household task inside the sheet

    /**
     * Not settings or setup, so they may use the whole screen (OD-19: "Browsing and watching content may be full screen").
     * Add to this list only for something the household watches or browses, never for a page of choices.
     */
    static final Set<String> CONTENT=new TreeSet<>(Arrays.asList(
        "UnifiedPreviewActivity",    // Home itself
        "AppsActivity",              // the Apps grid (managing one app opens a side panel inside it)
        "DlnaLibraryActivity",       // browsing Your Films & TV (its setup hands over to LibrarySetupActivity)
        "LibraryActivity",           // browsing a media server's catalogue
        "PlayerActivity",            // watching
        "NowPlayingActivity",        // what's playing, with its controls
        "WallpaperActivity",         // the Wallpaper: artwork, photos and the clock
        "VoiceActivity"));           // Ask your TV: a small listening pane over Home (ReturnJourneyTest checks it is small)

    // --- reading the app
    /** preview/app, found from wherever the tests run (Gradle runs them in the module; local runners from the repository). */
    static File app(){
        for(File dir=new File(System.getProperty("user.dir")).getAbsoluteFile();dir!=null;dir=dir.getParentFile())
            for(File c:new File[]{dir,new File(dir,"app"),new File(dir,"preview/app")})
                if(new File(c,"AndroidManifest.xml").isFile()&&new File(c,"src/io/unifiedtv/preview/GlassScreen.java").isFile())return c;
        throw new AssertionError("preview/app not found from "+System.getProperty("user.dir"));
    }
    /** Activity simple name → its android:theme ("" when none), from the manifest. */
    static Map<String,String> manifest()throws Exception{
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
        Element root=f.newDocumentBuilder().parse(new File(app(),"AndroidManifest.xml")).getDocumentElement();
        String ns="http://schemas.android.com/apk/res/android",pkg=root.getAttribute("package");
        Map<String,String> out=new TreeMap<>();NodeList list=root.getElementsByTagName("activity");
        for(int i=0;i<list.getLength();i++){Element a=(Element)list.item(i);String name=a.getAttributeNS(ns,"name");
            if(name.startsWith("."))name=pkg+name;
            assertTrue("activity outside "+pkg+": "+name,name.startsWith(pkg+".")&&name.indexOf('.',pkg.length()+1)<0);
            out.put(name.substring(pkg.length()+1),a.getAttributeNS(ns,"theme"));}
        assertFalse("no activities read from the manifest",out.isEmpty());
        return out;
    }
    static Class<?> type(String simple)throws ClassNotFoundException{return Class.forName("io.unifiedtv.preview."+simple,false,SidebarRuleTest.class.getClassLoader());}

    // --- the rules
    @Test public void everyActivityIsClassifiedOnce()throws Exception{
        Set<String> listed=new TreeSet<>();
        for(Set<String> l:Arrays.asList(SETUP,OWN_SHEET,CONTENT))for(String n:l)assertTrue(n+" is in more than one list",listed.add(n));
        Set<String> declared=manifest().keySet();
        Set<String> unlisted=new TreeSet<>(declared);unlisted.removeAll(listed);
        assertTrue("OD-19: decide whether each of these is a settings/setup screen (SETUP: the sidebar) or content (CONTENT), and list it in SidebarRuleTest: "+unlisted,unlisted.isEmpty());
        Set<String> stale=new TreeSet<>(listed);stale.removeAll(declared);
        assertTrue("listed in SidebarRuleTest but not in the manifest: "+stale,stale.isEmpty());
    }
    @Test public void settingsAndSetupUseTheSidebarTheme()throws Exception{
        Map<String,String> themes=manifest();
        for(Set<String> l:Arrays.asList(SETUP,OWN_SHEET))for(String n:l)
            assertEquals("OD-19: "+n+" is a settings or setup screen, so it needs android:theme=\""+THEME+"\" (translucent: Home stays visible and still)",THEME,themes.get(n));
    }
    @Test public void setupScreensAreGlassScreensAndOnlyThey()throws Exception{
        for(String n:manifest().keySet()){Class<?> c=type(n);boolean glass=GlassScreen.class.isAssignableFrom(c);
            if(SETUP.contains(n))assertTrue("OD-19: "+n+" must extend GlassScreen, the sidebar base",glass);
            else assertFalse(n+" is a GlassScreen sidebar: list it in SETUP, not "+(CONTENT.contains(n)?"CONTENT":"OWN_SHEET"),glass);}
    }
    @Test public void everyFullScreenScreenIsListedAsContent()throws Exception{
        Set<String> plain=new TreeSet<>();
        // Every activity the app declares...
        for(String n:manifest().keySet()){Class<?> c=type(n);if(Screen.class.isAssignableFrom(c)&&!GlassScreen.class.isAssignableFrom(c))plain.add(n);}
        // ...and every class in the sources that extends Screen directly, declared or not.
        Pattern extendsScreen=Pattern.compile("\\bclass\\s+(\\w+)\\s+extends\\s+(?:io\\.unifiedtv\\.preview\\.)?Screen\\b");
        File[] sources=new File(app(),"src/io/unifiedtv/preview").listFiles((d,name)->name.endsWith(".java"));
        assertNotNull(sources);
        for(File f:sources){Matcher m=extendsScreen.matcher(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
            while(m.find())if(!m.group(1).equals("GlassScreen"))plain.add(m.group(1));}
        assertTrue("found no full-screen Screen classes: the scan is broken",plain.contains("PlayerActivity"));
        for(String n:plain)assertTrue("OD-19: "+n+" extends the full-screen Screen. A settings or setup screen must extend GlassScreen "
            +"(the sidebar); only content may be full screen, listed with a reason in SidebarRuleTest.CONTENT",CONTENT.contains(n)||OWN_SHEET.contains(n));
    }
}
