package io.unifiedtv.adb;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

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
 * <p>Never logged: no command output, token, signature or key is written to a log by this class.
 */
public final class AdbLink implements Closeable {
    /** Network debugging's port. The only port a production link uses. */
    public static final int PORT = 5555;
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257,
        STLS = 0x534c5453;
    static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** A_VERSION with checksums optional (adbd still accepts ours: we always compute them). */
    static final int VERSION = 0x01000001;
    static final int MAX_DATA = 256 * 1024, ACCEPT_MAX = 1024 * 1024, OUTPUT_MAX = 4 * 1024 * 1024, HEADER = 24;
    static final int CONNECT_MS = 3000, ANSWER_MS = 15_000, COMMAND_MS = 60_000;
    /** How long the owner has to answer the TV's prompt. */
    public static final int PROMPT_MS = 90_000;

    /** Told when the TV is showing its "Allow USB debugging?" prompt, so the page can say what to press. */
    public interface Prompt { void showing(); }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private String banner = "";
    private int nextId = 1;
    private volatile boolean closed;

    private AdbLink(Socket socket) throws IOException {
        this.socket = socket; this.in = new DataInputStream(socket.getInputStream()); this.out = socket.getOutputStream();
    }

    /** This TV, at the loopback address: built from its bytes, never from a name or a setting. */
    public static InetAddress loopback() {
        try { return InetAddress.getByAddress(new byte[]{127, 0, 0, 1}); } catch (IOException e) { throw new IllegalStateException(e); }
    }

    /** Connects to this TV's own ADB and signs in; the prompt may show on the TV the first time. */
    public static AdbLink open(AdbKey key, Prompt prompt) throws IOException { return connect(PORT, key, prompt, PROMPT_MS, null); }

    /**
     * As {@link #open(AdbKey, Prompt)}, handing the socket to {@code opening} before the handshake starts, so the page can
     * close it while the TV's prompt is still waiting (the owner left): the handshake then fails at once and nothing stays
     * open.
     */
    public static AdbLink open(AdbKey key, Prompt prompt, java.util.function.Consumer<Closeable> opening) throws IOException {
        return connect(PORT, key, prompt, PROMPT_MS, opening);
    }

    /** Tests only choose the port (a fake adbd on the loopback address); the address is always loopback. */
    static AdbLink connect(int port, AdbKey key, Prompt prompt, int promptMs) throws IOException { return connect(port, key, prompt, promptMs, null); }

    static AdbLink connect(int port, AdbKey key, Prompt prompt, int promptMs, java.util.function.Consumer<Closeable> opening) throws IOException {
        Socket s = new Socket();
        if (opening != null) opening.accept(s);
        try {
            s.connect(new InetSocketAddress(loopback(), port), CONNECT_MS);
        } catch (ConnectException | SocketTimeoutException e) {
            close(s);
            throw new AdbException(AdbException.Kind.CLOSED, "Nothing answers on port " + port + ": network debugging is off.", e);
        } catch (IOException e) {
            close(s);
            throw new AdbException(AdbException.Kind.CLOSED, "Couldn't reach port " + port + ".", e);
        }
        AdbLink link = null;
        try {
            if (!s.getInetAddress().isLoopbackAddress()) throw new AdbException(AdbException.Kind.NOT_THIS_TV, "Not this TV.");
            s.setTcpNoDelay(true); s.setSoTimeout(ANSWER_MS);
            link = new AdbLink(s);
            link.handshake(key, prompt, promptMs);
            s.setSoTimeout(COMMAND_MS);
            return link;
        } catch (IOException | RuntimeException e) {
            if (link != null) link.close(); else close(s);
            throw e;
        }
    }

