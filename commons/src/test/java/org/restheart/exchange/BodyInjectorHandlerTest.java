/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import io.undertow.server.handlers.form.FormData;

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
     *
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    @Test
    public void test_extractProperties()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final String jsonString = "{\"key1\": \"value1\", \"key2\": \"value2\"}";
        FormData formData = new FormData(1);

        formData.add("properties", jsonString);
        BsonDocument result = MongoRequestContentInjector.extractMetadata(formData);
        BsonDocument expected = BsonDocument.parse(jsonString);
        assertEquals(expected, result);
    }

}
