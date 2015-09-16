/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.restheart.db;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
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
        DBObject dbo = DAOUtils.validContent(null);
        assertNotNull(dbo);
        assertTrue(dbo instanceof BasicDBObject);
        
        dbo = new BasicDBObject("name", "test");
        assertEquals(DAOUtils.validContent(dbo), dbo);
    }
    
}
