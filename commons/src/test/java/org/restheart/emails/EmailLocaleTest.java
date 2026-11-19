package org.restheart.emails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class EmailLocaleTest {

    private static final String TEMPLATE = """
            <html><head><title><span lang="en">Reset</span><span lang="it">Reimposta</span></title></head>
            <body><span lang="en">Hello</span><span lang="it">Ciao</span></body></html>""";

    private static JsonObject body(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static BsonDocument user(String locale) {
        var user = new BsonDocument("_id", new BsonString("a@example.com"));
        return locale == null ? user : user.append("locale", new BsonString(locale));
    }

    private static String bodyOf(String lang) {
        return EmailRenderer.render(TEMPLATE, Map.of(), lang).htmlBody();
    }

    @Test
    void aRequestWithNoLocaleHasNone() {
        assertNull(EmailLocale.ofRequest(body("{}")));
        assertNull(EmailLocale.ofRequest(body("{\"locale\": null}")));
        assertNull(EmailLocale.ofRequest((JsonObject) null));
        assertNull(EmailLocale.ofRequest(new BsonDocument()));
        assertNull(EmailLocale.ofRequest((BsonDocument) null));
    }

    @Test
    void theLocaleOfARequestIsALanguageCodeInLowerCase() {
        assertEquals("it", EmailLocale.ofRequest(body("{\"locale\": \"it\"}")));
        assertEquals("it", EmailLocale.ofRequest(body("{\"locale\": \" IT \"}")));
        assertEquals("ptbr", EmailLocale.ofRequest(body("{\"locale\": \"ptBR\"}")));
        assertEquals("it", EmailLocale.ofRequest(new BsonDocument("locale", new BsonString("IT"))));
    }

    @Test
    void anythingButALanguageCodeIsRefused() {
        for (var invalid : new String[]{
                "\"\"", "\"i\"", "\"italian\"", "\"it-IT\"", "\"it_IT\"", "\"i1\"",
                "\"it\\\"><script>\"", "\"it\\r\\nBcc: x@example.com\"",
                "1", "true", "[\"it\"]", "{\"lang\": \"it\"}"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> EmailLocale.ofRequest(body("{\"locale\": " + invalid + "}")), invalid);
        }
        assertThrows(IllegalArgumentException.class,
                () -> EmailLocale.ofRequest(new BsonDocument("locale", new BsonString("it-IT"))));
        assertThrows(IllegalArgumentException.class,
                () -> EmailLocale.ofRequest(new BsonDocument("locale", new BsonInt32(1))));
    }

    @Test
    void theLocaleOfAUserIsReadOnlyWhenItIsALanguageCode() {
        assertEquals("it", EmailLocale.ofUser(user("it")));
        assertEquals("it", EmailLocale.ofUser(user("IT")));
        assertNull(EmailLocale.ofUser(user(null)));
        assertNull(EmailLocale.ofUser(user("it-IT")));
        assertNull(EmailLocale.ofUser(user("").append("locale", new BsonInt32(1))));
        assertNull(EmailLocale.ofUser(null));
    }

    @Test
    void theUsersLocaleWinsOverTheRequestsAndTheRequestsOverTheDefault() {
        assertEquals("en", EmailLocale.choose(user("en"), "it", "fr"));
        assertEquals("it", EmailLocale.choose(user(null), "it", "fr"));
        assertEquals("it", EmailLocale.choose(null, "it", "fr"));
        assertEquals("fr", EmailLocale.choose(user(null), null, "fr"));
        assertEquals("fr", EmailLocale.choose(null, null, "fr"));
        // a stored value that is not a language code counts as none
        assertEquals("it", EmailLocale.choose(user("it-IT"), "it", "fr"));
    }

    @Test
    void anExistingUserWithALocaleGetsTheEmailInIt() {
        assertTrue(bodyOf(EmailLocale.choose(user("en"), "it", "en")).contains("Hello"));
    }

    @Test
    void aUserWithNoLocaleGetsTheEmailInTheLanguageOfTheRequest() {
        var html = bodyOf(EmailLocale.choose(user(null), "it", "en"));
        assertTrue(html.contains("Ciao"));
        assertTrue(!html.contains("Hello"));
    }

    @Test
    void aLanguageTheTemplateDoesNotHaveFallsBackToEnglish() {
        var html = bodyOf(EmailLocale.choose(user(null), "de", "it"));
        assertTrue(html.contains("Hello"));
        assertTrue(!html.contains("Ciao"));
    }

    @Test
    void noLocaleAnywhereIsTheDefault() {
        assertTrue(bodyOf(EmailLocale.choose(user(null), null, "it")).contains("Ciao"));
    }
}
