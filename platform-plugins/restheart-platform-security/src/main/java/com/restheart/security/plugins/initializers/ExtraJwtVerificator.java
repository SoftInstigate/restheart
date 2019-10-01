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
package com.restheart.security.plugins.initializers;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.restheart.security.plugins.mechanisms.JwtAuthenticationMechanism;
import org.restheart.security.ConfigurationException;
import org.restheart.security.plugins.Initializer;
import org.restheart.security.plugins.PluginsRegistry;
import org.restheart.security.plugins.RegisterPlugin;

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

    @Override
    public void init() {
        JwtAuthenticationMechanism am;
        try {
            am = (JwtAuthenticationMechanism) PluginsRegistry
                    .getInstance()
                    .getAuthenticationMechanism("jwtAuthenticationMechanism");
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
