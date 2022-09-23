package org.restheart.providers;

import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "zapp2", description = "provides the plugin configuration")
public class TestWrongProvider2 implements Provider<String> {
    @Inject("zapp")
    private String urg;
    public String get(PluginRecord<?> caller) {
        return urg;
    }
}
