package io.unifiedtv.preview;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSpeechRecognizer;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

/**
 * Owner's TCL, 25 October 2026: "I didn't catch that" twice. A remote's microphone often reaches only the TV's own voice
 * screen, so when the in-pane recogniser hears nothing the pane hands over to it by itself and uses its words; partial
 * words already heard are used rather than thrown away. No assistant set up: the pane offers phone setup.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30)
public class VoiceFallbackTest {
    @Before public void speechServiceAndMicrophone() {
        ComponentName service = new ComponentName("com.example.speech", "com.example.speech.Service");
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).addServiceIfNotPresent(service);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).addIntentFilterForService(service, new IntentFilter(RecognitionService.SERVICE_INTERFACE));
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO);
        prefs().edit().clear().commit();
    }
    @After public void clean() { prefs().edit().clear().commit(); ShadowSpeechRecognizer.reset(); }
    private static android.content.SharedPreferences prefs() { return RuntimeEnvironment.getApplication().getSharedPreferences("unified-tv-preview", 0); }
    private static Button find(View v, String start) { if (v instanceof Button && ((Button) v).getText().toString().startsWith(start)) return (Button) v; if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Button b = find(((ViewGroup) v).getChildAt(i), start); if (b != null) return b; } return null; }
    private static String words(View v) { StringBuilder b = new StringBuilder(); if (v instanceof TextView) b.append(((TextView) v).getText()).append(' '); if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(words(((ViewGroup) v).getChildAt(i))); return b.toString(); }
    private static Bundle heard(String... w) { Bundle b = new Bundle(); b.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(Arrays.asList(w))); return b; }
    private static ShadowSpeechRecognizer recogniser() { return Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()); }
    /** Speak, then let the recogniser's posted set-up (listener, start) run, as it does on the TV's main thread. */
    private static void speak(VoiceActivity v) { find(v.findViewById(android.R.id.content), "Speak").performClick(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); }

    @Test public void whichErrorsHandOverToTheTvsOwnVoiceScreen() {
        for (int e : new int[]{SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_CLIENT}) assertTrue("" + e, VoiceActivity.fallBack(e));
        for (int e : new int[]{SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, SpeechRecognizer.ERROR_RECOGNIZER_BUSY}) assertFalse("" + e, VoiceActivity.fallBack(e));
        assertEquals("open netflix", VoiceActivity.words(" open netflix ", "open"));
        assertEquals("partial words are kept", "open bbc", VoiceActivity.words("", "open bbc"));
        assertEquals("", VoiceActivity.words(null, null));
    }

    @Test public void nothingHeardInThePaneOpensTheTvsVoiceScreenAndUsesItsWords() {
        try (ActivityController<VoiceActivity> c = Robolectric.buildActivity(VoiceActivity.class).setup()) {
            VoiceActivity v = c.get(); speak(v); assertTrue(v.listening());
            recogniser().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH);
            org.robolectric.shadows.ShadowActivity.IntentForResult asked = Shadows.shadowOf(v).getNextStartedActivityForResult();
            if (asked == null) { Intent started = Shadows.shadowOf(v).getNextStartedActivity(); fail("expected the TV's voice screen, got " + started); }
            assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, asked.intent.getAction());
            assertFalse("the pane stays over Home", v.isFinishing());
            Intent result = new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, new ArrayList<>(Arrays.asList("open netflix")));
            Shadows.shadowOf(v).receiveResult(asked.intent, Activity.RESULT_OK, result);
            assertEquals("“open netflix”", v.stateText()); assertTrue(v.answerText(), v.answerText().contains("Netflix isn't on this TV"));
            assertTrue("this TV goes straight to its own voice screen next time", prefs().getBoolean(VoiceActivity.SYSTEM_FIRST, false));
        }
    }

    @Test public void wordsAlreadyHeardAreUsedNotThrownAway() {
        try (ActivityController<VoiceActivity> c = Robolectric.buildActivity(VoiceActivity.class).setup()) {
            VoiceActivity v = c.get(); speak(v);
            recogniser().triggerOnPartialResults(heard("open bbc"));
            recogniser().triggerOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
            assertNull("no hand-over needed", Shadows.shadowOf(v).getNextStartedActivityForResult());
            assertEquals("“open bbc”", v.stateText());
        }
    }

    @Test public void theTvsVoiceScreenHearingNothingIsSaidPlainly() {
        try (ActivityController<VoiceActivity> c = Robolectric.buildActivity(VoiceActivity.class).setup()) {
            VoiceActivity v = c.get(); speak(v);
            recogniser().triggerOnResults(heard(""));
            org.robolectric.shadows.ShadowActivity.IntentForResult asked = Shadows.shadowOf(v).getNextStartedActivityForResult();
            assertNotNull("empty results hand over too", asked);
            Shadows.shadowOf(v).receiveResult(asked.intent, Activity.RESULT_CANCELED, null);
            assertEquals("I didn't catch that.", v.stateText()); assertFalse(prefs().getBoolean(VoiceActivity.SYSTEM_FIRST, false));
        }
    }

    @Test public void withNoAssistantThePaneOffersPhoneSetupAndClaimsNothing() {
        try (ActivityController<VoiceActivity> c = Robolectric.buildActivity(VoiceActivity.class).setup()) {
            VoiceActivity v = c.get(); View root = v.findViewById(android.R.id.content);
            String all = words(root); assertTrue(all, all.contains(Assistant.NOT_SET_UP));
            for (String claim : new String[]{"ChatGPT", "GPT", "Claude", "OpenAI"}) assertFalse(claim, all.contains(claim));
            Button setup = find(root, VoiceActivity.SET_UP); assertNotNull(setup); setup.performClick();
            Intent next = Shadows.shadowOf(v).getNextStartedActivity();
            assertEquals(SettingsActivity.class.getName(), next.getComponent().getClassName()); assertEquals("phone-" + PhoneService.ASSISTANT, next.getStringExtra("task"));
            assertTrue(SetupPages.owns(next.getStringExtra("task")));
        }
        prefs().edit().putBoolean(Assistant.CONFIGURED, true).commit();
        try (ActivityController<VoiceActivity> c = Robolectric.buildActivity(VoiceActivity.class).setup()) {
            View root = c.get().findViewById(android.R.id.content);
            assertNull(find(root, VoiceActivity.SET_UP)); assertTrue(words(root).contains("not tested yet"));
        }
    }
}
