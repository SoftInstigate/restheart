package org.restheart.providers;

import java.util.Map;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "config", description = "provides the plugin configuration")
public class PluginConfigurationProvider implements Provider<Map<String, Object>> {
    public Map<String, Object> get(PluginRecord<?> caller) {
        return caller.getConfArgs();
    }
}
