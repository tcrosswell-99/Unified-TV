package io.unifiedtv.preview;
import java.time.*;
import java.time.format.DateTimeFormatter;
final class FixturePolicy {
    static String label(String start,String end,Instant now){
        ZoneId uk=ZoneId.of("Europe/London");
        try{
            ZonedDateTime kick=parse(start,uk),finish=parse(end,uk);
            if(!kick.toLocalDate().equals(now.atZone(uk).toLocalDate())||now.isAfter(finish.toInstant().plusSeconds(900)))return "";
            return now.isBefore(kick.toInstant())?"Tottenham · "+kick.format(DateTimeFormatter.ofPattern("HH:mm")):"Tottenham · Live";
        }catch(Exception e){return "";}
    }
    private static ZonedDateTime parse(String s,ZoneId uk){try{return OffsetDateTime.parse(s.replace(' ','T')).atZoneSameInstant(uk);}catch(Exception e){return LocalDateTime.parse(s.replace(' ','T')).atZone(uk);}}
}
