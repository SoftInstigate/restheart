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
package org.restheart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests the core options added in 9.15.0 (#794, #799, #800).
 */
public class CoreModuleTest {

    @Test
    public void defaults() {
        var core = CoreModule.build(new HashMap<>(), true);

        assertTrue(core.responseCompression());
        assertEquals(1024 * 1024, core.nonBlockingContentLimit());
        assertEquals(64 * 1024, core.blockingContentPrebufferLimit());
    }

    @Test
    public void fromConfiguration() {
        Map<String, Object> conf = new HashMap<>();
        Map<String, Object> core = new HashMap<>();
        core.put(CoreModule.RESPONSE_COMPRESSION_KEY, false);
        core.put(CoreModule.NON_BLOCKING_CONTENT_LIMIT_KEY, 2048);
        core.put(CoreModule.BLOCKING_CONTENT_PREBUFFER_LIMIT_KEY, 0);
        conf.put(CoreModule.CORE_KEY, core);

        var module = CoreModule.build(conf, true);

        assertFalse(module.responseCompression());
        assertEquals(2048, module.nonBlockingContentLimit());
        assertEquals(0, module.blockingContentPrebufferLimit());
    }
}
