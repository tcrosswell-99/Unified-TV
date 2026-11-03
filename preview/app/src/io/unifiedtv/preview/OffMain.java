package io.unifiedtv.preview;

/**
 * Disk, keystore and JSON work stays off the main thread (owner's Sony BRAVIA, build run 106, 3 November 2026: Films &amp;
 * TV "locked the TV" while the library was being checked). The places that read or write the saved library list or the
 * encrypted folder records call {@link #check}; tests turn {@link #strict} on, so any of them reached from the main thread
 * fails the test, as StrictMode's disk checks would on a TV. Off by default: it never throws on a TV.
 */
final class OffMain {
    private OffMain(){}
    /** Tests only: throw when disk work is reached from the main thread. */
    static volatile boolean strict;
    static void check(String what){
        if(strict&&android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IllegalStateException(what+" on the main thread");
    }
}
