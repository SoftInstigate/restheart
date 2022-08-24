package org.restheart.services;

import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

//TODO remove this
@RegisterPlugin(name = "foo", description = "just a test dependency provider")
public class TestProvider implements Provider<String> {
    public String get() {
        return "foo";
    }
}
