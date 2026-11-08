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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the RFC 2046 boundary validation of {@link MultipartRequest} (#797).
 */
public class MultipartRequestTest {

    @Test
    public void validBoundaries() {
        assertEquals("benchmark-boundary", MultipartRequest.boundary("multipart/form-data; boundary=benchmark-boundary"));
        assertEquals("x", MultipartRequest.boundary("multipart/form-data; boundary=x"));
        assertEquals("quoted:boundary", MultipartRequest.boundary("multipart/form-data; boundary=\"quoted:boundary\""));
        assertEquals("abc", MultipartRequest.boundary("multipart/form-data; charset=utf-8; BOUNDARY=abc"));
        assertEquals("a".repeat(70), MultipartRequest.boundary("multipart/form-data; boundary=" + "a".repeat(70)));
        assertEquals("with space inside", MultipartRequest.boundary("multipart/form-data; boundary=\"with space inside\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "multipart/form-data",
        // Headers.extractQuotedValueFromHeader throws StringIndexOutOfBoundsException on this one
        "multipart/form-data; boundary=",
        "multipart/form-data; boundary=\"\"",
        "multipart/form-data; boundary=\"unbalanced",
        "multipart/form-data; boundary=a; boundary=b",
        "multipart/form-data; boundary=\"ends with space \"",
        "multipart/form-data; boundary=invalid<char>"
    })
    public void invalidBoundaries(String contentType) {
        assertNull(MultipartRequest.boundary(contentType), contentType);
    }

    @Test
    public void tooLongBoundary() {
        assertNull(MultipartRequest.boundary("multipart/form-data; boundary=" + "a".repeat(71)));
    }

    @Test
    public void nullContentType() {
        assertNull(MultipartRequest.boundary(null));
    }
}
