package org.restheart.accounts;

import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;

import java.util.Set;
import java.util.function.Predicate;
import org.restheart.exchange.Request;

/**
 * Registers ACL rules for all restheart-accounts public endpoints.
 * Runs BEFORE_STARTUP so rules are in place before the first request.
 */
@RegisterPlugin(
        name             = "accountsAclInitializer",
        description      = "Registers ACL rules for restheart-accounts public endpoints",
        initPoint        = InitPoint.BEFORE_STARTUP,
        enabledByDefault = true)
public class AccountsAclInitializer implements Initializer {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/auth/register",
            "/auth/verify",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/activate"
    );

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Override
    public void init() {
        Predicate<Request<?>> isPublic = r -> {
            var path = r.getPath();
            return PUBLIC_PATHS.contains(path)
                    || path.startsWith("/auth/oauth/authorize/")
                    || path.startsWith("/auth/oauth/callback/");
        };

        aclRegistry.registerAllow(isPublic);
        aclRegistry.registerAuthenticationRequirement(r -> !isPublic.test(r));
    }
}
