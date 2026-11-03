package io.unifiedtv.adb;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;

/**
 * A minimal client for the TV's own ADB, for the Optional clean-up only (SPEC "Optional TV Performance & Cleanup"). It
 * talks only to this TV, at the loopback address on port 5555, where network debugging listens once the owner has turned
 * it on; it never connects anywhere else. Written in plain Java from the ADB protocol description (system/core/adb
 * protocol.txt); no ADB library code is used.
 *
 * <p>What it does: CNXN, then AUTH (sign adbd's token; if the TV doesn't know the key yet, send the public key, and the TV
 * shows its own "Allow USB debugging?" prompt), then one {@code shell:} stream at a time (OPEN, WRTE/OKAY, CLSE). Only
 * the commands in {@link AdbCommands} can be sent. The link lives as long as the clean-up page that opened it, and is
 * closed when that page closes: nothing keeps ADB open afterwards.
 *
 * <p>Every wait has a hard limit (owner's Sony BRAVIA, Android TV 10, 3 November 2026: the page waited after the TV's
 * prompt): connecting {@link #CONNECT_MS}, each answer during sign-in {@link #ANSWER_MS}, the owner's Allow
 * {@link #PROMPT_MS}, and each command {@link #COMMAND_MS} from OPEN to its end, not per message. A failure carries the
 * {@link Step} it happened at, and a {@link Trace} hears each step (never a token, key, signature or output).
 *
 * <p>What real adbd builds do that this handles: after the public key, adbd may send a fresh TOKEN once the owner allows
 * it (it is signed, once); adbd may drop the connection once the owner allows it, expecting a new one (one reconnect,
 * signature only, never the key or a second prompt); the TV's maxdata from its CNXN (4096 on old builds) caps what is
 * sent; a stream closed with CLSE before OKAY was refused; a stray WRTE for an earlier stream is closed, not left waiting;
 * an old adbd (protocol 0x01000000) that never closes a finished shell stream ends it after a quiet pause; the banner's
 * shape varies ("device::…", "device:serial:…", no trailing ';').
 *
 * <p>Never logged: no command output, token, signature or key is written to a log by this class.
 */
public final class AdbLink implements Closeable {
    /** Network debugging's port. The only port a production link uses. */
    public static final int PORT = 5555;
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257,
        STLS = 0x534c5453;
    static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** A_VERSION with checksums optional (adbd still accepts ours: we always compute them); the older one needs them. */
    static final int VERSION = 0x01000001, VERSION_OLD = 0x01000000;
    /** What we accept per message; before adbd's CNXN only the protocol's original 4096 bytes may be sent. */
    static final int MAX_DATA = 256 * 1024, MAX_PAYLOAD_V1 = 4096, ACCEPT_MAX = 1024 * 1024, OUTPUT_MAX = 4 * 1024 * 1024, HEADER = 24;
    /** Hard limits, in milliseconds: connecting, each answer during sign-in, the owner's Allow, one whole command. */
    public static final int CONNECT_MS = 5_000, ANSWER_MS = 15_000, PROMPT_MS = 60_000, COMMAND_MS = 15_000;
    /** An old adbd that never closes a finished stream: its output counts as complete after this long with nothing more. */
    static final int QUIET_MS = 3_000;
    static final int RECONNECT_PAUSE_MS = 300;

    /** The steps of a link, in plain words, for the page and Owner diagnostics. */
    public enum Step {
        CONNECT("Connecting to the TV's own debugging service"),
        HELLO("Waiting for the TV to answer the debugging link"),
        SIGN_IN("Signing in with Unified TV's own key"),
        ALLOW("Waiting for Allow on the TV"),
        RECONNECT("Connecting again after Allow"),
        CONNECTED("Connected"),
        COMMAND("Running a command");
        public final String words;
        Step(String words) { this.words = words; }
    }

    /** Told when the TV is showing its "Allow USB debugging?" prompt, so the page can say what to press. */
    public interface Prompt { void showing(); }
    /** Hears each step of the link; {@code detail} is plain words or "", never a secret. */
    public interface Trace { void step(Step step, String detail); }

