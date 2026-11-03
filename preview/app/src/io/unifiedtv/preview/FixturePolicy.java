package io.unifiedtv.preview;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Spec 7: the matchday card, for whichever team the household chose (Settings > Smart Home > Your team; none by default).
 * Shown only on the matchday on the household's clock. Before kick-off it gives the kick-off time ("Kick-off moved to
 * 19:45" when the source moved it that day); during the match only "Live"; never a score or result, even when the
 * source's text or data contains one. It disappears 15 minutes after the end, and never survives past the matchday. A
 * fixture moved to another day says "Moved to Sunday 3 November" on its old matchday instead. Postponed or
 * time-to-be-confirmed fixtures give no card. Worked out from the fixture each minute, so "Live" and hiding never wait
 * for the next refresh.
 */
final class FixturePolicy {
    private FixturePolicy() {}
    static final long GRACE_SECONDS = 900;

    /** What the card says: the match ("Home v Away") and either a kick-off line or "Live"; the team for its badge, if set. */
    static final class Card {
        final String match, when; final boolean live; final Fixtures.Team team;
        Card(String match, String when, boolean live, Fixtures.Team team) { this.match = match; this.when = when; this.live = live; this.team = team; }
    }

    /**
     * The card for one fixture now, or null. zone: the household's clock; clock: its 12/24-hour time format; move: a
     * recorded move of this fixture (Fixtures.moveOf), or null.
     */
    static Card card(Fixtures.Fixture f, Instant now, ZoneId zone, DateTimeFormatter clock, Fixtures.Team team, Fixtures.Move move) {
        if (f == null || f.unsettled()) return null;
        Fixtures.Sport sport = team == null ? Fixtures.Sport.FOOTBALL : team.sport;
        LocalDate today = now.atZone(zone).toLocalDate();
        String match = match(f, team);
        if (move != null && move.otherDay(zone) && move.from.atZone(zone).toLocalDate().equals(today) && !f.kickoff.atZone(zone).toLocalDate().equals(today))
            return new Card(match, "Moved to " + f.kickoff.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())), false, team);
        if (!f.kickoff.atZone(zone).toLocalDate().equals(today) || now.isAfter(f.end(sport).plusSeconds(GRACE_SECONDS))) return null;
        boolean live = !now.isBefore(f.kickoff);
        String time = f.kickoff.atZone(zone).format(clock);
        String when = live ? "Live" : move != null && !move.otherDay(zone) ? sport.startWord + " moved to " + time : sport.startWord + " " + time;
        return new Card(match, when, live, team);
    }
    /** The first card any of these fixtures gives now (earliest kick-off first), or null. */
    static Card first(List<Fixtures.Fixture> all, Instant now, ZoneId zone, DateTimeFormatter clock, Fixtures.Team team, org.json.JSONObject track) {
        if (all == null) return null;
        List<Fixtures.Fixture> sorted = new ArrayList<>();
        for (Fixtures.Fixture f : all) if (f != null && f.kickoff != null) sorted.add(f);
        sorted.sort(Comparator.comparing(f -> f.kickoff));
        for (Fixtures.Fixture f : sorted) { Card c = card(f, now, zone, clock, team, Fixtures.moveOf(track, f)); if (c != null) return c; }
        return null;
    }

    /** The match line: the source's team names, or a calendar's text without any result. Never a score. */
    static String match(Fixtures.Fixture f, Fixtures.Team team) {
        if (!f.home.isEmpty() && !f.away.isEmpty()) {
            String home = f.home, away = f.away;
            // The household's own name for its team ("Rovers" for "Example Rovers FC") when it chose one.
            if (team != null && f.teamIds.size() == 2) { if (team.id.equals(f.teamIds.get(0))) home = team.displayName; else if (team.id.equals(f.teamIds.get(1))) away = team.displayName; }
            return trim(home + " v " + away);
        }
        return match(f.summary, team);
    }

    /**
     * A calendar's text as the match line, without any result: scores ("2-1", "2 - 1", "(3-2 pens)"), "FT", "HT", "AET"
     * and the like are removed; "vs" becomes "v"; the team's other names become its display name. Text that doesn't name
     * the team gets "Team v " in front. With no team set, the calendar's own words stand; nothing usable gives the team's
     * name, or "Matchday".
     */
    static String match(String summary, Fixtures.Team team) {
        String s = clean(summary);
        if (team != null) {
            List<String> names = new ArrayList<>(team.aliases);
            names.sort((a, b) -> b.length() - a.length());   // "Example Rovers FC" before "Example Rovers"
            for (String n : names) if (!n.trim().isEmpty()) s = s.replaceAll("(?i)(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(n.trim()) + "(?![\\p{L}\\p{N}])", java.util.regex.Matcher.quoteReplacement(team.displayName));
            s = s.replaceAll("\\s+", " ").trim();
        }
        if (s.isEmpty()) return team == null ? "Matchday" : team.displayName;
        if (team != null && !team.named(s)) return trim(team.displayName + " v " + s);
        return trim(s);
    }
    /** The calendar text with every result removed. */
    static String clean(String summary) {
        if (summary == null) return "";
        return summary
                .replaceAll("\\(([^)]*\\d[^)]*)\\)", " ")                       // (3-2 pens), (agg 4-1)
                .replaceAll("\\b\\d+\\s*[-–:]\\s*\\d+\\b", " v ")                  // a score sits between the two teams
                .replaceAll("(?i)\\b(FT|HT|AET|full[ -]?time|half[ -]?time|result|pens?)\\b", " ")
                .replaceAll("\\(\\s*\\)", " ")
                .replaceAll("(?i)\\s+(vs\\.?|versus)\\s+", " v ")
                .replaceAll("(?<![A-Za-z])\\d+(?![A-Za-z])", " ")                // any remaining lone numbers
                .replaceAll("[\\s·|]+", " ").trim()
                .replaceAll("(\\s+v)+\\s+", " v ")
                .replaceAll("^((v|-|–|:|,)\\s+)+", "").replaceAll("(\\s+(v|-|–|:|,))+$", "").replaceAll("^(v|-|–|:|,)$", "").trim();
    }
    private static String trim(String s) { return s.length() > 48 ? s.substring(0, 48).trim() : s; }
}
