package io.unifiedtv.preview;

import org.junit.Test;
import static org.junit.Assert.*;

/** Owner request, 25 October 2026: NordVPN's country as a small round flag beside the shield, from its own notification. */
public class NordCountryTest {
    @Test public void readsTheCountryOnlyWhileNordVpnSaysConnected() {
        assertEquals("GB", NordCountry.from("Connected to United Kingdom #2236", null));
        assertEquals("GB", NordCountry.from("NordVPN", "Connected to United Kingdom #2236"));
        assertEquals("DE", NordCountry.from(null, "You are connected to Germany - P2P"));
        assertEquals("US", NordCountry.from("Connected to United States #9012"));
        assertEquals("NL", NordCountry.from("Connected to Netherlands"));
        assertEquals("a server line beside a connected title", "FR", NordCountry.from("VPN connected", "France #812"));
        assertNull(NordCountry.from("Disconnected", "United Kingdom #2236"));
        assertNull(NordCountry.from("Connecting to United Kingdom #2236"));
        assertNull(NordCountry.from("VPN paused for 5 minutes"));
        assertNull("a promotion is not a connection", NordCountry.from("Get 3 extra months free"));
        assertNull("France alone, without saying connected", NordCountry.from("France #812"));
        assertTrue(NordCountry.stopped("Disconnected")); assertFalse(NordCountry.stopped("Connected to France #1"));
    }
    @Test public void namesBecomeCodesAndFlags() {
        assertEquals("GB", NordCountry.iso("UK")); assertEquals("GB", NordCountry.iso(" united  kingdom ")); assertEquals("US", NordCountry.iso("USA"));
        assertEquals("CH", NordCountry.iso("Switzerland")); assertEquals("KR", NordCountry.iso("South Korea")); assertNull(NordCountry.iso("Atlantis"));
        assertEquals("🇬🇧", NordCountry.flag("GB")); assertEquals(NordCountry.flag("GB"), NordCountry.flag("gb"));
        assertNull(NordCountry.flag("GBR")); assertNull(NordCountry.flag(null));
        assertEquals("United Kingdom", NordCountry.name("GB"));
    }
    @Test public void shownOnlyWhileAndroidReportsAVpn() {
        assertEquals("GB", NordCountry.shown("GB", VpnGate.State.CONNECTED));
        for (VpnGate.State s : new VpnGate.State[]{VpnGate.State.DISCONNECTED, VpnGate.State.CHECKING, VpnGate.State.UNKNOWN}) assertNull(s.name(), NordCountry.shown("GB", s));
    }
}
