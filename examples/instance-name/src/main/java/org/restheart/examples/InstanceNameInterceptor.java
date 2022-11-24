package org.restheart.examples;

import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.Inject;
import org.restheart.Configuration;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import io.undertow.util.HttpString;

@RegisterPlugin(name = "instanceNameInterceptor",
                description = "Add the X-Restheart-Instance response header",
                enabledByDefault = true)
public class InstanceNameInterceptor implements MongoInterceptor {
    public static final HttpString X_RESTHEART_INSTANCE_HEADER = HttpString.tryFromString("X-Restheart-Instance");

    private String instanceName;

    @Inject("rh-config")
    private Configuration config;

    @OnInit
    public void init() {
        this.instanceName = config.instanceName();
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) {
        response.getHeaders().put(X_RESTHEART_INSTANCE_HEADER, this.instanceName);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return true;
    }
}
