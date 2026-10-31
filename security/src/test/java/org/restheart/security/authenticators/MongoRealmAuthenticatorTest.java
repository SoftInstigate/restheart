/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.authenticators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The password check of {@link MongoRealmAuthenticator}, in both hashing modes.
 *
 * <p>An account with an empty stored password has no password at all: restheart-accounts creates
 * OAuth users that way and clears the password when it activates an unverified account through
 * OAuth. Such an account must refuse every Basic credential, the empty one included, whatever
 * {@code bcrypt-hashed-password} says. In bcrypt mode the check already failed because an empty
 * string is not a hash; in plaintext mode {@code "" equals ""} used to pass.
 */
public class MongoRealmAuthenticatorTest {

    /** bcrypt of "secret", the hash of the default admin user in restheart-default-config.yml. */
    private static final String SECRET_HASH = "$2a$12$lZiMMNJ6pkyg4uq/I1cF5uxzUbU25aXHtg7W7sD2ED7DG1wzUoo6u";

    private final MongoRealmAuthenticator authenticator = new MongoRealmAuthenticator();

    private boolean check(boolean hashed, String candidate, String stored) {
        return authenticator.checkPassword("users-db", "alice", hashed,
                candidate == null ? null : candidate.toCharArray(),
                stored == null ? null : stored.toCharArray());
    }

    @Test
    void emptyStoredPasswordMatchesNothingInBcryptMode() {
        assertFalse(check(true, "", ""));
        assertFalse(check(true, "secret", ""));
    }

    @Test
    void emptyStoredPasswordMatchesNothingInPlaintextMode() {
        assertFalse(check(false, "", ""));
        assertFalse(check(false, "secret", ""));
    }

    @Test
    void nullStoredPasswordMatchesNothing() {
        assertFalse(check(true, "", null));
        assertFalse(check(false, "", null));
    }

    @Test
    void realPasswordsStillVerifyInBcryptMode() {
        assertTrue(check(true, "secret", SECRET_HASH));
        assertFalse(check(true, "", SECRET_HASH));
        assertFalse(check(true, "wrong", SECRET_HASH));
    }

    @Test
    void realPasswordsStillVerifyInPlaintextMode() {
        assertTrue(check(false, "secret", "secret"));
        assertFalse(check(false, "", "secret"));
        assertFalse(check(false, "wrong", "secret"));
    }
}
