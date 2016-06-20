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
package org.restheart.handlers.injectors;

import io.undertow.server.handlers.form.FormData;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Maurizio Turatti
 */
public class BodyInjectorHandlerTest {

    public BodyInjectorHandlerTest() {
    }

    /**
     * If filename is not null and properties don't have a filename then put the
     * filename
     */
    @Test
    public void test_putFilename() {
        BsonDocument properties = new BsonDocument();
        BsonString expectedFilename = new BsonString("myFilename");
        BodyInjectorHandler.putFilename(
                expectedFilename.getValue(),
                "defaultFilename", properties);

        assertEquals(
                expectedFilename,
                properties.get(BodyInjectorHandler.FILENAME));
    }

    /**
     * If filename is not null but properties contain a filename key then put
     * the properties filename value
     */
    @Test
    public void test_overrideFilename() {
        BsonString expectedFilename = new BsonString("other");

        BsonDocument properties
                = BsonDocument.parse(
                        "{\"filename\": \""
                        + expectedFilename.getValue()
                        + "\"}");

        BodyInjectorHandler.putFilename(
                "formDataFilename",
                "defaultFilename",
                properties);
        assertEquals(
                expectedFilename,
                properties.get(BodyInjectorHandler.FILENAME));
    }

    /**
     * If both filename is null and properties don't contain a filename then use
     * the default value
     */
    @Test
    public void test_emptyFilename() {
        BsonString expectedFilename = new BsonString("defaultFilename");
        BsonDocument properties = new BsonDocument();
        BodyInjectorHandler.putFilename("", 
                expectedFilename.getValue(), 
                properties);

        assertEquals(
                expectedFilename,
                properties.get(BodyInjectorHandler.FILENAME));
    }

    /**
     * If formData contains a PROPERTIES part, then must be valid JSON
     */
    @Test
    public void test_extractProperties() {
        final String jsonString
                = "{\"key1\": \"value1\", \"key2\": \"value2\"}";
        FormData formData = new FormData(1);
        formData.add(BodyInjectorHandler.PROPERTIES, jsonString);
        BsonDocument result = BodyInjectorHandler.extractProperties(formData);
        BsonDocument expected = BsonDocument.parse(jsonString);
        assertEquals(expected, result);
    }

}
