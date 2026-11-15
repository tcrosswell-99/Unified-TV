package io.unifiedtv.preview;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OD-29 (owner, 3 November 2026: "Unified TV runs the VPN itself"): what Unified TV asks NordVPN for, and the WireGuard
 * (NordLynx) set-up it builds from the answers. Pure: the network comes in through {@link Http}, so all of it is tested
 * with made-up answers (NordLynxTest); nothing here touches Android.
 *
 * <p><b>Unofficial.</b> NordVPN publishes no API for this. Everything below follows what NordVPN's own open-source Linux
 * app (NordSecurity/nordvpn-linux, package core: CredentialsURL "/v1/users/services/credentials", CredentialsResponse
 * "nordlynx_private_key", Country "id"/"name"/"code", Server "station"/"hostname"/"technologies") and several community
 * guides (for example the "NordVPN WireGuard config" gist by bluewalk and its comments, 2023–2026) use. It may change
 * without notice; when it does, the TV says it couldn't reach NordVPN or got no server, and the household can still use
 * NordVPN's own app (docs/VPN_PROTECTION.md).
 *
 * <p>Secrets: the access token is sent once, as HTTP Basic auth (user "token"), only to {@link #API}, and is never kept;
 * the NordLynx private key is returned to the caller to be saved in the Vault and written only into the tunnel's own
 * configuration. Neither is ever logged, shown, put in an exception message or exported.
 */
final class NordLynx {
    private NordLynx() {}

    static final String API = "https://api.nordvpn.com",
        CREDENTIALS = "/v1/users/services/credentials", COUNTRIES = "/v1/servers/countries", RECOMMENDATIONS = "/v1/servers/recommendations";
    /** NordLynx is WireGuard over UDP; the servers list it under this technology identifier. */
    static final String TECHNOLOGY = "wireguard_udp";
    /** WireGuard's own port; every NordLynx guide uses it. */
    static final int PORT = 51820;
    /** The handshake keepalive, in seconds (no polling of our own: WireGuard sends it). */
    static final int KEEPALIVE = 25;
    /** The tunnel address NordLynx gives every client (assumed fixed, as in every guide; NordVPN's servers do the NAT). */
    static final String ADDRESS = "10.5.0.2/32";
    /**
     * NordVPN's own public DNS servers, used inside the tunnel. These are NordVPN service addresses, not anything of the
     * household's (AGENTS.md rule 6). Source: NordVPN's support pages list 103.86.96.100 and 103.86.99.100 as its DNS
     * servers, and the same pair is in every NordLynx guide above.
     */
    static final String[] NORD_DNS = {"103.86.96.100", "103.86.99.100"};
    /**
     * Addresses that stay on the home network, never in the tunnel, so the NAS, Home Assistant, AirPlay and the phone
     * setup page keep working while the VPN is on: the private ranges, link-local, loopback, and multicast (SSDP, mDNS)
     * and the reserved block above it. Everything else, IPv4 and IPv6, goes through NordVPN.
     */
    static final String[] LOCAL_V4 = {"10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"};
    static final String[] LOCAL_V6 = {"::1/128", "fc00::/7", "fe80::/10", "ff00::/8"};

    // ---------------------------------------------------------------- the network, as an interface
    static final class Reply {
        final int status; final String body;
        Reply(int status, String body) { this.status = status; this.body = body == null ? "" : body; }
    }
    interface Http {
        /** One GET; {@code authorization} is a whole Authorization header value, or null. Throws when NordVPN can't be reached. */
        Reply get(String url, String authorization) throws IOException;
    }
    /** The real one: HTTPS to {@link #API} only, no redirects, short timeouts, bounded answers, no caches. */
    static final Http HTTPS = (url, authorization) -> {
        if (!url.startsWith(API + "/")) throw new IOException("Not NordVPN's API");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            if (authorization != null) c.setRequestProperty("Authorization", authorization);
            int status = c.getResponseCode();
            return new Reply(status, status >= 200 && status < 300 ? read(c.getInputStream()) : "");
        } finally { c.disconnect(); }
    };
    static final int LIMIT = 4 * 1024 * 1024;
    static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = i.read(b)) != -1) { if (out.size() + n > LIMIT) throw new IOException("Answer too large"); out.write(b, 0, n); }
            return out.toString("UTF-8");
        }
    }

    // ---------------------------------------------------------------- the key, from the access token
    /** HTTP Basic auth as NordVPN's API takes an access token: the user is the word "token", the password the token. */
    static String basic(String token) {
        return "Basic " + Base64.getEncoder().encodeToString(("token:" + token).getBytes(StandardCharsets.UTF_8));
    }
    /** A WireGuard key: 32 bytes in standard Base64 (44 characters, ending "="). */
    static boolean validKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9+/]{43}=")) return false;
        try { return Base64.getDecoder().decode(key).length == 32; } catch (IllegalArgumentException e) { return false; }
    }
    /** The NordLynx private key in the credentials answer, or null. Nothing else in the answer is kept. */
    static String privateKey(String body) {
        try { String k = new JSONObject(body).optString("nordlynx_private_key", "").trim(); return validKey(k) ? k : null; }
        catch (JSONException | RuntimeException e) { return null; }
    }
    /** The outcome of a token check, and the key only when it worked. */
    static final class KeyResult {
        final PhoneFlows.Verdict verdict; final String key;
        KeyResult(PhoneFlows.Verdict verdict, String key) { this.verdict = verdict; this.key = key; }
    }
    /**
     * One request with the access token: OK with the private key; REFUSED when NordVPN says no (401, 403) or its answer
     * has no usable key; BUSY when it asks to wait (429); OFFLINE otherwise. The token goes nowhere else.
     */
    static KeyResult fetchKey(Http http, String token) {
        if (token == null || token.isEmpty()) return new KeyResult(PhoneFlows.Verdict.REFUSED, null);
        Reply r;
        try { r = http.get(API + CREDENTIALS, basic(token)); } catch (IOException | RuntimeException e) { return new KeyResult(PhoneFlows.Verdict.OFFLINE, null); }
        if (r.status == 401 || r.status == 403) return new KeyResult(PhoneFlows.Verdict.REFUSED, null);
        if (r.status == 429) return new KeyResult(PhoneFlows.Verdict.BUSY, null);
        if (r.status != 200) return new KeyResult(PhoneFlows.Verdict.OFFLINE, null);
        String key = privateKey(r.body);
        return key == null ? new KeyResult(PhoneFlows.Verdict.REFUSED, null) : new KeyResult(PhoneFlows.Verdict.OK, key);
    }

    // ---------------------------------------------------------------- countries and servers
    static final class Country {
        final int id; final String name, code;
        Country(int id, String name, String code) { this.id = id; this.name = name; this.code = code; }
    }
    /** The countries NordVPN offers: id, name and two-letter code; anything malformed is left out. Sorted by name. */
    static List<Country> countries(String body) {
        List<Country> out = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(body);
            for (int i = 0; i < all.length(); i++) {
                JSONObject c = all.optJSONObject(i); if (c == null) continue;
                int id = c.optInt("id", -1); String name = c.optString("name", "").trim(), code = c.optString("code", "").trim().toUpperCase(Locale.ROOT);
                if (id > 0 && !name.isEmpty() && code.matches("[A-Z]{2}")) out.add(new Country(id, name, code));
            }
        } catch (JSONException | RuntimeException ignored) {}
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }
    /** The short list kept on the TV for a week (no cities, nothing personal). */
    static String countriesJson(List<Country> list) {
        JSONArray a = new JSONArray();
        try { for (Country c : list) a.put(new JSONObject().put("id", c.id).put("name", c.name).put("code", c.code)); } catch (JSONException ignored) {}
        return a.toString();
    }
    static Country country(List<Country> list, String code) {
        if (code == null) return null;
        for (Country c : list) if (c.code.equalsIgnoreCase(code)) return c;
        return null;
    }

    static final class Server {
        final String name, hostname, station, publicKey, country; final int load;
        Server(String name, String hostname, String station, String publicKey, String country, int load) {
            this.name = name; this.hostname = hostname; this.station = station; this.publicKey = publicKey; this.country = country; this.load = load;
        }
        /** Where the tunnel goes: the server's address when NordVPN gave one (no DNS needed), else its name. */
        String endpoint() { return (station != null && !station.isEmpty() ? station : hostname) + ":" + PORT; }
    }
    private static final String IPV4 = "(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    /**
     * The recommended servers that offer NordLynx, in NordVPN's own order: name ("United Kingdom #1234"), host name,
     * address (station), the server's WireGuard public key (technologies[] with identifier wireguard_udp, metadata
     * name "public_key") and its country code. A server without a usable key or address is left out.
     */
    static List<Server> servers(String body) {
        List<Server> out = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(body);
            for (int i = 0; i < all.length(); i++) {
                JSONObject s = all.optJSONObject(i); if (s == null) continue;
                String host = s.optString("hostname", "").trim().toLowerCase(Locale.ROOT), station = s.optString("station", "").trim();
                if (!host.matches("[a-z0-9][a-z0-9.-]{1,252}")) host = "";
                if (!station.matches(IPV4)) station = "";
                if (host.isEmpty() && station.isEmpty()) continue;
                String key = null;
                JSONArray tech = s.optJSONArray("technologies");
                for (int t = 0; tech != null && t < tech.length() && key == null; t++) {
                    JSONObject x = tech.optJSONObject(t); if (x == null || !TECHNOLOGY.equals(x.optString("identifier"))) continue;
                    JSONArray meta = x.optJSONArray("metadata");
                    for (int m = 0; meta != null && m < meta.length(); m++) {
                        JSONObject e = meta.optJSONObject(m);
                        if (e != null && "public_key".equals(e.optString("name")) && validKey(e.optString("value").trim())) { key = e.optString("value").trim(); break; }
                    }
                }
                if (key == null) continue;
                String code = "";
                JSONArray where = s.optJSONArray("locations");
                JSONObject first = where == null ? null : where.optJSONObject(0), country = first == null ? null : first.optJSONObject("country");
                if (country != null) code = country.optString("code", "").trim().toUpperCase(Locale.ROOT);
                if (!code.matches("[A-Z]{2}")) code = "";
                String name = s.optString("name", "").trim();
                out.add(new Server(name.isEmpty() ? host : name, host, station, key, code, s.optInt("load", -1)));
            }
        } catch (JSONException | RuntimeException ignored) {}
        return out;
    }
    /** NordVPN's recommended NordLynx servers, in a country (its NordVPN id) or, with null, wherever NordVPN suggests. */
    static String recommendations(Integer countryId, int limit) {
        // The brackets are percent-encoded: filters[servers_technologies][identifier]=wireguard_udp&filters[country_id]=<id>.
        return API + RECOMMENDATIONS + "?filters%5Bservers_technologies%5D%5Bidentifier%5D=" + TECHNOLOGY
            + (countryId == null ? "" : "&filters%5Bcountry_id%5D=" + countryId) + "&limit=" + Math.max(1, Math.min(limit, 10));
    }

    // ---------------------------------------------------------------- the tunnel's configuration
    /**
     * The tunnel in WireGuard's own configuration format (wg-quick), for the WireGuard library to parse. It holds the
     * private key, so it is handed straight to the tunnel and never logged, stored or shown.
     */
    static String config(String privateKey, Server server) {
        if (!validKey(privateKey) || server == null || !validKey(server.publicKey)) throw new IllegalArgumentException("incomplete");
        return "[Interface]\nPrivateKey = " + privateKey + "\nAddress = " + ADDRESS + "\nDNS = " + String.join(", ", NORD_DNS) + "\n\n"
            + "[Peer]\nPublicKey = " + server.publicKey + "\nAllowedIPs = " + String.join(", ", allowedIps()) + "\nEndpoint = " + server.endpoint()
            + "\nPersistentKeepalive = " + KEEPALIVE + "\n";
    }
    /** Everything (0.0.0.0/0 and ::/0) except {@link #LOCAL_V4} and {@link #LOCAL_V6}, as the fewest CIDR blocks. */
    static List<String> allowedIps() {
        List<String> out = new ArrayList<>(except("0.0.0.0/0", LOCAL_V4));
        out.addAll(except("::/0", LOCAL_V6));
        return out;
    }

    /** {@code all} minus each of {@code out}, as CIDR blocks (one address family). */
    static List<String> except(String all, String... out) {
        Block whole = Block.parse(all);
        List<BigInteger[]> ranges = new ArrayList<>(); ranges.add(new BigInteger[]{whole.first(), whole.last()});
        for (String o : out) {
            Block b = Block.parse(o); if (b.bits != whole.bits) throw new IllegalArgumentException("mixed families");
            List<BigInteger[]> next = new ArrayList<>();
            for (BigInteger[] r : ranges) {
                if (b.last().compareTo(r[0]) < 0 || b.first().compareTo(r[1]) > 0) { next.add(r); continue; }
                if (b.first().compareTo(r[0]) > 0) next.add(new BigInteger[]{r[0], b.first().subtract(BigInteger.ONE)});
                if (b.last().compareTo(r[1]) < 0) next.add(new BigInteger[]{b.last().add(BigInteger.ONE), r[1]});
            }
            ranges = next;
        }
        List<String> cidrs = new ArrayList<>();
        for (BigInteger[] r : ranges) {
            BigInteger start = r[0];
            while (start.compareTo(r[1]) <= 0) {
                int size = start.signum() == 0 ? whole.bits : Math.min(start.getLowestSetBit(), whole.bits);
                while (start.add(BigInteger.ONE.shiftLeft(size)).subtract(BigInteger.ONE).compareTo(r[1]) > 0) size--;
                cidrs.add(Block.format(start, whole.bits) + "/" + (whole.bits - size));
                start = start.add(BigInteger.ONE.shiftLeft(size));
            }
        }
        return cidrs;
    }
    /** Whether {@code address} (a literal) is inside the block {@code cidr}; false for another family. */
    static boolean contains(String cidr, String address) {
        Block b = Block.parse(cidr); byte[] raw = literal(address);
        if (raw.length * 8 != b.bits) return false;
        BigInteger a = new BigInteger(1, raw);
        return a.compareTo(b.first()) >= 0 && a.compareTo(b.last()) <= 0;
    }
    private static byte[] literal(String address) {
        String a = address.trim();
        if (!a.matches("[0-9a-fA-F:.]+") || a.isEmpty()) throw new IllegalArgumentException("not an address literal");
        try { return InetAddress.getByName(a).getAddress(); } catch (IOException e) { throw new IllegalArgumentException("not an address literal"); }
    }
    static final class Block {
        final BigInteger base; final int bits, prefix;
        private Block(BigInteger base, int bits, int prefix) { this.base = base; this.bits = bits; this.prefix = prefix; }
        static Block parse(String cidr) {
            int slash = cidr.indexOf('/'); if (slash < 0) throw new IllegalArgumentException("no prefix");
            byte[] raw = literal(cidr.substring(0, slash)); int bits = raw.length * 8, prefix = Integer.parseInt(cidr.substring(slash + 1));
            if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("bad prefix");
            BigInteger mask = BigInteger.ONE.shiftLeft(bits - prefix).subtract(BigInteger.ONE);
            return new Block(new BigInteger(1, raw).andNot(mask), bits, prefix);
        }
        BigInteger first() { return base; }
        BigInteger last() { return base.add(BigInteger.ONE.shiftLeft(bits - prefix)).subtract(BigInteger.ONE); }
        BigInteger size() { return BigInteger.ONE.shiftLeft(bits - prefix); }
        static String format(BigInteger value, int bits) {
            if (bits == 32) { long v = value.longValue(); return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255); }
            int[] g = new int[8];
            for (int i = 0; i < 8; i++) g[i] = value.shiftRight(16 * (7 - i)).intValue() & 0xffff;
            int bestAt = -1, bestLen = 1;   // compress the longest run of two or more zero groups
            for (int i = 0; i < 8; ) { if (g[i] != 0) { i++; continue; } int j = i; while (j < 8 && g[j] == 0) j++; if (j - i > bestLen) { bestAt = i; bestLen = j - i; } i = j; }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i == bestAt) { b.append(i == 0 ? "::" : ":"); i += bestLen - 1; continue; }
                b.append(Integer.toHexString(g[i])); if (i < 7) b.append(':');
            }
            return b.toString();
        }
    }
}
