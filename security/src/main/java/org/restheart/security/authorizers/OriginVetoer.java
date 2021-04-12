package org.restheart.security.authorizers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.restheart.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.plugins.ConfigurablePlugin;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import static org.restheart.utils.URLUtils.removeTrailingSlashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(
        name = "originVetoer",
        description = "protects from CSRF attacks by forbidding requests whose Origin header is not whitelisted",
        enabledByDefault = false,
        authorizerType = TYPE.VETOER)
public class OriginVetoer implements Authorizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OriginVetoer.class);

    private List<String> whitelist = null;

    @InjectConfiguration
    public void setConfiguration(Map<String, Object> args) {
        try {
            List<String> _whitelist = ConfigurablePlugin.argValue(args, "whitelist");
            this.whitelist = _whitelist.stream()
                .filter(item -> item != null)
                .map(item -> item.trim())
                .map(item -> item.toLowerCase())
                .map(item -> removeTrailingSlashes(item))
                .map(item -> item.concat("/"))
                .collect(Collectors.toList());

            LOGGER.info("whitelist defined for originVetoer, requests will be accepted with Origin header in {}", this.whitelist);
        } catch(ConfigurationException ce) {
            this.whitelist = null;
            LOGGER.info("No whitelist defined for originVetoer, all Origin headers are accepted");
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAllowed(Request request) {
        if (this.whitelist == null || this.whitelist.isEmpty()) {
            return true;
        } else {
            var origin = request.getHeader("Origin");

            if (origin == null) {
                LOGGER.warn("request forbidden by originVetoer due to missing Origin header, whitelist is {}", whitelist);
                return false;
            } else {
                var allowed = this.whitelist.stream().anyMatch(wl -> removeTrailingSlashes(origin.toLowerCase()).concat("/").startsWith(wl));

                if (!allowed) {
                    LOGGER.warn("request forbidden by originVetoer due to Origin header {} not in whitelist {}", origin, whitelist);
                }

                return allowed;
            }
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAuthenticationRequired(Request request) {
        return false;
    }
}
