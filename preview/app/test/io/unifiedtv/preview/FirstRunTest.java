package io.unifiedtv.preview;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** SET-010 / SET-015: the guided setup's steps, saved progress, when it opens by itself, and the Settings row's words. */
public class FirstRunTest {
    @Test public void fiveStepsInOrder(){
        assertEquals(Arrays.asList("welcome","remote","apps","library","finish"),FirstRun.STEPS);
        assertEquals(FirstRun.REMOTE,FirstRun.next(FirstRun.WELCOME));assertEquals(FirstRun.FINISH,FirstRun.next(FirstRun.LIBRARY));assertNull(FirstRun.next(FirstRun.FINISH));
        assertNull(FirstRun.previous(FirstRun.WELCOME));assertEquals(FirstRun.APPS,FirstRun.previous(FirstRun.LIBRARY));
        assertEquals("an unknown step starts at Welcome",FirstRun.WELCOME,FirstRun.next("nowhere"));
    }
    @Test public void progressIsSavedVersionedAndSurvivesAnythingUnreadable(){
        FirstRun.State s=FirstRun.at(FirstRun.parse(null),FirstRun.APPS);
        assertEquals(FirstRun.IN_PROGRESS,s.status);assertEquals(3,s.number());
        FirstRun.State back=FirstRun.parse(s.write());assertEquals(FirstRun.IN_PROGRESS,back.status);assertEquals(FirstRun.APPS,back.step);
        assertTrue(s.write().contains("\"schemaVersion\":1"));
        for(String bad:new String[]{"","{","[]","{\"status\":\"exploded\",\"step\":\"moon\"}"}){FirstRun.State p=FirstRun.parse(bad);assertEquals(bad,FirstRun.NOT_STARTED,p.status);assertEquals(FirstRun.WELCOME,p.step);}
    }
    @Test public void skippingFinishingAndVisitingAgain(){
        FirstRun.State going=FirstRun.at(FirstRun.parse(null),FirstRun.REMOTE);
        assertEquals(FirstRun.SKIPPED,FirstRun.skipped(going).status);
        assertEquals(FirstRun.COMPLETED,FirstRun.finished().status);
        assertEquals("a visit after finishing doesn't undo it",FirstRun.COMPLETED,FirstRun.at(FirstRun.finished(),FirstRun.REMOTE).status);
        assertEquals("skipping a visit after finishing doesn't undo it either",FirstRun.COMPLETED,FirstRun.skipped(FirstRun.finished()).status);
        assertEquals(FirstRun.IN_PROGRESS,FirstRun.startOver().status);assertEquals(FirstRun.WELCOME,FirstRun.startOver().step);
    }
    @Test public void theSettingsRowSaysWhereItWasLeft(){
        assertEquals("Run the guided setup",FirstRun.status(FirstRun.parse(null)));
        assertEquals("Continue the guided setup (step 4 of 5)",FirstRun.status(FirstRun.at(FirstRun.parse(null),FirstRun.LIBRARY)));
        assertEquals("Run the guided setup again",FirstRun.status(FirstRun.finished()));
        assertEquals("Run the guided setup (skipped earlier)",FirstRun.status(FirstRun.skipped(FirstRun.startOver())));
    }
    @Test public void onlyTheMiddleStepsAreSeparateParts(){
        for(String p:new String[]{FirstRun.REMOTE,FirstRun.APPS,FirstRun.LIBRARY})assertEquals(p,FirstRun.part(p));
        assertNull(FirstRun.part(FirstRun.WELCOME));assertNull(FirstRun.part(FirstRun.FINISH));assertNull(FirstRun.part(null));
    }
    @Test public void opensByItselfOnlyOnAFreshInstallInAdults(){
        MapPrefs p=new MapPrefs();assertTrue("fresh install",FirstRun.shouldStart(p));
        p.edit().putString(Profiles.PREF,Profiles.CHILD).commit();assertFalse("never in Kids",FirstRun.shouldStart(p));
        p=new MapPrefs();p.edit().putString("dock","netflix").commit();assertFalse("an earlier install with its own dock",FirstRun.shouldStart(p));
        p=new MapPrefs();p.edit().putBoolean(HubBuilder.OFFERED,true).commit();assertFalse("the old Build My TV card was already shown",FirstRun.shouldStart(p));
        p=new MapPrefs();FirstRun.save(p,FirstRun.skipped(FirstRun.startOver()));assertFalse("skipped",FirstRun.shouldStart(p));
        p=new MapPrefs();FirstRun.save(p,FirstRun.at(FirstRun.parse(null),FirstRun.APPS));assertFalse("in progress: resumed from Settings, not pushed",FirstRun.shouldStart(p));
    }
}
