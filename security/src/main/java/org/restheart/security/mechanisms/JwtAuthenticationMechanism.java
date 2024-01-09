/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.mechanisms;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.codec.binary.StringUtils;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.plugins.ConsumingPlugin;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.security.JwtAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import com.google.common.net.HttpHeaders;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 * factory for JWT AuthenticationMechanism
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name="jwtAuthenticationMechanism", description = "handle JSON Web Token authentication", enabledByDefault = false)
public class JwtAuthenticationMechanism implements AuthMechanism, ConsumingPlugin<DecodedJWT> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationMechanism.class);

    public static final String JWT_AUTH_HEADER_PREFIX = "Bearer ";
    private JWTVerifier jwtVerifier;
    private Consumer<DecodedJWT> extraJwtVerifier = null;

    private boolean base64Encoded;
    private String algorithm;
    private String key;
    private String usernameClaim;
    private String rolesClaim;
    private List<String> fixedRoles;
    private String issuer;
    private List<String> audience;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() throws ConfigurationException {
        // get configuration arguments
        base64Encoded = arg(config, "base64Encoded");
        algorithm = arg(config, "algorithm");
        key = arg(config, "key");
        if ("secret".equals(key)) {
            LOGGER.warn("You should really update the JWT key!");
        }
        usernameClaim = arg(config, "usernameClaim");
        rolesClaim = argOrDefault(config, "rolesClaim", null);
        fixedRoles = argOrDefault(config, "fixedRoles", null);
        issuer = argOrDefault(config, "issuer", null);
        var _audience = argOrDefault(config, "audience", null);

        audience = new ArrayList<String>();

        if (_audience == null) {
            this.audience = null;
        } else if (_audience instanceof String _as) {
            audience = new ArrayList<String>();
            this.audience.add(_as);
        } else if (_audience instanceof List<?> _al) {
            audience = new ArrayList<String>();
            _al.stream().filter(e -> e instanceof String).map(e -> (String)e).forEach(e -> this.audience.add(e));
        } else {
            throw new ConfigurationException("Wrong audience, must be a String or an Array of Strings");
        }

        Algorithm _algorithm;

        try {
            _algorithm = getAlgorithm(algorithm, key);
        } catch (CertificateException | UnsupportedEncodingException ex) {
            throw new ConfigurationException("wrong JWT configuration, cannot setup algorithm", ex);
        }

        Verification v = JWT.require(_algorithm);

        if (audience != null && !audience.isEmpty()) {
            v = v.withAudience(audience.toArray(String[]::new));
        }

        if (issuer != null) {
            v = v.withIssuer(issuer);
        }

        if (rolesClaim != null && fixedRoles != null) {
            throw new ConfigurationException("wrong JWT configuration, cannot set both 'rolesClaim' and 'fixedRoles'");
        }

        if (rolesClaim == null && (fixedRoles == null || fixedRoles.isEmpty())) {
            throw new ConfigurationException("wrong JWT configuration, need to set either 'rolesClaim' or 'fixedRoles'");
        }

        this.jwtVerifier = v.build();
    }

    @Override
    public AuthenticationMechanism.AuthenticationMechanismOutcome authenticate(HttpServerExchange hse, SecurityContext sc) {
        try {
            var token = getToken(hse);

            if (token != null) {
                if (base64Encoded) {
                    token = StringUtils.newStringUtf8(Base64.getUrlDecoder().decode(token));
                }

                var verifiedJwt = jwtVerifier.verify(token);

                var subject = verifiedJwt.getClaim(usernameClaim).asString();

                if (subject == null) {
                    LOGGER.debug("username not specified with claim {}", usernameClaim);
                    sc.authenticationFailed("JwtAuthenticationManager", "username not specified");
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }

                Set<String> actualRoles = new LinkedHashSet<>();

                if (rolesClaim != null) {
                    Claim _roles = verifiedJwt.getClaim(rolesClaim);

                    if (_roles != null && !_roles.isNull()) {
                        try {
                            String[] __roles = _roles.asArray(String.class);

                            if (__roles != null) {
                                for (String role : __roles) {
                                    actualRoles.add(role);
                                }
                            } else {
                                LOGGER.debug("roles is not an array: {}", _roles.asString());
                                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                            }
                        } catch (JWTDecodeException ex) {
                            LOGGER.warn("Jwt cannot get roles from claim {}, extepected an array of strings: {}", rolesClaim, _roles.toString());
                        }
                    }
                } else if (this.fixedRoles != null) {
                    actualRoles.addAll(this.fixedRoles);
                }

                if (this.extraJwtVerifier != null) {
                    this.extraJwtVerifier.accept(verifiedJwt);
                }

                var jwtPayload = new String(Base64.getUrlDecoder().decode(verifiedJwt.getPayload()), Charset.forName("UTF-8"));

                var account = new JwtAccount(subject, actualRoles, jwtPayload);

                sc.authenticationComplete(account, "JwtAuthenticationManager", false);

                Request.of(hse).addXForwardedHeader("Jwt-Payload", jwtPayload);

                return AuthenticationMechanismOutcome.AUTHENTICATED;
            }

        } catch (JWTVerificationException ex) {
            LOGGER.debug("Jwt not verified: {}", ex.getMessage());
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        return new AuthenticationMechanism.ChallengeResult(true, 200);
    }

    /**
     * set an extra verification step via a Consumer that can throw
     * JWTVerificationException to make the verification failing
     *
     * @param extraJwtVerifier
     */
    @Override
    public void addConsumer(Consumer<DecodedJWT> extraJwtVerifier) {
        this.extraJwtVerifier = extraJwtVerifier;
    }

    private String getToken(HttpServerExchange hse) {
        var _authHeader = hse.getRequestHeaders().get(HttpHeaders.AUTHORIZATION);

        if (_authHeader != null && !_authHeader.isEmpty()) {
            String authHeader = _authHeader.getFirst();

            if (authHeader.startsWith(JWT_AUTH_HEADER_PREFIX)) {
                return authHeader.substring(7);
            }
        }

        return null;
    }

    private Algorithm getAlgorithm(String name, String key) throws CertificateException, UnsupportedEncodingException {
        if (name == null || key == null) {
            throw new IllegalArgumentException("algorithm and key are required.");
        } else if (name.startsWith("HMAC") || name.startsWith("HS")) {
            return getHMAC(name, key.getBytes("UTF-8"));
        } else if (name.startsWith("RS")) {
            return getRSA(name, key);
        } else {
            throw new IllegalArgumentException("unknown algorithm " + name);
        }
    }

    private Algorithm getHMAC(String name, byte[] key) throws IllegalArgumentException {
        return switch(name) {
            case "HMAC256", "HS256" -> Algorithm.HMAC256(key);
            case "HMAC384", "HS384" -> Algorithm.HMAC384(key);
            case "HMAC512", "HS512" -> Algorithm.HMAC512(key);
            default -> throw new IllegalArgumentException("unknown HMAC algorithm " + name);
        };
    }

    private Algorithm getRSA(String name, String key) throws IllegalArgumentException, CertificateException {
        var rsaKey = getRSAPublicKey(key);

        return switch(name) {
            case "RSA256", "RS256" -> Algorithm.RSA256(rsaKey, null);
            case "RSA384", "RS384" -> Algorithm.RSA384(rsaKey, null);
            case "RSA512", "RS512" -> Algorithm.RSA512(rsaKey, null);
            default -> throw new IllegalArgumentException("unknown HMAC algorithm " + name);
        };
    }

    private RSAPublicKey getRSAPublicKey(String key) throws CertificateException {
        var fact = CertificateFactory.getInstance("X.509");
        var is = new ByteArrayInputStream(Base64.getDecoder().decode(key));
        var cer = (X509Certificate) fact.generateCertificate(is);
        return (RSAPublicKey) cer.getPublicKey();
    }
}
