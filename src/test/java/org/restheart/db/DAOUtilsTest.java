/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.restheart.db;

import org.bson.BsonDocument;
import org.bson.BsonString;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class DAOUtilsTest {

    private static final Logger LOG = LoggerFactory.getLogger(DAOUtilsTest.class);

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    public DAOUtilsTest() {
    }

    //@Test
    public void testGetDataFromRows() {
        // TODO
    }

    //@Test
    public void testGetDataFromRow() {
        // TODO
    }

    @Test
    public void testValidContent() {
        BsonDocument dbo = DAOUtils.validContent(null);
        assertNotNull(dbo);
        assertTrue(dbo.isDocument());

        dbo = new BsonDocument("name", new BsonString("test"));
        assertEquals(DAOUtils.validContent(dbo), dbo);
    }

}
