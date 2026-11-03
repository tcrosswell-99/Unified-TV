package io.unifiedtv.adb;

import java.io.IOException;

/**
 * Why the link to the TV's own ADB failed, and at which step, so the page can say it in plain words. Never carries key
 * material, a token or command output.
 */
public final class AdbException extends IOException {
    public enum Kind {
        /** Nothing answers on the port, or the TV refused the connection: network debugging is off. */
        CLOSED,
        /** The TV's "Allow debugging?" prompt was refused, or the TV still doesn't know the key. */
        NOT_ALLOWED,
        /** The TV answered in a way this client doesn't speak (wireless-debugging pairing only, a damaged message). */
        PROTOCOL,
        /** What answered is not this TV. */
        NOT_THIS_TV,
        /** The command is not on the allowlist (AdbCommands); it was never sent. */
        REFUSED,
        /** The link was closed, or the TV stopped answering. */
        LOST,
        /** A step took longer than it may (see {@link #step}): the link was closed. */
        TIMED_OUT
    }
    public final Kind kind;
    /** The step the link was at when it failed; null when not known (a simulated TV, or a refusal before sending). */
    public final AdbLink.Step step;

    public AdbException(Kind kind, String message) { this(kind, null, message, null); }
    public AdbException(Kind kind, String message, Throwable cause) { this(kind, null, message, cause); }
    public AdbException(Kind kind, AdbLink.Step step, String message) { this(kind, step, message, null); }
    public AdbException(Kind kind, AdbLink.Step step, String message, Throwable cause) { super(message, cause); this.kind = kind; this.step = step; }
}
