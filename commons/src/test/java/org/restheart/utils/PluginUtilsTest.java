/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.JsonInterceptor;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginUtilsTest {
    @Test
    public void testDontInterceptDefault() {
        var plugin = new TestPluginDefault();

        InterceptPoint[] expected = {};

        assertArrayEquals(expected,
                PluginUtils.dontIntercept(plugin));
    }

    @Test
    public void testDontIntercept() {
        var plugin = new TestPlugin();

        InterceptPoint[] expected = { InterceptPoint.REQUEST_AFTER_AUTH, InterceptPoint.RESPONSE };

        assertArrayEquals(expected,
                PluginUtils.dontIntercept(plugin));
    }

    @Test
    public void testDefaultUri() {
        var plugin = new TestPlugin();

        assertEquals(PluginUtils.defaultURI(plugin), "/test");
    }

    @Test
    public void testDefaultUriDefault() {
        var plugin = new TestPluginDefault();

        assertEquals(PluginUtils.defaultURI(plugin), "/testDefaultPlugin");
    }

    @Test
    public void testInterceptPoint() {
        var plugin = new TestPlugin();

        assertEquals(InterceptPoint.REQUEST_BEFORE_AUTH,
                PluginUtils.interceptPoint(plugin));
    }

    @Test
    public void testInterceptPointDefault() {
        var plugin = new TestPluginDefault();

        assertEquals(InterceptPoint.REQUEST_AFTER_AUTH,
                PluginUtils.interceptPoint(plugin));
    }

    @Test
    public void testInitPoint() {
        var plugin = new TestPlugin();

        assertEquals(InitPoint.BEFORE_STARTUP,
                PluginUtils.initPoint(plugin));
    }

    @Test
    public void testInitPointDefault() {
        var plugin = new TestPluginDefault();

        assertEquals(InitPoint.AFTER_STARTUP,
                PluginUtils.initPoint(plugin));
    }

    @Test
    public void testRequiresContent() {
        var plugin = new TestPlugin();

        assertEquals(true, PluginUtils.requiresContent(plugin));
    }

    @Test
    public void testRequiresContentDefault() {
        var plugin = new TestPluginDefault();

        assertEquals(false, PluginUtils.requiresContent(plugin));
    }

    @RegisterPlugin(name = "testPlugin", description = "test description", defaultURI = "/test", dontIntercept = {
            InterceptPoint.REQUEST_AFTER_AUTH,
            InterceptPoint.RESPONSE }, priority = 100, enabledByDefault = false, interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH, initPoint = InitPoint.BEFORE_STARTUP, requiresContent = true)
    private static class TestPlugin implements JsonService, JsonInterceptor, Initializer {
        @Override
        public void handle(JsonRequest r, JsonResponse s) throws Exception {

        }

        @Override
        public boolean resolve(JsonRequest r, JsonResponse s) {
            return true;
        }

        @Override
        public void init() {

        }
    }

    @RegisterPlugin(name = "testDefaultPlugin", description = "test description")
    private static class TestPluginDefault implements JsonService,
            JsonInterceptor, Initializer {
        @Override
        public void handle(JsonRequest request, JsonResponse response) throws Exception {

        }

        @Override
        public boolean resolve(JsonRequest r, JsonResponse s) {
            return true;
        }

        @Override
        public void init() {

        }
    }
}
