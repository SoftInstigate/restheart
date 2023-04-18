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
package org.restheart.mongodb.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class DAOUtilsTest {

    // private static final Logger LOG =
    // LoggerFactory.getLogger(DAOUtilsTest.class);

    /**
     *
     */
    // @Rule
    // public TestRule watcher = new TestWatcher() {
    // @Override
    // protected void starting(Description description) {
    // LOG.info("executing test {}", description.toString());
    // }
    // };

    /**
     *
     */
    public DAOUtilsTest() {
    }

    /**
     *
     */
    @Test
    public void testValidContent() {
        BsonDocument dbo = DbUtils.validContent(null);
        assertNotNull(dbo);
        assertTrue(dbo.isDocument());

        dbo = new BsonDocument("name", new BsonString("test"));
        assertEquals(DbUtils.validContent(dbo), dbo);
    }

}
