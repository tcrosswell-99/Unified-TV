package io.unifiedtv.preview;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.*;

/**
 * Settings > Smart Home > Football (owner decision OD-22, 25 October 2026): the household picks a UK team by league or by
 * a few letters and that is all; no account, key or phone step. API-Football is an optional row underneath that opens
 * its phone setup in the same sheet (OD-17, OD-19). TheSportsDB and API-Football are fakes: nothing reaches the network.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class FootballSettingsJourneyTest {
    final List<String> sent=Collections.synchronizedList(new ArrayList<>()),apiSent=Collections.synchronizedList(new ArrayList<>());
    static final String HARTLEPOOL="{\"teams\":[{\"idTeam\":\"134388\",\"strTeam\":\"Hartlepool United\",\"strSport\":\"Soccer\",\"idLeague\":\"4719\",\"strLeague\":\"English National League\",\"strColour1\":\"#0047AB\",\"strColour2\":\"#FFFFFF\"}]}";
    static final String HEARTS="{\"teams\":["+SportsDbTest.team(133647,"Heart of Midlothian","HEA","Hearts","Soccer",254,"#7A263A","#FFFFFF")+"]}";
    @Before public void start()throws Exception{
        RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        RuntimeEnvironment.getApplication().getSharedPreferences("vault",0).edit().clear().commit();
        Vault.testKey=VaultTest.aes();SportsDb.paceMs=0;
        SportsDb.http=path->{sent.add(path);if(!SportsDb.allowed(path))throw new AssertionError(path);
            return new JSONObject(path.contains("English%20Premier%20League")?SportsDbTest.PREMIER:path.contains("Scottish%20Premiership")?HEARTS
                :path.equals("searchteams.php?t=Hartlepool%20United")?HARTLEPOOL:"{\"teams\":null}");};
        ApiFootball.http=(key,path)->{apiSent.add(path);throw new AssertionError("API-Football asked without a key: "+path);};
    }
    @After public void stop()throws Exception{
        waitForList();checked();SportsDb.http=SportsDb::fetch;ApiFootball.http=ApiFootball::get;SportsDb.paceMs=2_500;Vault.testKey=null;
    }

    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static View root(SettingsActivity a){return a.findViewById(android.R.id.content);}
    private Button find(View v,String name){if(v instanceof Button&&name.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),name);if(b!=null)return b;}return null;}
    private TextView text(View v,String start){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(start))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=text(((ViewGroup)v).getChildAt(i),start);if(t!=null)return t;}return null;}
    private void press(SettingsActivity a,String name){Button b=find(root(a),name);assertNotNull("a row '"+name+"'",b);b.requestFocus();b.performClick();idle();}
    private void open(SettingsActivity a,String category){press(a,category);assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());}
    private static void focused(SettingsActivity a,String expected){View f=a.getCurrentFocus();assertTrue("focus on '"+expected+"'",f instanceof TextView&&expected.contentEquals(((TextView)f).getText()));}
    /** The team list is read on its own thread (from the fakes, instantly); its rows appear when it reports back. */
    private static void waitForList()throws InterruptedException{for(int i=0;i<500&&TeamList.building();i++)Thread.sleep(10);idle();}
    /** After a pick, the team's fixtures are read once on their own thread ("football-check"); this waits for it. */
    private static void checked()throws InterruptedException{
        for(int i=0;i<500;i++){boolean running=false;for(Thread t:Thread.getAllStackTraces().keySet())if("football-check".equals(t.getName())&&t.isAlive())running=true;if(!running)break;Thread.sleep(10);}
        idle();
    }
    private void waitFor(SettingsActivity a,String row)throws InterruptedException{for(int i=0;i<500&&find(root(a),row)==null;i++){Thread.sleep(10);idle();}}

    @Test public void pickATeamByLeagueWithNoKeyAndNoPhone()throws Exception{
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();int standard=a.sheet().getLayoutParams().width;open(a,"Smart Home");
            assertNotNull(text(root(a),"Football fixtures: choose your team"));
            assertNotNull("the default needs no account",text(root(a),SetupPages.NO_ACCOUNT));
            press(a,"Your team: choose from the UK teams");
            assertEquals(SettingsActivity.TEAM,a.overlay().task());assertEquals("inside the same sheet",standard,a.sheet().getLayoutParams().width);
            waitForList();
            assertNotNull("TheSportsDB's three (its free key lists few) and Unified TV's own Premier League clubs",find(root(a),"Premier League · 21 teams"));
            assertNotNull("the Championship whole, even with nothing from TheSportsDB",find(root(a),"Championship · 24 teams"));
            assertNotNull(find(root(a),"League One · 24 teams"));
            assertNotNull("Scotland's, with the other name tried",find(root(a),"Premiership · 1 team"));
            assertNotNull(text(root(a),SportsDb.ATTRIBUTION));
            press(a,"Premier League · 21 teams");
            assertNotNull("a club the free key left out",find(root(a),"Wolverhampton Wanderers"));
            assertNotNull(text(root(a),"England: Premier League"));
            press(a,"Tottenham Hotspur");
            assertEquals("back to Smart Home",SettingsOverlay.Phase.DETAIL,a.overlay().page());
            Fixtures.Team t=FixtureFeed.team(a);
            assertEquals("sdb:133616",t.id);assertEquals("api:47",t.alt);assertEquals("TOT",t.shortName);
            assertEquals("the club's own colours for the drawn badge",0xFF132257,t.primary);assertEquals(0xFFFFFFFF,t.secondary);
            assertNotNull(text(root(a),"Football fixtures: Tottenham Hotspur"));assertNotNull(find(root(a),"Badge: TOT on Navy and White"));
            assertNull("a listed team has no calendar names",text(root(a),"Other names in the calendar"));
            assertFalse("no key was asked for",FixtureFeed.apiConfigured(a));assertTrue(apiSent.isEmpty());
            for(String r:new ArrayList<>(sent))assertFalse("only league names and numbers, and the team's number: "+r,r.startsWith("searchteams"));
            assertEquals("its league is kept for its fixtures","4328",t.league);
            assertNotEquals("no phone setup was opened",SettingsActivity.PHONE_FOOTBALL,a.overlay().task());
        }
    }
    @Test public void findATeamWithAFewLetters()throws Exception{
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Smart Home");press(a,"Your team: choose from the UK teams");waitForList();
            press(a,"Search by name");
            AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);
            EditText field=(EditText)Shadows.shadowOf(d).getView();field.setText("hearts");
            d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();idle();
            assertNotNull("searched on the TV, by the club's other name",find(root(a),"Heart of Midlothian · Premiership"));
            for(String r:new ArrayList<>(sent))assertFalse("what is typed never leaves the TV",r.contains("hearts"));
            assertNull("found in the list: nothing to look up",find(root(a),"Look up \"hearts\" on TheSportsDB"));
            press(a,"Heart of Midlothian · Premiership");
            assertEquals("sdb:133647",FixtureFeed.team(a).id);assertNotNull(text(root(a),"Football fixtures: Heart of Midlothian"));
            press(a,"No team");assertNull(FixtureFeed.team(a));assertNotNull(text(root(a),"Football fixtures: choose your team"));
        }
    }
    @Test public void aClubTheListLacksIsLookedUpByNameOnlyWhenAsked()throws Exception{
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Smart Home");press(a,"Your team: choose from the UK teams");waitForList();
            press(a,"Search by name");
            AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();((EditText)Shadows.shadowOf(d).getView()).setText("Hartlepool United");
            d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();idle();
            assertNotNull(text(root(a),"No team in the list matches \"Hartlepool United\"."));
            assertNotNull("says what it sends",text(root(a),SetupPages.ASK_NOTE));
            for(String r:new ArrayList<>(sent))assertFalse("not sent until asked: "+r,r.contains("Hartlepool"));
            press(a,"Look up \"Hartlepool United\" on TheSportsDB");
            waitFor(a,"Hartlepool United · English National League");
            assertTrue(sent.contains("searchteams.php?t=Hartlepool%20United"));
            press(a,"Hartlepool United · English National League");
            Fixtures.Team t=FixtureFeed.team(a);assertEquals("sdb:134388",t.id);assertEquals("4719",t.league);assertEquals(0xFF0047AB,t.primary);
            checked();
        }
    }
    @Test public void aBundledClubCanBePickedAndSaysWhenNoFixturesAreFound()throws Exception{
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Smart Home");press(a,"Your team: choose from the UK teams");waitForList();
            press(a,"Championship · 24 teams");press(a,"Wrexham");
            Fixtures.Team t=FixtureFeed.team(a);
            assertEquals("known by name until its number is found",Fixtures.Team.CLUB,t.id);assertEquals("4329",t.league);assertEquals("WRE",t.shortName);assertEquals(0xFFD71920,t.primary);
            checked();a.refresh();idle();
            assertNotNull("TheSportsDB had nothing for it (the fakes): said plainly",text(root(a),SetupPages.noFixtures("Wrexham")));
            assertNotNull(text(root(a),"Football fixtures: Wrexham"));
            press(a,"Your team: Wrexham");assertNotNull(text(root(a),SetupPages.noFixtures("Wrexham")));
        }
    }
    @Test public void theOptionalKeyRowOpensItsPhoneSetupInTheSheet(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();int standard=a.sheet().getLayoutParams().width;open(a,"Smart Home");
            assertNotNull(text(root(a),SetupPages.MORE_DETAIL));assertNotNull(text(root(a),"Not needed: without a key, fixtures come from TheSportsDB."));
            assertNull("nothing to remove yet",find(root(a),"Remove the football key"));
            press(a,SetupPages.ADD_KEY);
            assertEquals(SettingsActivity.PHONE_FOOTBALL,a.overlay().task());assertEquals(standard,a.sheet().getLayoutParams().width);
            assertNotNull("the TV says it's optional",text(root(a),"Optional. Your team's fixtures already come from TheSportsDB"));
            a.onBackPressed();idle();assertEquals(SettingsOverlay.Phase.DETAIL,a.overlay().page());focused(a,SetupPages.ADD_KEY);
        }
    }
    @Test public void aSavedKeyShowsItsUseAndCanBeRemoved()throws Exception{
        FixtureFeed.saveKey(RuntimeEnvironment.getApplication(),"0123456789abcdef0123456789abcdef");
        FixtureFeed.saveTeam(RuntimeEnvironment.getApplication(),new Fixtures.Team("sdb:133616","Tottenham Hotspur","TOT",0xFF132257,0xFFFFFFFF,Fixtures.Sport.FOOTBALL,null,""));
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();open(a,"Smart Home");
            assertNotNull(text(root(a),"API-Football: key saved · 0 of 100 requests used today"));assertNull(find(root(a),SetupPages.ADD_KEY));
            assertNotNull("says plainly why TheSportsDB is used",text(root(a),"TheSportsDB doesn't give API-Football's number for Tottenham Hotspur"));
            press(a,"Remove the football key");
            AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(String.valueOf(Shadows.shadowOf(d).getMessage()).contains("Fixtures carry on from TheSportsDB"));
            d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();idle();
            assertFalse(FixtureFeed.apiConfigured(a));assertEquals("the team stays","sdb:133616",FixtureFeed.team(a).id);assertNotNull(find(root(a),SetupPages.ADD_KEY));
        }
    }
}
