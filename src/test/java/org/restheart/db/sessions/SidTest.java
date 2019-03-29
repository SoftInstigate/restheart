/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.db.sessions;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SidTest {

    public SidTest() {
    }

    @Test
    public void testSessionOptionCase1() {
        for (int cont = 0; cont < 10; cont++) {
            var so = new SessionOptions(false);
            var sid = Sid.randomUUID(so);
            var so2 = Sid.getSessionOptions(sid);
            
            Assert.assertEquals(so, so2);
        }
    }
    
    @Test
    public void testSessionOptionCase2() {
        for (int cont = 0; cont < 10; cont++) {
            var so = new SessionOptions(true);
            var sid = Sid.randomUUID(so);
            var so2 = Sid.getSessionOptions(sid);
            
            Assert.assertEquals(so, so2);
        }
    }
}
