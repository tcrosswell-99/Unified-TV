package io.unifiedtv.preview;

/**
 * Whether this TV is showing smoke and carbon monoxide alarms (SET-053, with the owner's exception of 25 October 2026):
 * the household may switch alarms off on a TV, but only an adult (the PIN), and while they are off Home and Settings say
 * so permanently. Stopping household alerts counts as off too. With no Home Assistant, or no detector ever seen, the TV
 * claims nothing: "Not configured — no live monitoring". The TV is never a certified alarm.
 *
 * Mirrored in preferences (non-secret) so Home can read it without opening the encrypted store: SHOWN follows the
 * household setting "Show smoke and carbon monoxide alarms on this TV", STOPPED is set when an adult stops household
 * alerts and cleared when they start again, DETECTORS is how many smoke/CO detectors the last full read found (-1 never).
 */
final class AlarmWatch {
    private AlarmWatch(){}
    static final String SHOWN="alarmsShown",STOPPED="householdStopped",DETECTORS="alarmDetectors",CONFIGURED="householdConfigured";
    enum State {ON,OFF,NOT_CONFIGURED}
    static final String OFF_NOTICE="Alarms off on this TV",NOT_CONFIGURED_TEXT="Not configured — no live monitoring";
    /** shown: the household switch; stopped: alerts stopped by an adult; configured: Home Assistant set up; detectors: last count, -1 unknown. */
    static State state(boolean shown,boolean stopped,boolean configured,int detectors){
        if(!shown||stopped)return State.OFF;
        if(!configured||detectors<=0)return State.NOT_CONFIGURED;
        return State.ON;
    }
    /** Home's permanent notice: only when an adult switched alarms off (not-configured is the normal state of most homes). */
    static boolean homeNotice(boolean shown,boolean stopped){return state(shown,stopped,true,1)==State.OFF;}
    /** Settings' words for each state. */
    static String words(State s){
        switch(s){
            case OFF:return OFF_NOTICE+". An adult switched them off or stopped household alerts. Smoke and carbon monoxide alarms will not appear here until an adult switches them back on.";
            case NOT_CONFIGURED:return NOT_CONFIGURED_TEXT+". No smoke or carbon monoxide detector is connected to this TV through Home Assistant.";
            default:return "Smoke and carbon monoxide alarms from Home Assistant show on this TV above everything else, whoever is watching. This TV adds to your alarms; it is not a certified alarm.";
        }
    }
    static State of(android.content.SharedPreferences p){return state(p.getBoolean(SHOWN,true),p.getBoolean(STOPPED,false),p.getBoolean(CONFIGURED,false),p.getInt(DETECTORS,-1));}
}
