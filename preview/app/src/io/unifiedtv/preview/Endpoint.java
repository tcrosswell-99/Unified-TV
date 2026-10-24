package io.unifiedtv.preview;

import java.net.URI;
import java.util.Locale;

/** Strict origin and path validation shared by tests and all credential-bearing requests. */
public final class Endpoint {
    private Endpoint() {}
    public static String base(String host, String port, boolean https) {
        String h = host.trim();
        if (h.isEmpty() || h.contains("/") || h.contains("@") || h.contains("?") || h.contains("#") || h.contains("%") || h.matches(".*\\s.*"))
            throw new IllegalArgumentException("Enter a hostname or IP without a URL or path");
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length()-1);
        int p;
        try { p = Integer.parseInt(port.trim()); } catch (Exception e) { throw new IllegalArgumentException("Enter a port from 1 to 65535"); }
        if (p < 1 || p > 65535) throw new IllegalArgumentException("Enter a port from 1 to 65535");
        try {
            URI u = new URI(https ? "https" : "http", null, h, p, null, null, null);
            if (u.getHost() == null) throw new IllegalArgumentException();
            return u.toASCIIString();
        } catch (Exception e) { throw new IllegalArgumentException("Check the hostname or IP"); }
    }
    public static URI resolve(String base, String path) {
        URI origin = URI.create(base), target = origin.resolve(path);
        if (!origin.getScheme().equals(target.getScheme()) || !origin.getAuthority().equals(target.getAuthority()) ||
            !path.startsWith("/") || path.startsWith("//") || target.getUserInfo() != null || target.getFragment() != null)
            throw new IllegalArgumentException("Connection must stay on the configured server");
        return target;
    }
    public static boolean protectedApp(String pkg, String label, String configured) {
        String s = (pkg + " " + label).toLowerCase(Locale.ROOT);
        return (!configured.isEmpty() && configured.equals(pkg)) || s.contains("reamsmart") || s.contains("smarters") || s.contains("live tv");
    }
}
