/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.emails;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renders HTML email templates with multi-language support and variable substitution.
 *
 * <p>Moved to {@code restheart-commons} alongside {@link EmailTemplateLoader} when
 * {@code restheart-stripe} needed the same {@code {{variable}}} + i18n rendering for its
 * own billing-notification templates — a second module needing this algorithm is the point
 * at which it stops being an accounts detail.
 *
 * <h2>Template format</h2>
 * Templates are standard HTML files. Two features are layered on top:
 *
 * <h3>i18n — {@code <span lang="xx">} tags</h3>
 * <p>Any element wrapped in {@code <span lang="xx">...</span>} is language-aware.
 * During rendering, spans matching the requested language have their content kept
 * (the span tag itself is removed), while all other language spans are removed entirely.
 *
 * <pre>{@code
 * <span lang="en">Hello {{firstName}}</span>
 * <span lang="it">Ciao {{firstName}}</span>
 * }</pre>
 *
 * <p>If the requested language does not appear in the template at all, the renderer
 * falls back to {@code "en"}. If {@code "en"} is also absent, all lang spans are kept
 * verbatim (fail-safe: better show all languages than none).
 *
 * <h3>Variable substitution — {@code {{variable}}} placeholders</h3>
 * <p>After i18n filtering, every occurrence of {@code {{key}}} is replaced with the
 * corresponding value from the variables map. Unknown placeholders are left as-is.
 *
 * <h3>Subject extraction</h3>
 * <p>The email subject is taken from the {@code <title>} element (which may itself
 * contain lang spans). All other {@code <head>} content is stripped from the body.
 *
 * <h2>Example template</h2>
 * <pre>{@code
 * <!DOCTYPE html>
 * <html>
 * <head>
 *   <meta charset="UTF-8">
 *   <title>
 *     <span lang="en">Verify your email — {{appName}}</span>
 *     <span lang="it">Verifica la tua email — {{appName}}</span>
 *   </title>
 * </head>
 * <body>
 *   <p>
 *     <span lang="en">Hi {{firstName}}, please verify your address.</span>
 *     <span lang="it">Ciao {{firstName}}, verifica il tuo indirizzo.</span>
 *   </p>
 *   <p><a href="{{verificationLink}}">
 *     <span lang="en">Verify email</span>
 *     <span lang="it">Verifica email</span>
 *   </a></p>
 * </body>
 * </html>
 * }</pre>
 */
public final class EmailRenderer {

