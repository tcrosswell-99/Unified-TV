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
    /** OD-26: NordVPN's own word, connected or not, is how Live TV tells NordVPN's VPN from another app's. */
    @Test public void nordVpnsOwnWordConnectedOrNot() {
        assertEquals(Boolean.TRUE, NordCountry.status("Connected to United Kingdom #2236"));
        assertEquals("connected without a country it can read", Boolean.TRUE, NordCountry.status("NordVPN", "VPN connected"));
        assertEquals(Boolean.FALSE, NordCountry.status("Disconnected"));
        assertEquals(Boolean.FALSE, NordCountry.status("Connecting to United Kingdom #2236"));
        assertEquals(Boolean.FALSE, NordCountry.status("VPN paused for 5 minutes"));
        assertNull("a promotion says neither", NordCountry.status("Get 3 extra months free"));
        assertNull(NordCountry.status((CharSequence) null));
        // Review 2: negations first; "connected" inside a negative never counts.
        for (String[] no : new String[][]{{"NordVPN", "Not connected"}, {"You're not connected to VPN"}, {"You’re not connected to United Kingdom"},
                {"VPN isn't connected"}, {"Unprotected", "Connected devices"}, {"No connection"}, {"Disconnected from United Kingdom #2236"}})
            assertEquals(java.util.Arrays.toString(no), Boolean.FALSE, NordCountry.status(no));
        assertNull("not connected to a country is never a flag", NordCountry.from("Not connected to United Kingdom #2236"));
        assertEquals(Boolean.TRUE, NordCountry.status("Connected"));
        assertEquals(Boolean.TRUE, NordCountry.status("NordVPN", "VPN connected."));
        assertNull("unsure wording is not connected", NordCountry.status("NordVPN", "Auto-connect is on and connected devices are safe"));
        assertNull(NordCountry.status("Connected devices: 3"));
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
