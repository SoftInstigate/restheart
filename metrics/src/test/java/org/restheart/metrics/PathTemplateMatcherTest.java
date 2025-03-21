/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2023 - 2025 SoftInstigate
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

package org.restheart.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatcher;

public class PathTemplateMatcherTest {

    @Test
    public void testMetricParamWithCurleyBraces() {
        var pathTemplate = "/{serviceName}/{*}";
        var uri = "/metrics/{tenant}/ping";

        var params = getPathParams(pathTemplate, uri);

        assertEquals("{tenant}/ping", params.get("*"));
    }

    // this is equal to Request.getPathParams()
    private Map<String, String> getPathParams(String pathTemplate, String path) {
        var ptm = new PathTemplateMatcher<String>();

        try {
            ptm.add(PathTemplate.create(pathTemplate), "");
        } catch (Throwable t) {
            throw new IllegalArgumentException("wrong path template", t);
        }

        var match = ptm.match(path);

        return match != null ? ptm.match(path).getParameters() : new HashMap<>();
    }
}
