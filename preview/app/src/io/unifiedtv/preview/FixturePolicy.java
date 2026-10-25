package io.unifiedtv.preview;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Spec 7: the Tottenham matchday card. Shown only on the UK matchday; before kick-off it gives the UK kick-off time,
 * during the match only "Live", and never a score or result, even if the calendar's text contains one. It disappears 15
 * minutes after the scheduled end, and never survives past the UK matchday. Worked out from the fixture each minute, so
 * "Live" and hiding never wait for the next calendar refresh.
 */
final class FixturePolicy {
    private FixturePolicy() {}
    static final ZoneId UK = ZoneId.of("Europe/London");
    static final long GRACE_SECONDS = 900;

    /** One fixture as read from the calendar: its text and times. */
    static final class Fixture {
        final String summary; final ZonedDateTime kick, finish;
        Fixture(String summary, ZonedDateTime kick, ZonedDateTime finish) { this.summary = summary; this.kick = kick; this.finish = finish; }
    }
    /** What the card says: the match ("Tottenham v Arsenal") and either "Kick-off 19:45" or "Live". */
    static final class Card {
        final String match, when; final boolean live;
        Card(String match, String when, boolean live) { this.match = match; this.when = when; this.live = live; }
    }

    /** The fixture, or null when the times can't be read (postponed, missing): no card rather than a wrong one. */
    static Fixture fixture(String summary, String start, String end) {
        try {
            ZonedDateTime kick = parse(start), finish = end == null || end.isEmpty() ? kick.plusHours(2) : parse(end);
            return new Fixture(summary == null ? "" : summary, kick, finish);
        } catch (Exception e) { return null; }
    }

    static Card card(Fixture f, Instant now) {
        if (f == null) return null;
        if (!f.kick.toLocalDate().equals(now.atZone(UK).toLocalDate()) || now.isAfter(f.finish.toInstant().plusSeconds(GRACE_SECONDS))) return null;
        boolean live = !now.isBefore(f.kick.toInstant());
        return new Card(match(f.summary), live ? "Live" : "Kick-off " + f.kick.format(DateTimeFormatter.ofPattern("HH:mm")), live);
    }

    /**
     * The match line without any result: scores ("2-1", "2 - 1", "(3-2 pens)"), "FT", "HT", "AET" and the like are
     * removed; "Tottenham Hotspur" is shortened to "Tottenham"; "vs" becomes "v". Nothing usable gives plain "Tottenham".
     */
    static String match(String summary) {
        if (summary == null) return "Tottenham";
        String s = summary
                .replaceAll("\\(([^)]*\\d[^)]*)\\)", " ")                       // (3-2 pens), (agg 4-1)
                .replaceAll("\\b\\d+\\s*[-–:]\\s*\\d+\\b", " v ")                  // a score sits between the two teams
                .replaceAll("(?i)\\b(FT|HT|AET|full[ -]?time|half[ -]?time|result|pens?)\\b", " ")
                .replaceAll("\\(\\s*\\)", " ")
                .replaceAll("(?i)\\bTottenham Hotspur( FC)?\\b", "Tottenham")
                .replaceAll("(?i)\\bSpurs\\b", "Tottenham")
                .replaceAll("(?i)\\s+(vs\\.?|versus)\\s+", " v ")
                .replaceAll("(?<![A-Za-z])\\d+(?![A-Za-z])", " ")                // any remaining lone numbers
                .replaceAll("[\\s·|]+", " ").trim()
                .replaceAll("(\\s+v)+\\s+", " v ")
                .replaceAll("^((v|-|–|:|,)\\s+)+", "").replaceAll("(\\s+(v|-|–|:|,))+$", "").trim();
        if (s.isEmpty() || !s.toLowerCase(Locale.ROOT).contains("tottenham")) return s.isEmpty() ? "Tottenham" : "Tottenham v " + s;
        return s.length() > 48 ? s.substring(0, 48).trim() : s;
    }

    /** Kept for callers that have only times: "Tottenham · 18:30", "Tottenham · Live" or nothing. */
    static String label(String start, String end, Instant now) {
        Card c = card(fixture("", start, end), now);
        return c == null ? "" : "Tottenham · " + (c.live ? "Live" : c.when.replace("Kick-off ", ""));
    }

    private static ZonedDateTime parse(String s) {
        try { return OffsetDateTime.parse(s.replace(' ', 'T')).atZoneSameInstant(UK); }
        catch (Exception e) { return LocalDateTime.parse(s.replace(' ', 'T')).atZone(UK); }
    }
}
