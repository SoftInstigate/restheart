package org.restheart.providers;

import org.restheart.Bootstrapper;
import org.restheart.Configuration;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "rh-config", description = "provides the RESTHeart configuration")
public class RHConfigurationProvider implements Provider<Configuration> {
    public Configuration get(PluginRecord<?> caller) {
        return Bootstrapper.getConfiguration();
    }
}
