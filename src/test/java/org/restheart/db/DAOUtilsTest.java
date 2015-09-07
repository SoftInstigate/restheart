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

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class DAOUtilsTest {
    
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
        System.out.println("validContent");
        DBObject dbo = DAOUtils.validContent(null);
        assertNotNull(dbo);
        assertTrue(dbo instanceof BasicDBObject);
        
        dbo = new BasicDBObject("name", "test");
        assertEquals(DAOUtils.validContent(dbo), dbo);
    }
    
}
