package org.restheart.polyglot;

import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;

public abstract class AbstractJavaScriptPlugin {
    protected String name;
    protected String description;
    protected String uri;
    protected boolean secured;
    protected MATCH_POLICY matchPolicy;
    protected InterceptPoint interceptPoint;

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSecured() {
        return secured;
    }

    public MATCH_POLICY getMatchPolicy() {
        return matchPolicy;
    }

    public InterceptPoint getInterceptPoint() {
        return interceptPoint;
    }
}
