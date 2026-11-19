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

import java.util.regex.Pattern;

import org.bson.BsonDocument;

import com.google.gson.JsonObject;

/**
 * The language of the emails the plugins send: restheart-accounts and restheart-stripe.
 *
 * <p>An email is rendered, in order, in:
 * <ol>
 *   <li>the recipient's {@code locale}, when their user document has one: the user's choice;</li>
 *   <li>the {@code locale} of the request body: the client's guess;</li>
 *   <li>{@code default-locale} of the plugin's configuration.</li>
 * </ol>
 *
 * <p>A {@code locale} is a language code as the {@code lang} attribute of the templates takes
 * it: 2 to 5 letters, stored and compared in lower case. It only selects a version of the
 * template, it never reaches the text of the email. A language the template does not have
 * falls back to {@code en}, in {@link EmailRenderer}.
 */
public final class EmailLocale {

    /** The property of the request body and of the user document. */
    public static final String FIELD = "locale";

    // the same shape EmailRenderer accepts in <span lang="xx">
    private static final Pattern LANG = Pattern.compile("^[a-zA-Z]{2,5}$");

    private EmailLocale() {
        // utility class — no instances
    }

    /**
     * The {@code locale} of a request body.
     *
     * @param body the JSON body of the request
     * @return the language code in lower case, or {@code null} when the body has none
     * @throws IllegalArgumentException when {@code locale} is present and is not a language
     *         code; the message is meant for the 400 response
     */
    public static String ofRequest(JsonObject body) {
        if (body == null || !body.has(FIELD) || body.get(FIELD).isJsonNull()) {
            return null;
        }
        var value = body.get(FIELD);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(INVALID);
        }
        var locale = normalize(value.getAsString());
        if (locale == null) {
            throw new IllegalArgumentException(INVALID);
        }
        return locale;
    }

    /**
     * The {@code locale} of a request body, for the services that read it as BSON.
     *
     * @param body the body of the request
     * @return the language code in lower case, or {@code null} when the body has none
     * @throws IllegalArgumentException when {@code locale} is present and is not a language
     *         code; the message is meant for the 400 response
     */
    public static String ofRequest(BsonDocument body) {
        if (body == null || !body.containsKey(FIELD) || body.get(FIELD).isNull()) {
            return null;
        }
        var locale = body.get(FIELD).isString() ? normalize(body.getString(FIELD).getValue()) : null;
        if (locale == null) {
            throw new IllegalArgumentException(INVALID);
        }
        return locale;
    }

    /**
     * A value as a language code.
     *
     * @param value any string, or {@code null}
     * @return the language code in lower case, or {@code null} when the value is not one
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        var locale = value.strip();
        return LANG.matcher(locale).matches() ? locale.toLowerCase(java.util.Locale.ROOT) : null;
    }

    private static final String INVALID = "locale must be a language code of 2 to 5 letters, such as 'en' or 'it'";

    /**
     * The {@code locale} stored on a user document.
     *
     * @param user the user document, or {@code null} when there is none
     * @return the language code in lower case, or {@code null} when the document has none or
     *         holds something that is not a language code
     */
    public static String ofUser(BsonDocument user) {
        if (user == null || !user.containsKey(FIELD) || !user.get(FIELD).isString()) {
            return null;
        }
        return normalize(user.getString(FIELD).getValue());
    }

    /**
     * The language of an email to a recipient.
     *
     * @param user          the recipient's user document, or {@code null} when there is none
     * @param requestLocale the {@code locale} of the request, as returned by
     *                      {@link #ofRequest}, or {@code null}
     * @param defaultLocale {@code default-locale} of the plugin's configuration
     * @return the recipient's locale, else the request's, else the default
     */
    public static String choose(BsonDocument user, String requestLocale, String defaultLocale) {
        var stored = ofUser(user);
        if (stored != null) {
            return stored;
        }
        return requestLocale != null ? requestLocale : defaultLocale;
    }
}