    /** The limits one link uses (tests shorten them). */
    static final class Timeouts {
        final int connect, answer, prompt, command, quiet;
        Timeouts(int connect, int answer, int prompt, int command, int quiet) { this.connect = connect; this.answer = answer; this.prompt = prompt; this.command = command; this.quiet = quiet; }
        static final Timeouts DEFAULT = new Timeouts(CONNECT_MS, ANSWER_MS, PROMPT_MS, COMMAND_MS, QUIET_MS);
        Timeouts prompt(int ms) { return new Timeouts(connect, answer, ms, command, quiet); }
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private final Timeouts limits;
    private final Trace trace;
    private String banner = "";
    private int maxData = MAX_PAYLOAD_V1, version;
    private boolean connected;
    private int nextId = 1;
    private volatile boolean closed;

    private AdbLink(Socket socket, Timeouts limits, Trace trace) throws IOException {
        this.socket = socket; this.in = new DataInputStream(socket.getInputStream()); this.out = socket.getOutputStream();
        this.limits = limits; this.trace = trace;
    }

    /** This TV, at the loopback address: built from its bytes, never from a name or a setting. */
    public static InetAddress loopback() {
        try { return InetAddress.getByAddress(new byte[]{127, 0, 0, 1}); } catch (IOException e) { throw new IllegalStateException(e); }
    }

    /** Connects to this TV's own ADB and signs in; the prompt may show on the TV the first time. */
    public static AdbLink open(AdbKey key, Prompt prompt) throws IOException { return connect(PORT, key, prompt, Timeouts.DEFAULT, null, null); }

    /**
     * As {@link #open(AdbKey, Prompt)}, handing each socket to {@code opening} before its handshake starts, so the page can
     * close it while the TV's prompt is still waiting (the owner left): the handshake then fails at once and nothing stays
     * open. {@code trace} hears each step.
     */
    public static AdbLink open(AdbKey key, Prompt prompt, java.util.function.Consumer<Closeable> opening, Trace trace) throws IOException {
        return connect(PORT, key, prompt, Timeouts.DEFAULT, opening, trace);
    }
    public static AdbLink open(AdbKey key, Prompt prompt, java.util.function.Consumer<Closeable> opening) throws IOException {
        return connect(PORT, key, prompt, Timeouts.DEFAULT, opening, null);
    }

    /** Tests only choose the port (a fake adbd on the loopback address) and the limits; the address is always loopback. */
    static AdbLink connect(int port, AdbKey key, Prompt prompt, int promptMs) throws IOException { return connect(port, key, prompt, Timeouts.DEFAULT.prompt(promptMs), null, null); }