    private void handshake(AdbKey key, Prompt prompt, int promptMs) throws IOException {
        send(CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
        boolean signed = false, offered = false;
        while (true) {
            Message m;
            try { m = read(); }
            catch (SocketTimeoutException e) {
                throw offered ? new AdbException(AdbException.Kind.NOT_ALLOWED, "The TV's prompt wasn't answered in time.", e)
                    : new AdbException(AdbException.Kind.PROTOCOL, "The TV didn't answer.", e);
            } catch (EOFException e) {
                throw offered ? new AdbException(AdbException.Kind.NOT_ALLOWED, "The TV didn't allow it.", e)
                    : new AdbException(AdbException.Kind.LOST, "The TV closed the connection.", e);
            }
            if (m.command == CNXN) { banner = new String(m.data, StandardCharsets.UTF_8).replace("\0", ""); return; }
            if (m.command == STLS) throw new AdbException(AdbException.Kind.PROTOCOL, "This TV only offers wireless-debugging pairing.");
            if (m.command != AUTH || m.arg0 != AUTH_TOKEN) throw new AdbException(AdbException.Kind.PROTOCOL, "Unexpected answer from the TV.");
            if (m.data.length != AdbKey.TOKEN_BYTES) throw new AdbException(AdbException.Kind.PROTOCOL, "Unexpected sign-in request from the TV.");
            if (!signed) {
                signed = true;
                byte[] signature;
                try { signature = key.signToken(m.data); } catch (GeneralSecurityException e) { throw new AdbException(AdbException.Kind.PROTOCOL, "Couldn't sign in.", e); }
                send(AUTH, AUTH_SIGNATURE, 0, signature);
            } else if (!offered) {
                // The TV doesn't know this key yet: send it, and the TV asks the owner.
                offered = true;
                send(AUTH, AUTH_RSAPUBLICKEY, 0, key.publicKeyPayload());
                socket.setSoTimeout(promptMs);
                if (prompt != null) prompt.showing();
            } else {
                throw new AdbException(AdbException.Kind.NOT_ALLOWED, "The TV didn't allow it.");
            }
        }
    }

    /** What adbd said about itself in its CNXN ("device::ro.product.name=…;ro.product.model=…;…"). */
    public String banner() { return banner; }

    /** One property from the banner, or "" when it isn't there. */
    public String bannerProperty(String name) {
        int at = banner.indexOf("::");
        String props = at < 0 ? "" : banner.substring(at + 2);
        for (String part : props.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) return part.substring(eq + 1);
        }
        return "";
    }

    /** Runs one allowed command and returns what it printed. Anything not on {@link AdbCommands} is refused unsent. */
    public synchronized String shell(String command) throws IOException {
        if (!AdbCommands.allowed(command)) throw new AdbException(AdbException.Kind.REFUSED, "Not an allowed command.");
        if (closed) throw new AdbException(AdbException.Kind.LOST, "The link is closed.");
        int local = nextId++, remote = 0;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while (true) {
                Message m = read();
                if (m.arg1 != local) continue;   // an earlier stream's leftovers
                if (m.command == OKAY) { if (remote == 0) remote = m.arg0; continue; }
                if (m.command == WRTE) {
                    if (remote == 0) remote = m.arg0;
                    if (output.size() + m.data.length <= OUTPUT_MAX) output.write(m.data, 0, m.data.length);
                    send(OKAY, local, remote, new byte[0]);
                    continue;
                }
                if (m.command == CLSE) {
                    if (remote == 0) throw new AdbException(AdbException.Kind.PROTOCOL, "The TV refused to run it.");
                    send(CLSE, local, remote, new byte[0]);
                    return new String(output.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        } catch (AdbException e) {
            throw e;
        } catch (IOException e) {
            close();
            throw new AdbException(AdbException.Kind.LOST, "The TV stopped answering.", e);
        }
    }

    public boolean isOpen() { return !closed && !socket.isClosed(); }

    @Override public void close() { closed = true; close(socket); }

    private static void close(Socket s) { try { s.close(); } catch (IOException ignored) { /* nothing to undo */ } }

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
        if (data.length > MAX_DATA) throw new AdbException(AdbException.Kind.PROTOCOL, "Message too long.");
        out.write(header(command, arg0, arg1, data));
        out.write(data);
        out.flush();
    }

    private Message read() throws IOException {
        byte[] head = new byte[HEADER];
        in.readFully(head);
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
