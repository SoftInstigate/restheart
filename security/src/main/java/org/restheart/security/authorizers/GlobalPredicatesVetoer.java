package org.restheart.security.authorizers;

import org.restheart.exchange.Request;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;

@RegisterPlugin(
        name = "globalPredicatesVetoer",
        description = "vetoes requests according to global predicates",
        enabledByDefault = true,
        authorizerType = TYPE.VETOER)
public class GlobalPredicatesVetoer implements Authorizer {
    private PluginsRegistry registry;

    @InjectPluginsRegistry
    public void setPluginsRegistry(PluginsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean isAllowed(Request<?> request) {
        return registry.getGlobalSecurityPredicates()
                .stream()
                .allMatch(predicate -> predicate.resolve(request.getExchange()));
    }

    @Override
    public boolean isAuthenticationRequired(Request<?> request) {
        return false;
    }
}
