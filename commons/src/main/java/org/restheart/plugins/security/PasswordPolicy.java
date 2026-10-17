/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.plugins.security;

import java.util.List;

/**
 * What makes a password acceptable, and how strongly it is hashed.
 *
 * <p>Injected with {@code @Inject("passwordPolicy")}. The implementation lives in
 * restheart-security, next to the authenticator that will later verify these passwords and that
 * owns the settings behind them; this interface lives in restheart-commons so that a module setting
 * a password — restheart-accounts, for one — needs only commons at compile time.
 *
 * <p>Having one policy is the point. A password set through an accounts endpoint and one written
 * straight to the users collection must be judged the same way, or a deployment ends up with a rule
 * that holds on one path and not the other, and nobody notices until the weak password is already
 * stored.
 */
public interface PasswordPolicy {

    /** Whether a weak password is refused at all. When false, {@link #weaknessOf} finds nothing. */
    boolean enforced();

    /** The lowest acceptable strength score, 0 (weak) to 4 (very strong). */
    int minimumStrength();

    /** BCrypt cost, as log2 of the iteration count, for whoever hashes the password. */
    int bcryptComplexity();

    /**
     * Why this password cannot be used, or {@code null} when it can.
     *
     * <p>A null password returns null: that is a missing field for the caller to report, not a weak
     * password.
     */
    Weakness weaknessOf(String password);

    /**
     * What is wrong with a password. Both fields can be empty — the estimator does not always have
     * something to say, and a caller rendering them must not promise advice it does not have.
     */
    record Weakness(String warning, List<String> suggestions) {
    }

    /**
     * The same thing as one line of prose, for callers whose errors are plain text.
     *
     * <p>Carries the warning and suggestions when there are any: "too weak" on its own tells someone
     * to try again without telling them what to try.
     *
     * @return the message, or {@code null} when the password is acceptable
     */
    default String rejection(String password) {
        var weakness = weaknessOf(password);

        if (weakness == null) {
            return null;
        }

        var message = new StringBuilder("Password is too weak");

        if (weakness.warning() != null && !weakness.warning().isBlank()) {
            message.append(": ").append(weakness.warning());
        }

        if (weakness.suggestions() != null && !weakness.suggestions().isEmpty()) {
            message.append(" (").append(String.join(" ", weakness.suggestions())).append(")");
        }

        return message.toString();
    }
}
