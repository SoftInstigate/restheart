package org.restheart.services;

import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

//TODO remove this
@RegisterPlugin(name = "bar", description = "just a test dependency provider")
public class TestProvider2 implements Provider<String> {

    @Override
    public String get() {
        return "bar";
    }
}
