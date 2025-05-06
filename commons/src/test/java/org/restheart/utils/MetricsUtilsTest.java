/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
package org.restheart.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.restheart.metrics.Metrics;

public class MetricsUtilsTest {
    @Test
    public void testLast() {
        assertEquals("two", Metrics.xffValue("[one, two, last]", 1));
        assertEquals("one", Metrics.xffValue("[one, two, last]", 2));
        assertEquals("one", Metrics.xffValue("     [one, two, last]    ", 3));
        assertEquals("last", Metrics.xffValue("[last]", 0));
        assertEquals("last", Metrics.xffValue("last", 2));
    }
}