    static AdbLink connect(int port, AdbKey key, Prompt prompt, Timeouts limits, java.util.function.Consumer<Closeable> opening, Trace trace) throws IOException {
        boolean mayOffer = true;
        for (int attempt = 0; ; attempt++) {
            Step step = attempt == 0 ? Step.CONNECT : Step.RECONNECT;
            note(trace, step, "");
            Socket s = new Socket();
            if (opening != null) opening.accept(s);
            try {
                s.connect(new InetSocketAddress(loopback(), port), limits.connect);
            } catch (SocketTimeoutException e) {
                close(s);
                throw new AdbException(AdbException.Kind.CLOSED, step, "Nothing answered on port " + port + " in time: network debugging is off.", e);
            } catch (IOException e) {
                close(s);
                throw new AdbException(AdbException.Kind.CLOSED, step, "Nothing answers on port " + port + ": network debugging is off.", e);
            }
            AdbLink link = null;
            try {
                if (!s.getInetAddress().isLoopbackAddress()) throw new AdbException(AdbException.Kind.NOT_THIS_TV, step, "Not this TV.");
                s.setTcpNoDelay(true);
                link = new AdbLink(s, limits, trace);
                link.handshake(key, prompt, mayOffer);
                return link;
            } catch (Reconnect r) {
                // The TV dropped the link after its prompt: some adbd builds do once the owner allows the key. Once more,
                // with the signature only: if the key still isn't allowed, that is a refusal, never a second prompt.
                if (link != null) link.close(); else close(s);
                if (attempt > 0) throw new AdbException(AdbException.Kind.NOT_ALLOWED, Step.ALLOW, "The TV didn't allow it.", r.getCause());
                mayOffer = false;
                try { Thread.sleep(RECONNECT_PAUSE_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AdbException(AdbException.Kind.LOST, Step.RECONNECT, "Stopped.", e); }
            } catch (IOException | RuntimeException e) {
                if (link != null) link.close(); else close(s);
                throw e;
            }
        }
    }

    /** The TV dropped the connection after the public key was offered (see {@link #connect}). */
    private static final class Reconnect extends IOException { Reconnect(Throwable cause) { super("dropped after the prompt", cause); } }

    private void handshake(AdbKey key, Prompt prompt, boolean mayOffer) throws IOException {
        Step step = Step.HELLO;
        note(trace, step, "");
        send(CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
        long until = now() + limits.answer;
        boolean signed = false, offered = false, signedAgain = false;
        while (true) {
            Message m;
            try { m = read(until - now()); }
            catch (SocketTimeoutException e) {
                if (step == Step.ALLOW) throw new AdbException(AdbException.Kind.TIMED_OUT, step, "The TV's prompt wasn't answered in time.", e);
                throw new AdbException(AdbException.Kind.TIMED_OUT, step, "The TV didn't answer.", e);
            } catch (AdbException e) {
                throw e;
            } catch (IOException e) {   // EOF or reset
                // Closed on this side (the owner pressed Back, or left the page): stop, never connect again.
                if (socket.isClosed()) throw new AdbException(AdbException.Kind.LOST, step, "Stopped.", e);
                if (offered) throw new Reconnect(e);
                throw new AdbException(AdbException.Kind.LOST, step, "The TV closed the connection.", e);
            }
            if (m.command == CNXN) {
                banner = new String(m.data, StandardCharsets.UTF_8).replace("\0", "");
                version = m.arg0;
                maxData = m.arg1 > 0 ? Math.min(MAX_DATA, m.arg1) : MAX_PAYLOAD_V1;
                connected = true;
                note(trace, Step.CONNECTED, "messages up to " + maxData + " bytes" + (oldAdbd() ? ", older debugging service" : ""));
                return;
            }
            if (m.command == STLS) throw new AdbException(AdbException.Kind.PROTOCOL, step, "This TV only offers wireless-debugging pairing.");
            if (m.command != AUTH || m.arg0 != AUTH_TOKEN) throw new AdbException(AdbException.Kind.PROTOCOL, step, "Unexpected answer from the TV.");
            if (m.data.length != AdbKey.TOKEN_BYTES) throw new AdbException(AdbException.Kind.PROTOCOL, step, "Unexpected sign-in request from the TV.");
            if (!signed) {
                signed = true; step = Step.SIGN_IN; note(trace, step, "");
                sign(key, m.data, step);
                until = now() + limits.answer;
            } else if (!offered) {
                if (!mayOffer) throw new AdbException(AdbException.Kind.NOT_ALLOWED, step, "The TV still doesn't allow this key.");
                // The TV doesn't know this key yet: send it, and the TV asks the owner. Nothing more is sent until it answers.
                offered = true; step = Step.ALLOW;
                send(AUTH, AUTH_RSAPUBLICKEY, 0, key.publicKeyPayload());
                note(trace, step, "");
                until = now() + limits.prompt;
                if (prompt != null) prompt.showing();
            } else if (!signedAgain) {
                // Some adbd builds answer the owner's Allow with a fresh token: sign it (once), still within the Allow time.
                signedAgain = true;
                note(trace, step, "the TV asked for a new signature");
                sign(key, m.data, step);
            } else {
                throw new AdbException(AdbException.Kind.NOT_ALLOWED, step, "The TV didn't allow it.");
            }
        }
    }

    private void sign(AdbKey key, byte[] token, Step step) throws IOException {
        byte[] signature;
        try { signature = key.signToken(token); } catch (GeneralSecurityException e) { throw new AdbException(AdbException.Kind.PROTOCOL, step, "Couldn't sign in.", e); }
        send(AUTH, AUTH_SIGNATURE, 0, signature);
    }

    /** What adbd said about itself in its CNXN ("device::ro.product.name=…;ro.product.model=…;…"). */
    public String banner() { return banner; }
    /** The largest payload this link sends: the TV's own maxdata from its CNXN, at most ours. */
    public int maxData() { return maxData; }
    /** The protocol version the TV announced. */
    public int version() { return version; }
    /** An adbd older than Android 9's protocol (0x01000000): it may leave a finished shell stream open. */
    boolean oldAdbd() { return connected && version != 0 && Integer.compareUnsigned(version, VERSION) < 0; }

    /** One property from the banner, or "" when it isn't there. Accepts "device::props", "device:serial:props" and no trailing ';'. */
    public String bannerProperty(String name) { return property(banner, name); }
    static String property(String banner, String name) {
        int first = banner.indexOf(':'), second = first < 0 ? -1 : banner.indexOf(':', first + 1);
        String props = second < 0 ? "" : banner.substring(second + 1);
        for (String part : props.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).trim().equals(name)) return part.substring(eq + 1).trim();
        }
        return "";
    }

