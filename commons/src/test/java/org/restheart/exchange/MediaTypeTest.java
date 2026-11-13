/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * Tests the media type helpers of {@link Exchange} (#798).
 */
public class MediaTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "application/json",
            "application/json; charset=utf-8",
            "Application/JSON",
            "application/json ;charset=UTF-8",
            "application/problem+json",
            "application/vnd.api+json",
            "application/vnd.benchmark+json; charset=utf-8",
            "application/hal+json",
            "text/x-json",
            "application/jsonrequest"
    })
    public void jsonMediaTypes(String contentType) {
        assertTrue(Exchange.isJsonMediaType(contentType), contentType);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            " ",
            "text/plain",
            "application/xml",
            "application/x-www-form-urlencoded",
            "application/jsonp",
            "application/json-patch",
            "/+json",
            "application/+json",
            "+json",
            "multipart/form-data; boundary=x"
    })
    public void notJsonMediaTypes(String contentType) {
        assertFalse(Exchange.isJsonMediaType(contentType), String.valueOf(contentType));
    }

    @Test
    public void mediaTypeEssence() {
        assertEquals("multipart/form-data", Exchange.mediaType("Multipart/Form-Data; boundary=abc"));
        assertEquals("application/json", Exchange.mediaType(" application/json "));
        assertNull(Exchange.mediaType(null));
        assertNull(Exchange.mediaType(" ; charset=utf-8"));
    }
}
