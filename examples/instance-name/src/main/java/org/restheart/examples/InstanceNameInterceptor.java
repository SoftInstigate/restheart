package org.restheart.examples;

import java.util.Map;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import io.undertow.util.HttpString;

import static org.restheart.plugins.ConfigurablePlugin.argValue;
import static org.restheart.ConfigurationKeys.INSTANCE_NAME_KEY;

@RegisterPlugin(name = "instanceNameInterceptor",
                description = "Add the X-Restheart-Instance response header",
                enabledByDefault = true)
public class InstanceNameInterceptor implements MongoInterceptor {
    public static final HttpString X_RESTHEART_INSTANCE_HEADER = HttpString.tryFromString("X-Restheart-Instance");

    private String instanceName;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void conf(Map<String, Object> args) {
        this.instanceName = args.containsKey(INSTANCE_NAME_KEY)
            ? argValue(args, INSTANCE_NAME_KEY)
            : "unknown";
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
