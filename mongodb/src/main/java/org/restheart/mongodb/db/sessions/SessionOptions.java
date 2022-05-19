/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.mongodb.db.sessions;

import com.google.common.base.Objects;
import java.util.UUID;
import static org.restheart.mongodb.db.sessions.Sid.longToBytes;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SessionOptions {

    /**
     *
     */
    public static final int CAUSALLY_CONSISTENT_FLAG = 0x20; // 0010 0000

    /**
     *
     */
    public static final String CAUSALLY_CONSISTENT_PROP = "causallyConsistent";

    private final boolean causallyConsistent;

    /**
     *
     * @param causallyConsistent
     */
    public SessionOptions(boolean causallyConsistent) {
        this.causallyConsistent = causallyConsistent;
    }

    /**
     *
     */
    public SessionOptions() {
        this(true);
    }

    /**
     *
     * @param sid
     */
    public SessionOptions(UUID sid) {
        var lsb = longToBytes(sid.getLeastSignificantBits());

        this.causallyConsistent = (lsb[0] & CAUSALLY_CONSISTENT_FLAG) == CAUSALLY_CONSISTENT_FLAG;
    }

    /**
     * @return the causallyConsistent
     */
    public boolean isCausallyConsistent() {
        return causallyConsistent;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SessionOptions otherSessionOptions) {
            return Objects.equal(this.causallyConsistent, otherSessionOptions.causallyConsistent);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(causallyConsistent);
    }

    @Override
    public String toString() {
        return "SessionOptions(causallyConsistent= " + this.causallyConsistent + ")";
    }
}
