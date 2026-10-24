package io.unifiedtv.preview;
import java.util.Locale;
/** Unrecognised speech never acquires authority to execute household services. */
final class VoiceCommand {
    static String classify(String request){
        String q=request.trim().toLowerCase(Locale.UK);
        if(q.contains("live tv")||q.contains("reamsmart")||q.contains("smarters")||q.contains("iptv"))return "blocked";
        if(q.equals("switch profile"))return "profile";
        if(q.equals("show camera")||q.equals("show ring camera"))return "camera";
        if(q.startsWith("find "))return "find";
        if(q.equals("open bbc"))return "bbc";
        if(q.equals("open netflix"))return "netflix";
        if(q.equals("open disney")||q.equals("open disney plus"))return "disney";
        return "discovery";
    }
}
