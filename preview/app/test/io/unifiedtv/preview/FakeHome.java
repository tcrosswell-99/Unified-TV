package io.unifiedtv.preview;

import io.unifiedtv.adb.AdbCommands;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Test help for the Home takeover (OD-28): which apps answer Home on a simulated TV, and which one the Home button opens,
 * as Android 11 decides it. The highest priority wins on its own (the TCL's tvlauncher declares priority 2, so Android's
 * own choice can't move Home past it); among equals, the Home set with set-home-activity wins; otherwise Android's
 * chooser. A disabled package answers nothing; Unified TV's own entry answers only while {@link #ours} says it is on.
 */
final class FakeHome {
    static final String CHOOSER = "android/com.android.internal.app.ResolverActivity";
    static final String SETUP = "com.google.android.tungsten.setupwraith/.SetupWraithActivity";
    static final String FALLBACK = "com.android.tv.settings/.FallbackHome";
    final String self, mine;
    /** Component → priority, as the TV's packages declare them. */
    final Map<String, Integer> priority = new LinkedHashMap<>();
    BooleanSupplier ours = () -> true;
    String preferred;

    FakeHome(String self) {
        this.self = self; mine = HomeTakeover.entry(self);
        priority.put(FakeTv.HOME, 2);
        priority.put(mine, 0);
        priority.put(FALLBACK, -1000);
    }

    private static String pkg(String component) { return component.substring(0, component.indexOf('/')); }

    List<String> candidates(FakeTv tv) {
        List<String> out = new ArrayList<>();
        for (String c : priority.keySet()) {
            if (c.equals(mine)) { if (ours.getAsBoolean()) out.add(c); continue; }
            String p = pkg(c);
            if (tv.installed.contains(p) && !tv.disabled.contains(p)) out.add(c);
        }
        return out;
    }

    String resolve(FakeTv tv) {
        List<String> all = candidates(tv);
        if (all.isEmpty()) return null;
        int best = Integer.MIN_VALUE; for (String c : all) best = Math.max(best, priority.get(c));
        List<String> top = new ArrayList<>(); for (String c : all) if (priority.get(c) == best) top.add(c);
        if (top.size() == 1) return top.get(0);
        if (preferred != null && top.contains(preferred)) return preferred;
        return CHOOSER;
    }

    String answer(String c, FakeTv tv) {
        if (c.equals(AdbCommands.HOME)) { String r = resolve(tv); return r == null ? "No activity found\n" : "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n" + r + "\n"; }
        if (c.equals(AdbCommands.HOME_CLAIMS)) {
            List<String> all = candidates(tv);
            StringBuilder b = new StringBuilder(all.size() + " activities found:\n");
            for (int i = 0; i < all.size(); i++) b.append("  Activity #").append(i).append(":\n    priority=").append(priority.get(all.get(i))).append(" preferredOrder=0\n    ").append(all.get(i)).append('\n');
            return b.toString();
        }
        if (c.startsWith("cmd package set-home-activity --user 0 ")) {
            String target = c.substring(39);
            if (!candidates(tv).contains(target)) return "Error: Failed to set default home.\n";
            preferred = target; return "Success\n";
        }
        return null;
    }
}
