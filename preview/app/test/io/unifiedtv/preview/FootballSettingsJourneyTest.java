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
    static final String HEARTS="{\"teams\":["+SportsDbTest.team(133647,"Heart of Midlothian","HEA","Hearts","Soccer",254,"#7A263A","#FFFFFF")+"]}";
    @Before public void start()throws Exception{
        RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview",0).edit().clear().commit();
        RuntimeEnvironment.getApplication().getSharedPreferences("vault",0).edit().clear().commit();
        Vault.testKey=VaultTest.aes();SportsDb.paceMs=0;
        SportsDb.http=path->{sent.add(path);if(!SportsDb.allowed(path))throw new AssertionError(path);
            return new JSONObject(path.contains("English%20Premier%20League")?SportsDbTest.PREMIER:path.contains("Scottish%20Premiership")?HEARTS:"{\"teams\":null}");};
        ApiFootball.http=(key,path)->{apiSent.add(path);throw new AssertionError("API-Football asked without a key: "+path);};
    }
    @After public void stop()throws Exception{
        waitForList();SportsDb.http=SportsDb::fetch;ApiFootball.http=ApiFootball::get;SportsDb.paceMs=2_500;Vault.testKey=null;
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

    @Test public void pickATeamByLeagueWithNoKeyAndNoPhone()throws Exception{
        try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity a=c.get();int standard=a.sheet().getLayoutParams().width;open(a,"Smart Home");
            assertNotNull(text(root(a),"Football fixtures: choose your team"));
            assertNotNull("the default needs no account",text(root(a),SetupPages.NO_ACCOUNT));
            press(a,"Your team: choose from the UK teams");
            assertEquals(SettingsActivity.TEAM,a.overlay().task());assertEquals("inside the same sheet",standard,a.sheet().getLayoutParams().width);
            waitForList();
            assertNotNull("England's leagues, from TheSportsDB",find(root(a),"Premier League · 3 teams"));
            assertNotNull("Scotland's, with the other name tried",find(root(a),"Premiership · 1 team"));
            assertNotNull(text(root(a),SportsDb.ATTRIBUTION));
            press(a,"Premier League · 3 teams");
            assertNotNull(text(root(a),"England: Premier League"));
            press(a,"Tottenham Hotspur");
            assertEquals("back to Smart Home",SettingsOverlay.Phase.DETAIL,a.overlay().page());
            Fixtures.Team t=FixtureFeed.team(a);
            assertEquals("sdb:133616",t.id);assertEquals("api:47",t.alt);assertEquals("TOT",t.shortName);
            assertEquals("the club's own colours for the drawn badge",0xFF132257,t.primary);assertEquals(0xFFFFFFFF,t.secondary);
            assertNotNull(text(root(a),"Football fixtures: Tottenham Hotspur"));assertNotNull(find(root(a),"Badge: TOT on Navy and White"));
            assertNull("a listed team has no calendar names",text(root(a),"Other names in the calendar"));
            assertFalse("no key was asked for",FixtureFeed.apiConfigured(a));assertTrue(apiSent.isEmpty());
            for(String r:new ArrayList<>(sent))assertTrue("only league names were sent: "+r,r.startsWith("search_all_teams.php?l="));
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
            press(a,"Heart of Midlothian · Premiership");
            assertEquals("sdb:133647",FixtureFeed.team(a).id);assertNotNull(text(root(a),"Football fixtures: Heart of Midlothian"));
            press(a,"No team");assertNull(FixtureFeed.team(a));assertNotNull(text(root(a),"Football fixtures: choose your team"));
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
