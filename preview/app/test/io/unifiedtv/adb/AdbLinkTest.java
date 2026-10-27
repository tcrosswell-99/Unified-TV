package io.unifiedtv.adb;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/**
 * The Optional clean-up's ADB client against a fake adbd running in this test on a loopback socket (plain java.net,
 * nothing desktop-only): message framing (little-endian header, checksum, magic), AUTH with the token signature and the
 * public key in adbd's format, the TV's prompt, shell streams with WRTE/OKAY/CLSE, and the command allowlist.
 *
 * <p>The fake checks signatures the way adbd does: s^e mod n must be PKCS#1 v1.5 type 1 padding around SHA-1's DigestInfo
 * and the token itself (RSA_verify with NID_sha1 over a 20-byte token). It reads the public key from the mincrypt struct.
 */
public class AdbLinkTest {
    private static KeyPair pair;
    private AdbKey key;
    private FakeAdbd adbd;

    @Before public void setUp() throws Exception {
        synchronized (AdbLinkTest.class) {
            if (pair == null) { KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)); pair = g.generateKeyPair(); }
        }
        key = AdbKey.of(pair.getPrivate(), (RSAPublicKey) pair.getPublic());
        adbd = new FakeAdbd();
    }
    @After public void tearDown() { adbd.close(); if (adbd.failure != null) throw adbd.failure; }

    // ---------------------------------------------------------------- the key
    @Test public void publicKeyIsAdbdsMincryptStructInBase64() {
        String line = key.publicKeyLine();
        assertTrue("ends with the name the TV's prompt shows", line.endsWith(" " + AdbKey.NAME));
        String b64 = line.substring(0, line.indexOf(' '));
        assertFalse("one line", b64.contains("\n") || b64.contains("\r"));
        byte[] raw = Base64.getDecoder().decode(b64);
        assertEquals("len + n0inv + n[64] + rr[64] + exponent", 524, raw.length);
        assertEquals(AdbKey.structBytes(), raw.length);
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("64 words", 64, b.getInt());
        long n0inv = b.getInt() & 0xffffffffL;
        BigInteger n = words(b), rr = words(b);
        assertEquals(65537, b.getInt());
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        assertEquals("the modulus, little-endian words", pub.getModulus(), n);
        assertEquals("n0inv * n[0] = -1 mod 2^32", 0xffffffffL, (n0inv * (n.longValue() & 0xffffffffL)) & 0xffffffffL);
        assertEquals("rr = (2^2048)^2 mod n", BigInteger.ONE.shiftLeft(4096).mod(n), rr);
        assertEquals("the payload is the line and a NUL", line + "\0", new String(key.publicKeyPayload(), StandardCharsets.UTF_8));
    }

    @Test public void tokenIsSignedAsSha1DigestInfoWithPkcs1Padding() throws Exception {
        byte[] token = new byte[20]; new SecureRandom().nextBytes(token);
        assertTrue("adbd's check", FakeAdbd.verify((RSAPublicKey) pair.getPublic(), token, key.signToken(token)));
        byte[] other = token.clone(); other[0] ^= 1;
        assertFalse("another token does not verify", FakeAdbd.verify((RSAPublicKey) pair.getPublic(), other, key.signToken(token)));
        try { key.signToken(new byte[19]); fail("only a 20-byte token is signed"); } catch (IllegalArgumentException expected) { /* refused */ }
    }

    @Test public void onlyA2048BitKeyIsAccepted() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(1024);
        KeyPair small = g.generateKeyPair();
        try { AdbKey.of(small.getPrivate(), (RSAPublicKey) small.getPublic()); fail("adbd wants 2048 bits"); } catch (IllegalArgumentException expected) { /* refused */ }
    }

    // ---------------------------------------------------------------- the handshake
    @Test public void firstTimeTheKeyIsOfferedAndTheTvAsks() throws Exception {
        adbd.allowOnPrompt = true;
        int[] prompts = {0};
        try (AdbLink link = AdbLink.connect(adbd.port(), key, () -> prompts[0]++, 5000)) {
            assertEquals("the TV's prompt was shown once", 1, prompts[0]);
            assertEquals("the TV now knows the key", 1, adbd.known.size());
            assertEquals("BeyondTV4", link.bannerProperty("ro.product.device"));
            assertEquals("", link.bannerProperty("nothing"));
        }
        assertEquals("CNXN, AUTH signature, AUTH public key", Arrays.asList("CNXN", "AUTH2", "AUTH3"), adbd.seen.subList(0, 3));
        assertEquals("the host announced itself", "host::\0", adbd.hostBanner);
    }

    @Test public void aKnownKeySignsInWithoutAPrompt() throws Exception {
        adbd.known.add(((RSAPublicKey) pair.getPublic()).getModulus());
        int[] prompts = {0};
        try (AdbLink link = AdbLink.connect(adbd.port(), key, () -> prompts[0]++, 5000)) {
            assertTrue(link.isOpen());
        }
        assertEquals("no prompt", 0, prompts[0]);
        assertFalse("the public key was not sent again", adbd.seen.contains("AUTH3"));
    }

    @Test public void aRefusedPromptIsNotAllowed() throws Exception {
        adbd.allowOnPrompt = false;   // the owner chose Cancel: adbd drops the connection
        try { AdbLink.connect(adbd.port(), key, null, 5000).close(); fail("refused"); }
        catch (AdbException e) { assertEquals(AdbException.Kind.NOT_ALLOWED, e.kind); }
    }

    @Test public void anUnansweredPromptTimesOut() throws Exception {
        adbd.silentOnPrompt = true;
        try { AdbLink.connect(adbd.port(), key, null, 300).close(); fail("timed out"); }
        catch (AdbException e) { assertEquals(AdbException.Kind.NOT_ALLOWED, e.kind); }
    }

    @Test public void pairingOnlyTlsIsNotSpoken() throws Exception {
        adbd.stls = true;
        try { AdbLink.connect(adbd.port(), key, null, 5000).close(); fail("STLS"); }
        catch (AdbException e) { assertEquals(AdbException.Kind.PROTOCOL, e.kind); }
    }

    @Test public void nothingListeningMeansDebuggingIsOff() throws Exception {
        int port; try (ServerSocket s = new ServerSocket(0, 1, AdbLink.loopback())) { port = s.getLocalPort(); }
        try { AdbLink.connect(port, key, null, 5000).close(); fail("closed"); }
        catch (AdbException e) { assertEquals(AdbException.Kind.CLOSED, e.kind); }
    }

    @Test public void theLinkOnlyEverGoesToThisTv() {
        assertTrue(AdbLink.loopback().isLoopbackAddress());
        assertArrayEquals(new byte[]{127, 0, 0, 1}, AdbLink.loopback().getAddress());
        assertEquals("network debugging's port", 5555, AdbLink.PORT);
    }

    // ---------------------------------------------------------------- shell streams
    @Test public void shellReturnsEveryChunkAndAcknowledgesEach() throws Exception {
        adbd.known.add(((RSAPublicKey) pair.getPublic()).getModulus());
        adbd.answers.put("getprop ro.build.fingerprint", Arrays.asList("TCL/BeyondTV6/BeyondTV4:11/", "RP1A.200622.001/AR10:user/release-keys\n"));
        adbd.answers.put("pm disable-user --user 0 com.tcl.bi", Collections.singletonList("Package com.tcl.bi new state: disabled-user\n"));
        try (AdbLink link = AdbLink.connect(adbd.port(), key, null, 5000)) {
            assertEquals("TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR10:user/release-keys\n", link.shell(AdbCommands.FINGERPRINT));
            assertEquals("Package com.tcl.bi new state: disabled-user\n", link.shell(AdbCommands.disable("com.tcl.bi")));
        }
        assertEquals("every WRTE was acknowledged", adbd.wrtes, adbd.acks);
        assertEquals(Arrays.asList("shell:getprop ro.build.fingerprint", "shell:pm disable-user --user 0 com.tcl.bi"), adbd.opened);
        assertTrue("streams were closed", adbd.seen.contains("CLSE"));
    }

    @Test public void aStreamTheTvRefusesIsAnError() throws Exception {
        adbd.known.add(((RSAPublicKey) pair.getPublic()).getModulus());
        adbd.refuseOpen = true;
        try (AdbLink link = AdbLink.connect(adbd.port(), key, null, 5000)) {
            try { link.shell(AdbCommands.PACKAGES); fail("refused"); } catch (AdbException e) { assertEquals(AdbException.Kind.PROTOCOL, e.kind); }
        }
    }

    @Test public void commandsOffTheAllowlistNeverReachTheTv() throws Exception {
        adbd.known.add(((RSAPublicKey) pair.getPublic()).getModulus());
        String[] refused = {"pm uninstall com.tcl.bi", "pm uninstall -k --user 0 com.tcl.bi", "pm hide com.tcl.bi", "pm clear com.tcl.bi", "reboot",
            "su -c id", "pm disable-user --user 0 com.tcl.bi; reboot", "pm disable-user --user 0 com.tcl.bi && reboot", "pm enable com.tcl.bi|sh",
            "pm disable-user com.tcl.bi", "pm disable com.tcl.bi", "settings put global window_animation_scale 0", "dumpsys package com.tcl.bi > /sdcard/x",
            "cmd package set-home-activity com.tcl.bi/.X", "am start -n com.tcl.bi/.X", "rm -rf /data", "pm enable ../x", "pm enable", " pm list packages",
            "pm list packages -d -f", "getprop", "dumpsys wifi", "pm disable-user --user 0 com.tcl.bi\nreboot", "pm grant com.tcl.bi android.permission.X"};
        try (AdbLink link = AdbLink.connect(adbd.port(), key, null, 5000)) {
            for (String c : refused) {
                assertFalse(c, AdbCommands.allowed(c));
                try { link.shell(c); fail("sent: " + c); } catch (AdbException e) { assertEquals(c, AdbException.Kind.REFUSED, e.kind); }
            }
        }
        assertTrue("nothing was opened on the TV: " + adbd.opened, adbd.opened.isEmpty());
        try { AdbCommands.disable("com.tcl.bi; reboot"); fail("a package name with shell syntax"); } catch (IllegalArgumentException expected) { /* refused */ }
    }

    @Test public void theAllowlistIsExactlyTheReadsAndTheTwoChanges() {
        for (String c : new String[]{AdbCommands.FINGERPRINT, AdbCommands.SERIAL, AdbCommands.ADB_SECURE, AdbCommands.ANDROID_ID, AdbCommands.PACKAGES,
            AdbCommands.DISABLED, AdbCommands.ENABLED, AdbCommands.SYSTEM, AdbCommands.HOME, AdbCommands.HOME_CLAIMS, AdbCommands.CHANNEL_APPS,
            AdbCommands.SETTINGS_APPS, AdbCommands.scale("window_animation_scale"), AdbCommands.dumpsys("com.tcl.bi")}) {
            assertTrue(c, AdbCommands.allowed(c)); assertFalse("reads only: " + c, AdbCommands.changes(c));
        }
        assertTrue(AdbCommands.changes(AdbCommands.disable("com.tcl.bi")));
        assertTrue(AdbCommands.changes(AdbCommands.enable("com.tcl.bi")));
        assertFalse("not every setting can be read", AdbCommands.allowed("settings get secure enabled_accessibility_services"));
    }

    /** Build run 99: notification access through the TV's debugging, only for Unified TV's own listener. */
    @Test public void notificationAccessOnlyForUnifiedTvsOwnListener() {
        for (String app : new String[]{"io.unifiedtv.launcher", "io.unifiedtv.launcher.devicecheck", "io.unifiedtv.preview", "io.unifiedtv.preview.devicecheck"}) {
            String c = AdbCommands.allowListener(app);
            assertEquals("cmd notification allow_listener " + app + "/io.unifiedtv.preview.MediaWatch", c);
            assertTrue(c, AdbCommands.allowed(c)); assertTrue("it changes the TV", AdbCommands.changes(c));
        }
        for (String c : new String[]{"cmd notification allow_listener com.evil/io.unifiedtv.preview.MediaWatch",
            "cmd notification allow_listener io.unifiedtv.launcher/com.evil.Listener", "cmd notification allow_listener io.unifiedtv.launcher/io.unifiedtv.preview.MediaWatch; reboot",
            "cmd notification allow_listener io.unifiedtv.launcher", "cmd notification disallow_listener io.unifiedtv.launcher/io.unifiedtv.preview.MediaWatch",
            "cmd notification allow_assistant io.unifiedtv.launcher/io.unifiedtv.preview.MediaWatch"})
            assertFalse(c, AdbCommands.allowed(c));
        try { AdbCommands.allowListener("com.evil"); fail("another app's listener"); } catch (IllegalArgumentException expected) { /* refused */ }
    }

    @Test public void framingIsLittleEndianWithChecksumAndMagic() {
        byte[] data = "host::\0".getBytes(StandardCharsets.UTF_8);
        ByteBuffer h = ByteBuffer.wrap(AdbLink.header(AdbLink.CNXN, AdbLink.VERSION, AdbLink.MAX_DATA, data)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x4e584e43, h.getInt()); assertEquals(0x01000001, h.getInt()); assertEquals(256 * 1024, h.getInt());
        assertEquals(data.length, h.getInt());
        int sum = 0; for (byte x : data) sum += x & 0xff;
        assertEquals(sum, h.getInt());
        assertEquals(~0x4e584e43, h.getInt());
        assertEquals("'CNXN' as ASCII, little-endian", "CNXN", new String(Arrays.copyOf(AdbLink.header(AdbLink.CNXN, 0, 0, new byte[0]), 4), StandardCharsets.US_ASCII));
        assertEquals(255 + 1, AdbLink.checksum(new byte[]{(byte) 0xff, 1}));
    }

    private static BigInteger words(ByteBuffer b) {
        BigInteger v = BigInteger.ZERO;
        for (int i = 0; i < 64; i++) v = v.or(BigInteger.valueOf(b.getInt() & 0xffffffffL).shiftLeft(32 * i));
        return v;
    }

    /** A fake adbd: one connection at a time, the legacy RSA AUTH and shell streams. Checks every header it reads. */
    static final class FakeAdbd implements AutoCloseable {
        final ServerSocket server;
        final Set<BigInteger> known = ConcurrentHashMap.newKeySet();
        final List<String> seen = new CopyOnWriteArrayList<>(), opened = new CopyOnWriteArrayList<>();
        final Map<String, List<String>> answers = new ConcurrentHashMap<>();
        volatile boolean allowOnPrompt = true, silentOnPrompt, stls, refuseOpen;
        volatile String hostBanner;
        volatile int wrtes, acks;
        /** An assertion that failed inside the fake (checked after each test). */
        volatile AssertionError failure;
        private final Thread thread;

        FakeAdbd() throws IOException {
            server = new ServerSocket(0, 4, AdbLink.loopback());
            thread = new Thread(() -> { while (!server.isClosed()) { try (Socket s = server.accept()) { serve(s); }
                catch (AssertionError e) { failure = e; } catch (Exception ignored) { /* the client went away: the next one */ } } }, "fake-adbd");
            thread.setDaemon(true); thread.start();
        }
        int port() { return server.getLocalPort(); }
        @Override public void close() { try { server.close(); } catch (IOException ignored) { /* closed */ } }

        static String name(int command) { return new String(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array(), StandardCharsets.US_ASCII); }

        private void serve(Socket s) throws Exception {
            s.setSoTimeout(10_000);
            DataInputStream in = new DataInputStream(s.getInputStream()); OutputStream out = s.getOutputStream();
            int[] m = read(in); byte[] data = last;
            assertEquals("the first message is CNXN", AdbLink.CNXN, m[0]);
            hostBanner = new String(data, StandardCharsets.UTF_8);
            if (stls) { write(out, AdbLink.STLS, 0x01000000, 0, new byte[0]); return; }
            byte[] token = new byte[20]; new SecureRandom().nextBytes(token);
            write(out, AdbLink.AUTH, AdbLink.AUTH_TOKEN, 0, token);
            boolean in1 = false;
            while (true) {
                m = read(in); data = last;
                assertEquals(AdbLink.AUTH, m[0]);
                if (m[1] == AdbLink.AUTH_SIGNATURE) {
                    for (BigInteger n : known) if (verify(n, token, data)) { in1 = true; break; }
                    if (in1) break;
                    new SecureRandom().nextBytes(token);
                    write(out, AdbLink.AUTH, AdbLink.AUTH_TOKEN, 0, token);
                } else if (m[1] == AdbLink.AUTH_RSAPUBLICKEY) {
                    assertEquals("NUL-terminated", 0, data[data.length - 1]);
                    String line = new String(data, 0, data.length - 1, StandardCharsets.UTF_8);
                    assertTrue(line.endsWith(" " + AdbKey.NAME));
                    ByteBuffer b = ByteBuffer.wrap(Base64.getDecoder().decode(line.substring(0, line.indexOf(' ')))).order(ByteOrder.LITTLE_ENDIAN);
                    b.getInt(); b.getInt(); BigInteger n = words(b);
                    if (silentOnPrompt) { Thread.sleep(3000); return; }
                    if (!allowOnPrompt) return;   // refused: the connection drops
                    known.add(n); break;
                } else fail("unexpected AUTH type " + m[1]);
            }
            write(out, AdbLink.CNXN, 0x01000000, 4096, "device::ro.product.name=BeyondTV6;ro.product.model=Smart TV Pro;ro.product.device=BeyondTV4;\0".getBytes(StandardCharsets.UTF_8));
            int nextRemote = 100;
            while (true) {
                try { m = read(in); } catch (IOException e) { return; }
                data = last;
                if (m[0] == AdbLink.OKAY) { acks++; continue; }
                if (m[0] == AdbLink.CLSE) continue;
                assertEquals(AdbLink.OPEN, m[0]);
                String service = new String(data, StandardCharsets.UTF_8).replace("\0", "");
                opened.add(service);
                int local = m[1];
                if (refuseOpen) { write(out, AdbLink.CLSE, 0, local, new byte[0]); continue; }
                int remote = nextRemote++;
                write(out, AdbLink.OKAY, remote, local, new byte[0]);
                for (String chunk : answers.getOrDefault(service.substring("shell:".length()), Collections.singletonList(""))) {
                    if (chunk.isEmpty()) continue;
                    write(out, AdbLink.WRTE, remote, local, chunk.getBytes(StandardCharsets.UTF_8)); wrtes++;
                    int[] ack = read(in);
                    assertEquals("each WRTE is acknowledged before the next", AdbLink.OKAY, ack[0]);
                    assertEquals(local, ack[1]); assertEquals(remote, ack[2]); acks++;
                }
                write(out, AdbLink.CLSE, remote, local, new byte[0]);
            }
        }

        private byte[] last;
        /** Reads one message, checking magic, length and the checksum the host always sends. */
        private int[] read(DataInputStream in) throws IOException {
            byte[] head = new byte[24]; in.readFully(head);
            ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
            int cmd = b.getInt(), a0 = b.getInt(), a1 = b.getInt(), len = b.getInt(), sum = b.getInt(), magic = b.getInt();
            assertEquals("magic", ~cmd, magic);
            assertTrue("length", len >= 0 && len <= AdbLink.MAX_DATA);
            last = new byte[len]; in.readFully(last);
            assertEquals("checksum", AdbLink.checksum(last), sum);
            String n = name(cmd); seen.add(cmd == AdbLink.AUTH ? n + a0 : n);
            return new int[]{cmd, a0, a1};
        }
        private static void write(OutputStream out, int cmd, int a0, int a1, byte[] data) throws IOException { out.write(AdbLink.header(cmd, a0, a1, data)); out.write(data); out.flush(); }

        static boolean verify(RSAPublicKey key, byte[] token, byte[] sig) { return verify(key.getModulus(), token, sig); }
        /** RSA_verify(NID_sha1, token, 20, sig): s^65537 mod n is 00 01 FF…FF 00 DigestInfo(SHA-1) token. */
        static boolean verify(BigInteger n, byte[] token, byte[] sig) {
            byte[] m = new BigInteger(1, sig).modPow(BigInteger.valueOf(65537), n).toByteArray();
            byte[] em = new byte[256]; int copy = Math.min(m.length, 256); System.arraycopy(m, m.length - copy, em, 256 - copy, copy);
            byte[] t = AdbKey.toSign(token);
            if (em[0] != 0 || em[1] != 1) return false;
            int pad = 256 - 3 - t.length;
            for (int i = 2; i < 2 + pad; i++) if (em[i] != (byte) 0xff) return false;
            if (em[2 + pad] != 0) return false;
            return Arrays.equals(Arrays.copyOfRange(em, 3 + pad, 256), t);
        }
    }
}
