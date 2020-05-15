/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.restheart.platform.jwt;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import org.restheart.ConfigurationException;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;

/**
 * Demonstrate how to add an extra verification step to the
 * jwtAuthenticationMechanism.
 * 
 * It is not enabledByDefault; to enable it add to
 * configuration file:<br>
 * <pre>
 * plugins-args:
 *     extraJwtVerificator:
 *         enabled: true
 * </pre>
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "extraJwtVerificator", 
        priority = 100, 
        description = "Demonstrate how to add an extra verifictation step "
                + "to the jwtAuthenticationMechanism",
        enabledByDefault = false)
public class ExtraJwtVerificator implements Initializer {
    private PluginsRegistry registry;
    
    @InjectPluginsRegistry
    public void init(PluginsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void init() {
        JwtAuthenticationMechanism am;
        try {
            var pr = registry
                    .getAuthMechanisms()
                    .stream()
                    .filter(_am -> "jwtAuthenticationMechanism".equals(_am.getName()))
                    .findFirst();
            
            if (pr.isPresent()) {
                am = (JwtAuthenticationMechanism) pr.get().getInstance();
            } else {
                throw new IllegalStateException("cannot get jwtAuthenticationMechanism");
            }
        } catch (ConfigurationException | ClassCastException ex) {
            throw new IllegalStateException("cannot get jwtAuthenticationMechanism", ex);
        }

        am.setExtraJwtVerifier(token -> {
            Claim extraClaim = token.getClaim("extra");

            if (extraClaim == null || extraClaim.isNull()) {
                throw new JWTVerificationException("missing extra claim");
            }

            var extra = extraClaim.asMap();

            if (!extra.containsKey("a")) {
                throw new JWTVerificationException("extra claim does not have "
                        + "'a' property");
            }

            if (!extra.containsKey("b")) {
                throw new JWTVerificationException("extra claim does not have "
                        + "'b' property");
            }
        });
    }

}
