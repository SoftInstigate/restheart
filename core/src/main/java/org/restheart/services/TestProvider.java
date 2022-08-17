package org.restheart.services;

import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

//TODO remove this
@RegisterPlugin(name = "testProvider", description = "just a test dependency provider")
public class TestProvider implements Provider<String> {
    private static final String HOLDER = "ciao";

    public String get() {
        return HOLDER;
    }
}
