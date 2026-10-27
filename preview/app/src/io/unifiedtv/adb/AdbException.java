package io.unifiedtv.adb;

import java.io.IOException;

/** Why the link to the TV's own ADB failed, so the page can say it in plain words. Never carries key material. */
public final class AdbException extends IOException {
    public enum Kind {
        /** Nothing answers on the port: network debugging is off. */
        CLOSED,
        /** The TV's "Allow debugging?" prompt was refused, or not answered in time. */
        NOT_ALLOWED,
        /** The TV answered in a way this client doesn't speak (wireless-debugging pairing only, a damaged message). */
        PROTOCOL,
        /** What answered is not this TV. */
        NOT_THIS_TV,
        /** The command is not on the allowlist (AdbCommands); it was never sent. */
        REFUSED,
        /** The link was closed, or the TV stopped answering. */
        LOST
    }
    public final Kind kind;
    public AdbException(Kind kind, String message) { super(message); this.kind = kind; }
    public AdbException(Kind kind, String message, Throwable cause) { super(message, cause); this.kind = kind; }
}
