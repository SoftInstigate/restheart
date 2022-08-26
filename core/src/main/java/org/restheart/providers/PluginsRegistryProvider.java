package org.restheart.providers;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "registry", description = "provides the PluginsRegistry")
public class PluginsRegistryProvider implements Provider<PluginsRegistry> {
    public PluginsRegistry get(PluginRecord<?> caller) {
        return PluginsRegistryImpl.getInstance();
    }
}
