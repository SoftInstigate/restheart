package org.restheart.providers;

import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "zapp", description = "provides the plugin configuration")
public class TestWrongProvider implements Provider<String> {
    @Inject("nonnooo")
    private String urg;
    public String get(PluginRecord<?> caller) {
        return "ciao";
    }
}
