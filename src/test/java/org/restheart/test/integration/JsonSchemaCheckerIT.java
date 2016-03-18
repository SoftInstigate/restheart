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
package org.restheart.test.integration;

import com.mashape.unirest.http.Unirest;
import java.net.URISyntaxException;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonSchemaCheckerIT extends AbstactIT {

    public JsonSchemaCheckerIT() throws URISyntaxException {
    }

    @Before
    public void createTestData() throws Exception {
        Unirest.post("http://httpbin.org/post")
                .queryString("name", "Mark")
                .field("last", "Polo")
                .asJson() ;


    }

    @Test
    public void testPostData() throws Exception {
        // *** test create invalid data

        // *** test create valid data

        // *** test update invalid data
    }

    @Test
    public void testPostDataDotNotation() throws Exception {
        // *** test post valid data with dot notation
    }

    @Test
    public void testPostIncompleteDataDotNotation() throws Exception {
        // *** test post valid data with dot notation
    }

    @Test
    public void testPutDataDotNotation() throws Exception {
        // *** test post valid data with dot notation
    }

    @Test
    public void testPatchData() throws Exception {
        // *** test create valid data
        // *** test patch valid data with dot notation
        // *** test patch invalid key
        // *** test patch invalid key
        // *** test patch wrong type object data
        // *** test patch invalid array data
    }

    /**
     * see bug https://softinstigate.atlassian.net/browse/RH-160
     *
     * @throws Exception
     */
    @Test
    public void testPatchIncompleteObject() throws Exception {
        // *** test create valid data

        // *** test patch valid data with dot notation
        // an incomplete details object. address and country are nullable but mandatory
    }
}
