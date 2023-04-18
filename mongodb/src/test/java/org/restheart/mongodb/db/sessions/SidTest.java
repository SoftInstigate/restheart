/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SidTest {
    @Test
    public void testSessionOptionCase1() {
        for (int cont = 0; cont < 10; cont++) {
            var so = new SessionOptions(false);
            var sid = Sid.randomUUID(so);
            var so2 = Sid.getSessionOptions(sid);

            assertEquals(so, so2);
        }
    }

    @Test
    public void testSessionOptionCase2() {
        for (int cont = 0; cont < 10; cont++) {
            var so = new SessionOptions(true);
            var sid = Sid.randomUUID(so);
            var so2 = Sid.getSessionOptions(sid);

            assertEquals(so, so2);
        }
    }
}
