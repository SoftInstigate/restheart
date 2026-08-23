/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security;

import java.util.Arrays;

import io.undertow.security.idm.Credential;

/**
 * An opaque, self-identifying API key.
 *
 * <p>Unlike a {@link io.undertow.security.idm.PasswordCredential}, this carries
 * no principal alongside it: the key <em>is</em> the identifier, and the
 * Authenticator resolves the account from the key itself. That is why an
 * Authenticator verifying one implements {@code verify(Credential)} — the
 * single-argument form — rather than {@code verify(String, Credential)}.
 *
 * <p>The key is held as a {@code char[]} so that a caller may
 * {@link #clear()} it once verification is done, on the same reasoning as
 * {@code PasswordCredential}: a {@code String} would linger in the heap until
 * it happened to be collected.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ApiKeyCredential implements Credential {
    private final char[] key;

    /**
     * @param key the key exactly as presented by the client, prefix included
     */
    public ApiKeyCredential(final char[] key) {
        this.key = key;
    }

    /**
     * @param key the key exactly as presented by the client, prefix included
     */
    public ApiKeyCredential(final String key) {
        this.key = key == null ? new char[0] : key.toCharArray();
    }

    /**
     * @return the key, prefix included. Not a copy — treat as read-only.
     */
    public char[] getKey() {
        return this.key;
    }

    /**
     * Overwrites the key in place.
     */
    public void clear() {
        Arrays.fill(this.key, '\0');
    }

    /**
     * Never the key. This object ends up in log statements and stack traces by
     * accident, and a credential that prints itself is a credential in a log
     * file.
     */
    @Override
    public String toString() {
        return "ApiKeyCredential[REDACTED]";
    }
}
