package io.unifiedtv.preview;

import java.util.Locale;

/**
 * The phone's pages: plain British English, large touch targets, inline CSS only, no script, no images, no fonts and
 * nothing fetched from anywhere else. Every value from outside is escaped. A secret is never written into a page.
 */
final class PhonePages {
    private PhonePages() {}

    /** Text and double-quoted attribute values (every attribute here is double-quoted, so ' needs no escape). */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) switch (c) {
            case '&': b.append("&amp;"); break; case '<': b.append("&lt;"); break; case '>': b.append("&gt;"); break;
            case '"': b.append("&quot;"); break; default: b.append(c);
        }
        return b.toString();
    }

    private static final String STYLE = "*{box-sizing:border-box}body{margin:0;font:17px/1.45 -apple-system,system-ui,'Segoe UI',Roboto,sans-serif;background:#0f1724;color:#eef2f8}"
        + "main{max-width:34rem;margin:0 auto;padding:1.4rem 1.1rem 3rem}h1{font-size:1.5rem;margin:.2rem 0 1rem}h2{font-size:1.1rem;margin:1.6rem 0 .5rem}"
        + "p{margin:.6rem 0}.muted{color:#a9b6c9;font-size:.93rem}.ok{background:#173a2a;border-radius:12px;padding:.9rem 1rem}.err{background:#4a1f24;border-radius:12px;padding:.9rem 1rem}"
        + "ol{padding-left:1.3rem}li{margin:.5rem 0}label{display:block;margin:.9rem 0 .3rem;font-weight:600}"
        + "input,textarea{width:100%;font:inherit;padding:.75rem;border-radius:10px;border:1px solid #4a5a73;background:#18233a;color:#fff}textarea{min-height:5.5rem;font-family:ui-monospace,monospace;font-size:.9rem}"
        + "button,.button{display:block;width:100%;text-align:center;font:inherit;font-weight:600;padding:.9rem;margin:1rem 0 .4rem;border:0;border-radius:12px;background:#dbe7ff;color:#0f1724;text-decoration:none}"
        + ".quiet{background:transparent;color:#dbe7ff;border:1px solid #4a5a73}details{margin:1.2rem 0;border-top:1px solid #2b3850;padding-top:.8rem}summary{cursor:pointer;color:#dbe7ff}"
        + "a{color:#9fc3ff}.code{letter-spacing:.35em;font-size:1.6rem;text-align:center}";

    /** The page frame: title, then body HTML that the caller has already escaped. */
    static String page(String title, String body) {
        return "<!doctype html><html lang=\"en-GB\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<meta name=\"referrer\" content=\"same-origin\"><title>" + esc(title) + " · Unified TV</title><style>" + STYLE + "</style></head><body><main><h1>"
            + esc(title) + "</h1>" + body + "</main></body></html>";
    }
    static String message(String title, String text) { return page(title, text == null || text.isEmpty() ? "" : "<p>" + esc(text) + "</p>"); }
    static String error(String text) { return text == null ? "" : "<p class=\"err\">" + esc(text) + "</p>"; }
    /** A finished step: the phone can be put away; the TV shows the same. */
    static String done(String title, String text) { return page(title, "<p class=\"ok\">" + esc(text) + "</p><p class=\"muted\">You can close this page.</p>"); }
    /** An external link that opens in a new tab and sends no referrer. */
    static String link(String url, String label, boolean button) {
        return "<a" + (button ? " class=\"button quiet\"" : "") + " href=\"" + esc(url) + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + esc(label) + "</a>";
    }

    /** The one prominent external link on a page ("Get a free key"): a full-width button, new tab, no referrer. */
    static String primary(String url, String label) {
        return "<a class=\"button\" href=\"" + esc(url) + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + esc(label) + "</a>";
    }

    /** The short typed address's first page: the TV's 6-digit code. */
    static String code(String problem) {
        return page("Enter the code on your TV", error(problem)
            + "<form method=\"post\" action=\"/\"><label for=\"code\">The 6 digits shown on the TV</label>"
            + "<input id=\"code\" class=\"code\" name=\"code\" inputmode=\"numeric\" autocomplete=\"one-time-code\" pattern=\"[0-9 ]{6,8}\" maxlength=\"8\" required autofocus>"
            + "<button type=\"submit\">Continue</button></form>");
    }

    /** A secret field: no autocomplete, autocorrect or capitalisation; never pre-filled. */
    static String secret(String name, String label, String hint) {
        return "<label for=\"" + name + "\">" + esc(label) + "</label><textarea id=\"" + name + "\" name=\"" + name + "\" autocomplete=\"off\" autocorrect=\"off\" autocapitalize=\"none\" spellcheck=\"false\" placeholder=\""
            + esc(hint) + "\"></textarea>";
    }

    /** Steps as an ordered list; each step already escaped HTML. */
    static String steps(String... html) {
        StringBuilder b = new StringBuilder("<ol>");
        for (String s : html) b.append("<li>").append(s).append("</li>");
        return b.append("</ol>").toString();
    }

    static String hidden(String name, String value) { return "<input type=\"hidden\" name=\"" + esc(name) + "\" value=\"" + esc(value) + "\">"; }
    static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
