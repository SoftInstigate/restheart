/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.security.impl;

import com.auth0.jwt.JWT;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.restheart.security.AuthenticationMechanismFactory;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import com.google.common.net.HttpHeaders;
import io.undertow.util.HeaderValues;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.codec.binary.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * factory for JWT AuthenticationMechanism
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JwtAuthenticationManagerFactory implements AuthenticationMechanismFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationManagerFactory.class);

    private JWTVerifier verifier;
    
    public static String JWT_AUTH_HEADER_PREFIX = "Bearer ";

    @Override
    public AuthenticationMechanism build(Map<String, Object> args, IdentityManager idm) {
        // get configuration arguments

        final boolean base64Encoded = (boolean) args.getOrDefault("base64Encoded", false);
        final String algorithm = (String) args.get("algorithm");
        final String key = (String) args.get("key");
        final String usernameClaim = (String) args.getOrDefault("usernameClaim", "sub");
        final String rolesClaim = (String) args.get("rolesClaim");
        final String issuer = (String) args.get("issuer");
        final String audience = (String) args.get("audience");

        Algorithm _algorithm;

        try {
            _algorithm = getAlgorithm(algorithm, key);
        } catch (CertificateException ex) {
            throw new IllegalArgumentException("wrong JWT configuration, cannot setup algorithm", ex);
        }

        Verification v = JWT.require(_algorithm);

        if (audience != null) {
            v.withAudience(audience);
        }

        if (issuer != null) {
            v.withIssuer(issuer);
        }

        this.verifier = v.build();

        return new AuthenticationMechanism() {
            @Override
            public AuthenticationMechanism.AuthenticationMechanismOutcome
                    authenticate(HttpServerExchange hse, SecurityContext sc) {
                try {
                    String token = getToken(hse);

                    if (token != null) {
                        if (base64Encoded) {
                            token = StringUtils.newStringUtf8(
                                    Base64.getDecoder().decode(token));
                        }

                        DecodedJWT verifiedJwt = verifier.verify(token);

                        String subject = verifiedJwt.getClaim(usernameClaim).asString();

                        if (subject == null) {
                            LOGGER.debug("username not specified with claim {}", usernameClaim);
                            sc.authenticationFailed("JwtAuthenticationManager", "username not specified");
                            return AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                        }

                        Claim _roles = verifiedJwt.getClaim(rolesClaim);

                        Set<String> roles = new LinkedHashSet<>();

                        boolean rolesInitialized = false;

                        if (_roles != null && !_roles.isNull()) {
                            try {
                                String[] __roles = _roles.asArray(String.class);

                                if (__roles != null) {
                                    rolesInitialized = true;
                                    for (int idx = 0; idx < __roles.length; idx++) {
                                        roles.add(__roles[idx]);
                                    }
                                }
                            } catch (JWTDecodeException ex) {
                                LOGGER.warn("Jwt cannot get roles from claim {}, "
                                        + "extepected an array of strings: {}", 
                                        rolesClaim, 
                                        _roles.toString());
                            }

                            if (!rolesInitialized && _roles.asString() != null) {
                                roles.add(_roles.asString());
                            }
                        }

                        SimpleAccount account = new SimpleAccount(
                                subject,
                                new char[0],
                                roles);

                        if (idm != null) {
                            idm.verify(account);
                        }

                        sc.authenticationComplete(account,
                                "JwtAuthenticationManager", false);
                        return AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED;
                    }

                } catch (JWTVerificationException ex) {
                    LOGGER.debug("Jwt not verified", ex);
                    return AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }

                // by returning NOT_ATTEMPTED, in case the provided credentials 
                // don't match any user of the IdentityManager, the authentication 
                // will fallback to the default authentication manager (BasicAuthenticationManager)
                // to make it failing, return NOT_AUTHENTICATED
                return AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_ATTEMPTED;
            }

            @Override
            public AuthenticationMechanism.ChallengeResult sendChallenge(HttpServerExchange hse, SecurityContext sc
            ) {
                return new AuthenticationMechanism.ChallengeResult(true, 200);
            }

            private String getToken(HttpServerExchange hse) {
                HeaderValues _authHeader = hse.getRequestHeaders().get(HttpHeaders.AUTHORIZATION);

                if (_authHeader != null && !_authHeader.isEmpty()) {
                    String authHeader = _authHeader.getFirst();

                    if (authHeader.startsWith(JWT_AUTH_HEADER_PREFIX)) {
                        return authHeader.substring(7);
                    }
                }

                return null;
            }
        };
    }

    private Algorithm getAlgorithm(String name, String key)
            throws CertificateException {
        if (name == null || key == null) {
            throw new IllegalArgumentException("algorithm and key are required.");
        } else if (name.startsWith("HMAC") || name.startsWith("HS")) {
            return getHMAC(name, key.getBytes());
        } else if (name.startsWith("RS")) {
            return getRSA(name, key);
        } else {
            throw new IllegalArgumentException("unknown algorithm " + name);
        }
    }

    private Algorithm getHMAC(String name, byte[] key)
            throws IllegalArgumentException {
        if ("HMAC256".equals(name) || "HS256".equals(name)) {
            return Algorithm.HMAC256(key);
        } else if ("HMAC384".equals(name) || "HS384".equals(name)) {
            return Algorithm.HMAC384(key);
        } else if ("HMAC512".equals(name) || "HS512".equals(name)) {
            return Algorithm.HMAC512(key);
        } else {
            throw new IllegalArgumentException("unknown HMAC algorithm " + name);
        }
    }

    private Algorithm getRSA(String name, String key)
            throws IllegalArgumentException, CertificateException {
        RSAPublicKey rsaKey = getRSAPublicKey(key);

        if ("RSA256".equals(name) || "RS256".equals(name)) {
            return Algorithm.RSA256(rsaKey, null);
        } else if ("RSA384".equals(name) || "RS384".equals(name)) {
            return Algorithm.RSA384(rsaKey, null);
        } else if ("RSA512".equals(name) || "RS512".equals(name)) {
            return Algorithm.RSA512(rsaKey, null);
        } else {
            throw new IllegalArgumentException("unknown HMAC algorithm " + name);
        }
    }

    private RSAPublicKey getRSAPublicKey(String key)
            throws CertificateException {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(key));
        X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
        return (RSAPublicKey) cer.getPublicKey();
    }
}
