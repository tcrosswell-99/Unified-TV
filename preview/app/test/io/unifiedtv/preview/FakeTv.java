package io.unifiedtv.preview;

import io.unifiedtv.adb.AdbCommands;
import java.io.Closeable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Test help for the Optional clean-up: a simulated TCL 50C725K answering the allowlisted shell commands from its state,
 * as Cleanup.Shell. Anything off the allowlist fails the test at once (the real link refuses it too). It records every
 * command, so a test can tell a read-only check from a change.
 */
final class FakeTv implements Cleanup.Shell, Closeable {
    static final String OWNER = "TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR10:user/release-keys";
    static final String SONY = "Sony/BRAVIA_VH2_EU/BRAVIA_VH2:12/STT1.230201.001/123:user/release-keys";
    static final String HOME = "com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity";

    String fingerprint = OWNER, serial = "TESTSERIAL0001", androidId = "a1b2c3d4e5f60718", adbSecure = "1", home = HOME;
    final Set<String> installed = new LinkedHashSet<>(), disabled = new LinkedHashSet<>(), homeClaims = new LinkedHashSet<>(),
        channelApps = new LinkedHashSet<>(), settingsApps = new LinkedHashSet<>(), inputDrivers = new LinkedHashSet<>();
    final Map<String, String> scales = new HashMap<>();
    final List<String> commands = new ArrayList<>();
    /** Runs before each disable command (a test checks the record is already on disk). */
    Runnable beforeChange;
    boolean closed;

    FakeTv() {
        installed.addAll(Arrays.asList("android", "com.android.systemui", "com.google.android.tvlauncher", "com.tcl.tv", "com.tcl.tvinput", "com.tcl.settings",
            "com.tcl.bi", "com.tcl.esticker", "com.tcl.messagebox", "com.tcl.channelplus", "com.tcl.overseasappshow", "com.tcl.tv.tclhome_passive",
            "com.google.android.feedback", "com.google.android.partnersetup", "com.tcl.dashboard", "com.google.android.katniss", "com.google.android.marvin.talkback",
            "com.google.android.tts", "com.netflix.ninja", "com.tcl.browser", "com.tcl.gallery", "com.google.android.tungsten.setupwraith", "com.tcl.MultiScreenInteraction_TV",
            "com.tcl.guard", "com.tcl.notereminder"));
        disabled.add("com.tcl.notereminder");   // already off before the clean-up
        homeClaims.addAll(Arrays.asList("com.google.android.tvlauncher", "com.tcl.keycustomfunctionservice"));
        channelApps.add("com.tcl.tv"); settingsApps.add("com.tcl.settings");
        scales.put("window_animation_scale", "1.0"); scales.put("transition_animation_scale", "1.0"); scales.put("animator_duration_scale", "null");
    }

    /** The bundled lists, read from the source tree (the local test runner doesn't package assets). */
    static String lists() {
        try { return new String(Files.readAllBytes(new File(SidebarRuleTest.app(), "src/main/assets/" + CleanupLists.ASSET).toPath()), StandardCharsets.UTF_8); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    List<String> changes() { List<String> out = new ArrayList<>(); for (String c : commands) if (AdbCommands.changes(c)) out.add(c); return out; }

    @Override public String run(String c) {
        if (!AdbCommands.allowed(c)) throw new AssertionError("not on the allowlist: " + c);
        commands.add(c);
        if (c.equals(AdbCommands.FINGERPRINT)) return fingerprint + "\n";
        if (c.equals(AdbCommands.SERIAL)) return serial + "\n";
        if (c.equals(AdbCommands.ANDROID_ID)) return androidId + "\n";
        if (c.equals(AdbCommands.ADB_SECURE)) return adbSecure + "\n";
        if (c.equals(AdbCommands.PACKAGES)) return list(installed);
        if (c.equals(AdbCommands.DISABLED)) return list(disabled);
        if (c.equals(AdbCommands.HOME)) return home == null ? "android\n" : "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n" + home + "\n";
        if (c.equals(AdbCommands.HOME_CLAIMS)) return brief(homeClaims);
        if (c.equals(AdbCommands.CHANNEL_APPS)) return brief(channelApps);
        if (c.equals(AdbCommands.SETTINGS_APPS)) return brief(settingsApps);
        if (c.startsWith("settings get global ")) return scales.getOrDefault(c.substring(20), "null") + "\n";
        if (c.startsWith("dumpsys package ")) {
            String p = c.substring(16);
            return "Packages:\n  Package [" + p + "] (1a2b3c):\n    versionCode=12 minSdk=28 targetSdk=30\n    versionName=1.2." + p.length() + "\n"
                + "    requested permissions:\n      android.permission.INTERNET\n" + (inputDrivers.contains(p) ? "      android.permission.TV_INPUT_HARDWARE\n" : "")
                + "    install permissions:\n      android.permission.INTERNET: granted=true\n    User 0: ceDataInode=1 installed=true\n";
        }
        if (c.startsWith("pm disable-user --user 0 ")) {
            if (beforeChange != null) beforeChange.run();
            String p = c.substring(25);
            if (!installed.contains(p)) return "Error: Unknown package: " + p + "\n";
            disabled.add(p); return "Package " + p + " new state: disabled-user\n";
        }
        if (c.startsWith("pm enable ")) { String p = c.substring(10); disabled.remove(p); return "Package " + p + " new state: enabled\n"; }
        return "";
    }
    private static String list(Collection<String> pkgs) { StringBuilder b = new StringBuilder(); for (String p : pkgs) b.append("package:").append(p).append('\n'); return b.toString(); }
    private static String brief(Collection<String> pkgs) { StringBuilder b = new StringBuilder("priority=0 preferredOrder=0\n"); for (String p : pkgs) b.append("  ").append(p).append("/.Main\n"); return b.toString(); }
    @Override public void close() { closed = true; }
}
