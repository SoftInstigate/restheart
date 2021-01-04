package org.restheart.polyglot;

import org.restheart.plugins.StringService;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;

public abstract class AbstractJavaScriptService implements StringService {
    protected String name;
    protected String description;
    protected String uri;
    protected boolean secured;
    protected MATCH_POLICY matchPolicy;

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

    @Override
    public abstract void handle(StringRequest request, StringResponse response);
}
