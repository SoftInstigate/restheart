/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import java.nio.ByteBuffer;
import java.util.UUID;
import static org.restheart.mongodb.db.sessions.SessionOptions.CAUSALLY_CONSISTENT_FLAG;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Sid {

    /**
     * retrieve a type 4 (pseudo randomly generated) UUID.
     *
     * The {@code UUID} is generated using a cryptographically strong pseudo
     * random number generator.
     *
     * @return A randomly generated {@code UUID}
     */
    public static UUID randomUUID() {
        return UUID.randomUUID();
    }

    /**
     * retrieve a type 4 (pseudo randomly generated) UUID, where MSB 3 and 4 of
     * the A byte are used to flag session options
     *
     * The {@code UUID} is generated using a cryptographically strong pseudo
     * random number generator weakend by using 2 bits for flagging.
     *
     * @param options
     * @return A randomly generated {@code UUID}
     */
    public static UUID randomUUID(SessionOptions options) {
        var uuid = UUID.randomUUID();

        var lsb = longToBytes(uuid.getLeastSignificantBits());

        setCasuallyConsistentFlag(lsb, options.isCausallyConsistent());

        return new UUID(uuid.getMostSignificantBits(), bytesToLong(lsb));
    }

    /**
     *
     * @param uuid
     * @return
     */
    public static SessionOptions getSessionOptions(UUID uuid) {
        var lsb = longToBytes(uuid.getLeastSignificantBits());

        boolean ccf = (lsb[0] & CAUSALLY_CONSISTENT_FLAG)
                == CAUSALLY_CONSISTENT_FLAG;

        return new SessionOptions(ccf);
    }

    /**
     *
     * sets the MSB3 of the UUID A byte<br>
     * <br>
     * UUID format:<br>
     * <br>
     * 123e4567-e89b-42d3-a456-556642440000<br>
     * xxxxxxxx-xxxx-Bxxx-Axxx-xxxxxxxxxxxx<br>
     * <br>
     * Where MSB1 and MSB2 of A are reserverd for UUID variant code<br>
     *
     * @param lsb
     * @param value
     */
    static void setCasuallyConsistentFlag(byte[] lsb, boolean value) {
        if (value) {
            lsb[0] |= CAUSALLY_CONSISTENT_FLAG; // 0010 0000
        } else {
            lsb[0] &= 0xDF; // 1101 1111
        }
    }

    static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }
}
