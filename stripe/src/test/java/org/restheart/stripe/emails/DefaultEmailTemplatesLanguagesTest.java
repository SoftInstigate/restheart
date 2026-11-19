package org.restheart.stripe.emails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Every text of the default email templates of this module comes in the same languages, and each
 * version carries the placeholders and the inner tags of the English one.
 *
 * <p>A text is a block: the {@code <span lang="xx">} elements that follow one another with only
 * white space between them. A text added later in one language only, a placeholder misspelled
 * or lost in a translation, makes this test fail.
 */
class DefaultEmailTemplatesLanguagesTest {

    /** The languages of the default templates, in the order of the spans of a block. */
    static final List<String> LANGUAGES = List.of("en", "it", "es", "fr", "de", "nl", "pt", "pl");

    // the same element EmailRenderer filters
    private static final Pattern LANG_SPAN = Pattern.compile(
            "<span\\s+lang=\"([a-zA-Z]{2,5})\"[^>]*>([\\s\\S]*?)</span>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^}]*}}");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Test
    void everyTextComesInEveryLanguageWithThePlaceholdersOfTheEnglishOne() throws Exception {
        var dir = getClass().getClassLoader().getResource("email-templates");
        assertNotNull(dir, "email-templates is not on the classpath");

        List<Path> templates;
        try (var files = Files.list(Path.of(dir.toURI()))) {
            templates = files.filter(f -> f.toString().endsWith(".html")).sorted().toList();
        }
        assertFalse(templates.isEmpty(), "no default template found in " + dir);

        for (var template : templates) {
            var name = template.getFileName().toString();
            var blocks = blocks(Files.readString(template, StandardCharsets.UTF_8));
            assertFalse(blocks.isEmpty(), name + " has no <span lang> text");

            for (var block : blocks) {
                var english = block.get("en");
                var where = name + ", text \"" + english + "\"";
                assertEquals(LANGUAGES, List.copyOf(block.keySet()), where + ": languages of the block");

                for (var version : block.entrySet()) {
                    var lang = where + ", " + version.getKey();
                    assertFalse(version.getValue().isBlank(), lang + ": empty");
                    assertEquals(all(PLACEHOLDER, english), all(PLACEHOLDER, version.getValue()), lang + ": placeholders");
                    assertEquals(all(TAG, english), all(TAG, version.getValue()), lang + ": inner tags");
                }
            }
        }
    }

    /** The blocks of a template: language to text, in the order of the spans. */
    private static List<Map<String, String>> blocks(String html) {
        var blocks = new ArrayList<Map<String, String>>();
        var m = LANG_SPAN.matcher(html);
        var previousEnd = -1;
        while (m.find()) {
            var follows = previousEnd >= 0 && html.substring(previousEnd, m.start()).isBlank();
            if (!follows) {
                blocks.add(new LinkedHashMap<>());
            }
            var block = blocks.get(blocks.size() - 1);
            var lang = m.group(1).toLowerCase();
            assertFalse(block.containsKey(lang), "the language " + lang + " is twice in the block of \"" + block.get("en") + "\"");
            block.put(lang, m.group(2).strip());
            previousEnd = m.end();
        }
        return blocks;
    }

    /** The matches of a pattern in a text, sorted: the order of the words changes with the language. */
    private static List<String> all(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(r -> r.group()).sorted().toList();
    }
}
