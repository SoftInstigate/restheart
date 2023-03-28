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
package org.restheart.exchange;

import io.undertow.server.handlers.form.FormData;
import org.bson.BsonDocument;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Maurizio Turatti
 */
public class BodyInjectorHandlerTest {

    /**
     *
     */
    public BodyInjectorHandlerTest() {
    }

    /**
     * If formData contains a PROPERTIES part, then must be valid JSON
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    @Test
    public void test_extractProperties() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final String jsonString = "{\"key1\": \"value1\", \"key2\": \"value2\"}";
        FormData formData = new FormData(1);

        formData.add("properties", jsonString);
        BsonDocument result = MongoRequestContentInjector.extractMetadata(formData);
        BsonDocument expected = BsonDocument.parse(jsonString);
        assertEquals(expected, result);
    }

}