    // Matches <span lang="xx" ...>...</span> (non-greedy, DOTALL for multiline content)
    private static final Pattern LANG_SPAN = Pattern.compile(
            "<span\\s+lang=\"([a-zA-Z]{2,5})\"[^>]*>([\\s\\S]*?)</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Matches the full <title>...</title> block (used to extract the subject)
    private static final Pattern TITLE_BLOCK = Pattern.compile(
            "<title[^>]*>([\\s\\S]*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Matches the <body>...</body> block (used to extract the HTML body)
    private static final Pattern BODY_BLOCK = Pattern.compile(
            "<body[^>]*>([\\s\\S]*?)</body>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private EmailRenderer() {
    }

    /**
     * Result of rendering a template.
     *
     * @param subject  the email subject line (from the {@code <title>} element)
     * @param htmlBody the HTML body (from the {@code <body>} element, or the full
     *                 template if no {@code <body>} tag is present)
     */
    public record RenderedEmail(String subject, String htmlBody) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders a template string for the given locale and variable set.
     *
     * @param templateContent raw HTML template (e.g. loaded from a file or resource)
     * @param vars            map of placeholder names → values; {@code null} is treated
     *                        as an empty map
     * @param lang            ISO 639-1 language code (e.g. {@code "en"}, {@code "it"});
     *                        falls back to {@code "en"} if the requested language is not
     *                        found in the template
     * @return the rendered subject and HTML body
     */
    public static RenderedEmail render(String templateContent,
                                       Map<String, String> vars,
                                       String lang) {
        if (templateContent == null || templateContent.isBlank()) {
            return new RenderedEmail("(no subject)", "(empty template)");
        }

        var effectiveLang = chooseLang(templateContent, lang);

        // Extract and render subject from <title>
        var subject = extractTitle(templateContent, effectiveLang, vars);

        // Extract and render body from <body> (fall back to full template)
        var body = extractBody(templateContent, effectiveLang, vars);

        return new RenderedEmail(subject, body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core processing steps
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Filters {@code <span lang="xx">} tags from {@code html}, keeping the content of
     * spans whose lang matches {@code lang} and discarding all others.
     * Spans that have no {@code lang} attribute are passed through unchanged.
     */
    static String filterLang(String html, String lang) {
        return LANG_SPAN.matcher(html).replaceAll(mr -> {
            var spanLang = mr.group(1).toLowerCase();
            var content = mr.group(2);
            // Escape $ and \ in content to prevent replaceAll misinterpretation
            return spanLang.equalsIgnoreCase(lang)
                    ? content.replace("\\", "\\\\").replace("$", "\\$")
                    : "";
        });
    }

    /**
     * Substitutes {@code {{key}}} placeholders with values from {@code vars}, HTML-escaped.
     * Unknown placeholders are left as-is.
     *
     * <p>The values are data — a name typed into a sign-up form, a team name, a URL built by the
     * caller — and must never become markup: unescaped, a "first name" carrying a link or a whole
     * paragraph turns every email that greets the user into a message written by whoever filled
     * in the form. Escaping keeps URLs working: a browser reads {@code &amp;} in an href as
     * {@code &}.
     *
     * <p>One pass over the template, so a value that itself contains {@code {{other}}} is not
     * expanded again.
     */
    static String substituteVars(String html, Map<String, String> vars) {
        return substitute(html, vars, true);
    }

    /**
     * Same as {@link #substituteVars}, for the subject: a plain-text header, so values are not
     * HTML-escaped, but line breaks and other control characters are removed, as they could
     * otherwise end the header and start new ones.
     */
    static String substituteSubjectVars(String subject, Map<String, String> vars) {
        return substitute(subject, vars, false);
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");

    private static String substitute(String text, Map<String, String> vars, boolean html) {
        if (vars == null || vars.isEmpty()) return text;
        return PLACEHOLDER.matcher(text).replaceAll(mr -> {
            var key = mr.group(1);
            if (!vars.containsKey(key)) {
                return java.util.regex.Matcher.quoteReplacement(mr.group());
            }
            var value = vars.get(key) != null ? vars.get(key) : "";
            var safe = html ? escapeHtml(value) : value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ");
            return java.util.regex.Matcher.quoteReplacement(safe);
        });
    }

    static String escapeHtml(String s) {
        var out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Determines the effective language to use. Falls back to {@code "en"} if the
     * requested language has no {@code <span lang="...">} in the template.
     * If {@code "en"} is also absent (template has no lang spans at all), returns the
     * originally requested language unchanged (all content is treated as language-neutral).
     */
    private static String chooseLang(String template, String requestedLang) {
        if (requestedLang == null || requestedLang.isBlank()) return "en";
        var lc = requestedLang.toLowerCase();
        if (hasLang(template, lc)) return lc;
        if (hasLang(template, "en")) return "en";
        return lc; // template has no lang spans — lang-neutral content
    }

    /** Returns true if the template contains at least one {@code <span lang="xx">}. */
    private static boolean hasLang(String template, String lang) {
        return Pattern.compile(
                "<span\\s+lang=\"" + Pattern.quote(lang) + "\"",
                Pattern.CASE_INSENSITIVE).matcher(template).find();
    }

    private static String extractTitle(String template, String lang, Map<String, String> vars) {
        var m = TITLE_BLOCK.matcher(template);
        if (!m.find()) return "(no subject)";
        var raw = m.group(1).strip();
        return substituteSubjectVars(filterLang(raw, lang), vars).strip();
    }

    private static String extractBody(String template, String lang, Map<String, String> vars) {
        var m = BODY_BLOCK.matcher(template);
        var raw = m.find() ? m.group(1) : template; // fall back to full template
        return substituteVars(filterLang(raw, lang), vars);
    }
}
