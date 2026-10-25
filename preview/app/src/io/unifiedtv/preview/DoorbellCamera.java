package io.unifiedtv.preview;

/**
 * The doorbell camera card's household settings (SET-051; owner, 25 October 2026): it closes by itself after 30 seconds
 * (the default) or 60 seconds, or only with Back (manual); and it is a large overlay over what is playing, where this TV
 * allows overlays, or a full-screen card. A press while the card is open extends its time. This is not the separate
 * "a doorbell queued during an alarm is still worth showing" window (AlertPolicy.RING_STILL_RELEVANT), which stays 45 s.
 *
 * SET-054: test events (household setup's "Test: someone rings the doorbell" and "Test: a person at the door") are
 * labelled TEST on the card and the toast, so a test can never be mistaken for a real visitor.
 */
final class DoorbellCamera {
    private DoorbellCamera(){}
    static final String CLOSE="doorbellClose",LAYOUT="doorbellLayout";
    static final String CLOSE_30="30",CLOSE_60="60",MANUAL="manual",OVERLAY="overlay",FULL="full";
    static final String[] CLOSES={CLOSE_30,CLOSE_60,MANUAL},LAYOUTS={OVERLAY,FULL};
    static String close(String saved){return CLOSE_60.equals(saved)||MANUAL.equals(saved)?saved:CLOSE_30;}
    /** Milliseconds until the card closes by itself; 0 for never (Back only). */
    static long closeMs(String saved){switch(close(saved)){case CLOSE_60:return 60_000;case MANUAL:return 0;default:return 30_000;}}
    static String closeLabel(String saved){switch(close(saved)){case CLOSE_60:return "After 60 seconds";case MANUAL:return "Only with Back";default:return "After 30 seconds";}}
    static String layout(String saved){return FULL.equals(saved)?FULL:OVERLAY;}
    static String layoutLabel(String saved){return FULL.equals(layout(saved))?"Full screen":"Large overlay (where supported)";}
    /** The card's title: the camera's name, with TEST in front for a test. */
    static String title(String name,boolean test){String n=name==null||name.trim().isEmpty()?"Doorbell":name.trim();return test?"TEST · "+n:n;}
    /** The person toast's words. */
    static String person(String name,boolean test){return (test?"TEST · ":"")+"Person at "+(name==null||name.trim().isEmpty()?"Doorbell":name.trim());}
    /** The hint at the foot of the card. */
    static String hint(String saved){return MANUAL.equals(close(saved))?"Back to close":"Back to close · closes by itself";}
}
