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
