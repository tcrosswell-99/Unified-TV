package com.gathin.tvhome;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemeTrustTest {
    private static final String SHA = ThemeTrust.sha256("{\"columns\":6}".getBytes(StandardCharsets.UTF_8));

    @Test public void onlyAndroid10IsChecked() {
        // 8 and 9 have no `content write`, so the tools can only cp; from 11 Android/data is private.
        assertFalse(ThemeTrust.checked(26));
        assertFalse(ThemeTrust.checked(28));
        assertTrue(ThemeTrust.checked(29));
        assertFalse(ThemeTrust.checked(30));
        assertFalse(ThemeTrust.checked(34));
    }

    @Test public void uncheckedVersionsTakeAnyOverlay() {
        assertTrue(ThemeTrust.trusted(28, SHA, null, "{\"columns\":6}", null));
        assertTrue(ThemeTrust.trusted(30, SHA, "other", "{\"columns\":6}", "{}"));
    }

    @Test public void android10TakesWhatTheProviderWrote() {
        assertTrue(ThemeTrust.trusted(29, SHA, SHA, "{\"columns\":6}", null));
    }

    @Test public void android10IgnoresAFileSomeoneElseWrote() {
        assertFalse(ThemeTrust.trusted(29, SHA, null, "{\"columns\":6}", null));
        assertFalse(ThemeTrust.trusted(29, SHA, "0000", "{\"columns\":6}", "{\"columns\":5}"));
        assertFalse(ThemeTrust.trusted(29, SHA, "0000", null, null));
        assertFalse(ThemeTrust.trusted(29, null, null, null, null));
    }

    @Test public void android10KeepsTheThemeItAlreadyApplied() {
        // A theme pushed before the provider recorded anything: the same JSON as the copy Home kept.
        assertTrue(ThemeTrust.trusted(29, SHA, null, "{\"columns\":6}", "{\"columns\":6}"));
    }

    @Test public void theHashIsLowerCaseHexSha256() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ThemeTrust.sha256(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ThemeTrust.sha256("abc".getBytes(StandardCharsets.US_ASCII)));
    }
}
