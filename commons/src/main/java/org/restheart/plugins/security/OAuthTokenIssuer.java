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
package org.restheart.plugins.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.restheart.exchange.Request;

import io.undertow.security.idm.Account;

/**
 * Decides which token the Authorization Code + PKCE flow issues, and what the user may choose
 * about it on the sign-in page before it exists.
 *
 * <p>Without one of these, the flow has one outcome: {@code POST /token} mints the JWT of the
 * account that signed in, with every role that account holds, renewable without end. A
 * deployment that has narrower, revocable credentials of its own — API keys, scoped tokens —
 * registers an issuer to hand one of those out instead. What is offered, to whom, and what is
 * minted are the issuer's business; RESTHeart only knows the three moments at which to ask.
 *
 * <p><strong>Registering an implementation is the switch.</strong> With none registered, the
 * page, {@code /authorize} and {@code /token} behave exactly as they always have. A deployment
 * supplies one as a {@code Provider<OAuthTokenIssuer>}; the OAuth services find it by type, so
 * no name has to be agreed between the two. One is active per instance.
 *
 * <h2>The three moments</h2>
 *
 * <ol>
 *   <li>{@link #offer} — the page has verified the credentials with {@code GET /authorize/offer}
 *       and asks what this account may choose. Empty means no choice step: the page submits at
 *       once and the default token is issued. A {@link Refusal} stops the page: this account
 *       obtains nothing here.</li>
 *   <li>{@link #claims} — {@code POST /authorize} has authenticated the account and passes it the
 *       choice. What comes back is sealed in the authorization code, signed, and is all the
 *       issuer will see again at {@code /token}: the code is what carries the decision across the
 *       redirect, statelessly. An empty map means no issuer claims and the default token.</li>
 *   <li>{@link #issue} — {@code POST /token} presents the sealed claims and wants the token. The
 *       response is the issuer's; {@code refresh_token} only if it gives one. A code sealed with
 *       issuer claims is never answered with the default token, whatever happens to the issuer in
 *       between: falling back would hand out the wide credential the issuer exists to replace.</li>
 * </ol>
 *
 * <p>The account at {@link #claims} is the one the credentials produced; at {@link #issue} it is
 * rebuilt from the code, with the same principal and roles. Anything the issuer needs at
 * {@code /token} beyond that goes in the claims — they are signed, not encrypted, and travel in
 * the redirect URL, so never a secret.
 *
 * <h2>The choice</h2>
 *
 * <p>An {@link Offer} names {@link Choice}s; the page renders them without interpreting them and
 * sends the user's answers back as {@code choice.<name>} query parameters on
 * {@code POST /authorize}. A client that knows what it wants says so with the OAuth {@code scope}
 * parameter, one {@code <name>:<value>} entry per choice: the page uses it to preselect, and
 * {@code /authorize} applies it when the page sent nothing for that choice. The issuer receives
 * one map either way, and validates it: nothing the page or the client sent is trusted.
 */
public interface OAuthTokenIssuer {

    /**
     * What the sign-in page may offer this account, once its credentials are verified.
     *
     * @param account the authenticated account
     * @param request the {@code GET /authorize/offer} request, carrying the OAuth query parameters
     * @return the offer, or empty for no choice step and the default token
     * @throws Refusal to stop the page: this account obtains no token here
     */
    Optional<Offer> offer(Account account, Request<?> request) throws Refusal;

    /**
     * Validates the user's choice and returns what to seal in the authorization code.
     *
     * @param account the authenticated account
     * @param request the {@code POST /authorize} request, carrying the OAuth query parameters
     * @param choice the choice, by choice name; empty when nothing was chosen
     * @return the claims to seal, by name; empty for no issuer claims and the default token
     * @throws Refusal to refuse the choice; sent back to the page or to the client
     */
    Map<String, Object> claims(Account account, Request<?> request, Map<String, String> choice) throws Refusal;

    /**
     * Mints the token from the claims sealed at {@code /authorize}.
     *
     * @param claims what {@link #claims} returned, as read back from the code
     * @param account the account rebuilt from the code
     * @param request the {@code POST /token} request
     * @return the token
     * @throws Refusal to refuse the grant, as an OAuth token error
     */
    IssuedToken issue(Map<String, Object> claims, Account account, Request<?> request) throws Refusal;

    /**
     * What the sign-in page shows between the credentials and the submit.
     *
     * @param title a heading for the step, or {@code null} for the page's own
     * @param message a line of explanation shown above the choices, or {@code null}
     * @param choices what the user decides; may be empty, and the step then only shows the message
     */
    record Offer(String title, String message, List<Choice> choices) {
        public Offer {
            choices = choices == null ? List.of() : List.copyOf(choices);
        }
    }

    /**
     * One thing the user decides.
     *
     * @param name the parameter name, sent back as {@code choice.<name>}
     * @param label what the page shows next to it
     * @param type {@link Type#SELECT}, {@link Type#NUMBER} or {@link Type#TEXT}
     * @param options the options of a {@link Type#SELECT}; ignored otherwise
     * @param min the lower bound of a {@link Type#NUMBER}, or {@code null}
     * @param max the upper bound of a {@link Type#NUMBER}, or {@code null}
     * @param value the preselected value, or {@code null}
     */
    record Choice(String name, String label, Type type, List<Option> options, Integer min, Integer max, String value) {
        public Choice {
            options = options == null ? List.of() : List.copyOf(options);
        }

        /** A choice among the given options. */
        public static Choice select(String name, String label, List<Option> options, String value) {
            return new Choice(name, label, Type.SELECT, options, null, null, value);
        }

        /** A whole number within bounds. */
        public static Choice number(String name, String label, int min, int max, int value) {
            return new Choice(name, label, Type.NUMBER, null, min, max, Integer.toString(value));
        }

        /** Free text. */
        public static Choice text(String name, String label, String value) {
            return new Choice(name, label, Type.TEXT, null, null, null, value);
        }
    }

    /** How the page renders a {@link Choice}. */
    enum Type {
        SELECT, NUMBER, TEXT
    }

    /**
     * An option of a {@link Type#SELECT} choice.
     *
     * @param value what is sent back
     * @param label what the page shows
     */
    record Option(String value, String label) {
    }

    /**
     * What {@code POST /token} answers.
     *
     * @param accessToken the token
     * @param tokenType the {@code token_type}, usually {@code Bearer}
     * @param expiresIn seconds until expiry, or {@code null} to leave {@code expires_in} out
     * @param refreshToken a refresh token, or {@code null} for none
     */
    record IssuedToken(String accessToken, String tokenType, Long expiresIn, String refreshToken) {
        /** A bearer token that expires and does not refresh. */
        public static IssuedToken bearer(String accessToken, long expiresIn) {
            return new IssuedToken(accessToken, "Bearer", expiresIn, null);
        }
    }

    /**
     * Why an account, a choice or a grant is refused, in OAuth terms.
     */
    class Refusal extends Exception {
        private static final long serialVersionUID = 1L;

        private final String error;

        /**
         * @param error the OAuth error code: {@code access_denied}, {@code invalid_scope}, …
         * @param description what to tell the user, or the client
         */
        public Refusal(String error, String description) {
            super(description);
            this.error = error;
        }

        /** The OAuth error code. */
        public String error() {
            return error;
        }

        /** The description. */
        public String description() {
            return getMessage();
        }
    }
}