    /**
     * Leniently, whether the banner describes this device: true when it names none of ro.product.device, .name and .model,
     * or when any of them equals (ignoring case) any of {@code values} (Build.DEVICE, Build.PRODUCT, Build.MODEL). The
     * fingerprint read with getprop is the decisive check; this only catches a banner that names another device outright.
     */
    public boolean bannerDescribes(String... values) { return describes(banner, values); }
    static boolean describes(String banner, String... values) {
        boolean named = false;
        for (String prop : new String[]{"ro.product.device", "ro.product.name", "ro.product.model"}) {
            String v = property(banner, prop).toLowerCase(Locale.ROOT);
            if (v.isEmpty()) continue;
            named = true;
            for (String mine : values) if (mine != null && !mine.trim().isEmpty() && mine.trim().toLowerCase(Locale.ROOT).equals(v)) return true;
        }
        return !named;
    }

    /** Runs one allowed command and returns what it printed. Anything not on {@link AdbCommands} is refused unsent. */
    public synchronized String shell(String command) throws IOException {
        if (!AdbCommands.allowed(command)) throw new AdbException(AdbException.Kind.REFUSED, Step.COMMAND, "Not an allowed command.");
        if (closed) throw new AdbException(AdbException.Kind.LOST, Step.COMMAND, "The link is closed.");
        long until = now() + limits.command;
        int local = nextId++, remote = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean quiet = false;
        try {
            send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            while (true) {
                long left = until - now();
                if (left <= 0) throw new SocketTimeoutException("command");
                Message m;
                if (quiet) {
                    m = readQuietly(Math.min(limits.quiet, left), left);
                    if (m == null) {   // an old adbd left the finished stream open: close it ourselves
                        send(CLSE, local, remote, new byte[0]);
                        return new String(output.toByteArray(), StandardCharsets.UTF_8);
                    }
                } else m = read(left);
                if (m.arg1 != local) {
                    // An earlier stream's leftovers. A WRTE waits for an answer: close that stream rather than leave it hanging.
                    if (m.command == WRTE && m.arg1 > 0 && m.arg1 < local) send(CLSE, m.arg1, m.arg0, new byte[0]);
                    continue;
                }
                if (m.command == OKAY) { if (remote == 0) remote = m.arg0; continue; }
                if (m.command == WRTE) {
                    if (remote == 0) remote = m.arg0;
                    if (output.size() + m.data.length <= OUTPUT_MAX) output.write(m.data, 0, m.data.length);
                    send(OKAY, local, remote, new byte[0]);
                    quiet = oldAdbd() && m.data.length > 0 && m.data[m.data.length - 1] == '\n';
                    continue;
                }
                if (m.command == CLSE) {
                    if (remote == 0) throw new AdbException(AdbException.Kind.PROTOCOL, Step.COMMAND, "The TV refused to run it.");
                    send(CLSE, local, remote, new byte[0]);
                    return new String(output.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        } catch (SocketTimeoutException e) {
            close();
            throw new AdbException(AdbException.Kind.TIMED_OUT, Step.COMMAND, "The TV took too long to answer a command.", e);
        } catch (AdbException e) {
            throw e;
        } catch (IOException e) {
            close();
            throw new AdbException(AdbException.Kind.LOST, Step.COMMAND, "The TV stopped answering.", e);
        }
    }

    public boolean isOpen() { return !closed && !socket.isClosed(); }

    @Override public void close() { closed = true; close(socket); }

    private static void close(Socket s) { try { s.close(); } catch (IOException ignored) { /* nothing to undo */ } }
    private static void note(Trace trace, Step step, String detail) { if (trace != null) try { trace.step(step, detail); } catch (RuntimeException ignored) { /* a trace never stops the link */ } }
    private static long now() { return System.nanoTime() / 1_000_000L; }

    // --- framing: six little-endian 32-bit words, then the payload
    static final class Message {
        final int command, arg0, arg1; final byte[] data;
        Message(int command, int arg0, int arg1, byte[] data) { this.command = command; this.arg0 = arg0; this.arg1 = arg1; this.data = data; }
    }

    static byte[] header(int command, int arg0, int arg1, byte[] data) {
        return ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(checksum(data)).putInt(~command).array();
    }

    /** The legacy payload checksum: the sum of the payload's bytes, each unsigned. */
    static int checksum(byte[] data) { int sum = 0; for (byte b : data) sum += b & 0xff; return sum; }

    private synchronized void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int limit = connected ? maxData : MAX_PAYLOAD_V1;
        if (data.length > limit) throw new AdbException(AdbException.Kind.PROTOCOL, connected ? Step.COMMAND : Step.SIGN_IN, "Message too long for this TV.");
        out.write(header(command, arg0, arg1, data));
        out.write(data);
        out.flush();
    }

    private void limit(long ms) throws IOException { socket.setSoTimeout((int) Math.max(1, Math.min(Integer.MAX_VALUE, ms))); }

    private Message read(long timeoutMs) throws IOException {
        if (timeoutMs <= 0) throw new SocketTimeoutException("deadline");
        limit(timeoutMs);
        byte[] head = new byte[HEADER];
        in.readFully(head);
        return rest(head);
    }

    /** Waits up to {@code quietMs} for the next message to start; null when none does (the stream is over). */
    private Message readQuietly(long quietMs, long leftMs) throws IOException {
        limit(quietMs);
        int first;
        try { first = in.read(); } catch (SocketTimeoutException e) { return null; }
        if (first < 0) throw new EOFException();
        limit(leftMs);
        byte[] head = new byte[HEADER];
        head[0] = (byte) first;
        in.readFully(head, 1, HEADER - 1);
        return rest(head);
    }

    private Message rest(byte[] head) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int command = b.getInt(), arg0 = b.getInt(), arg1 = b.getInt(), length = b.getInt();
        b.getInt();   // checksum: newer adbd sends 0, and TCP already checks the bytes
        int magic = b.getInt();
        if (magic != ~command) throw new AdbException(AdbException.Kind.PROTOCOL, "A damaged message from the TV.");
        if (length < 0 || length > ACCEPT_MAX) throw new AdbException(AdbException.Kind.PROTOCOL, "A message from the TV was too long.");
        byte[] data = new byte[length];
        in.readFully(data);
        return new Message(command, arg0, arg1, data);
    }
}
