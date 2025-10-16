/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.restheart.utils.Pair;
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
import java.util.regex.Pattern;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 * factory for JWT AuthenticationMechanism
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "jwtAuthenticationMechanism", description = "handle JSON Web Token authentication", enabledByDefault = false)
public class JwtAuthenticationMechanism implements AuthMechanism, ConsumingPlugin<Pair<HttpServerExchange, DecodedJWT>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationMechanism.class);

    public static final String JWT_AUTH_HEADER_PREFIX = "Bearer ";
    private JWTVerifier jwtVerifier;
    private Consumer<Pair<HttpServerExchange, DecodedJWT>> extraJwtVerifier = null;

    private boolean base64Encoded;

    private String usernameClaim;
    private String rolesClaim;
    private List<String> fixedRoles;

    // Regular expression to check for minimum JWT key complexity:
    // - At least one lowercase letter
    // - At least one uppercase letter
    // - At least one digit
    // - At least one special character
    // - At least 32 characters long
    private static final Pattern COMPLEXITY_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{32,}$");

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() throws ConfigurationException {
        // get configuration arguments
        base64Encoded = arg(config, "base64Encoded");
        final String key = arg(config, "key");
        validateKeyComplexity(key);
        usernameClaim = arg(config, "usernameClaim");
        rolesClaim = argOrDefault(config, "rolesClaim", null);
        fixedRoles = argOrDefault(config, "fixedRoles", null);
        final String issuer = argOrDefault(config, "issuer", null);
        final var _audience = argOrDefault(config, "audience", null);

        final List<String> audience = new ArrayList<>();

        if (_audience == null) {
            // do nothing
        } else if (_audience instanceof final String _as) {
            audience.add(_as);
        } else if (_audience instanceof final List<?> _al) {
            _al.stream().filter(String.class::isInstance).map(e -> (String) e).forEach(audience::add);
        } else {
            throw new ConfigurationException("Wrong audience, must be a String or an Array of Strings");
        }

        Algorithm _algorithm;
        final String algorithm = arg(config, "algorithm");
        try {
            _algorithm = getAlgorithm(algorithm, key);
        } catch (final CertificateException ex) {
            throw new ConfigurationException("wrong JWT configuration, cannot setup algorithm", ex);
        }

        Verification v = JWT.require(_algorithm);

        if (audience != null && !audience.isEmpty()) {
            v = v.withAudience(audience.toArray(String[]::new));
        }

        if (issuer != null) {
            v = v.withIssuer(issuer);
        }

        if (rolesClaim == null && fixedRoles == null) {
            throw new ConfigurationException("wrong JWT configuration, need to set at least one of 'rolesClaim' or 'fixedRoles'");
        }

        this.jwtVerifier = v.build();
    }

    protected static boolean validateKeyComplexity(final String jwtKey) {
        if (jwtKey == null || jwtKey.length() < 32) {
            LOGGER.warn("The JWT key is too short. It should be at least 32 characters long.");
            return false;
        }

        if (!COMPLEXITY_PATTERN.matcher(jwtKey).matches()) {
            LOGGER.warn("The JWT key does not meet complexity requirements. Ensure it contains uppercase letters, lowercase letters, digits, and special characters.");
            return false;
        }
        LOGGER.info("The JWT key meets the minimum complexity requirements.");
        return true;
    }

    @Override
    public AuthenticationMechanism.AuthenticationMechanismOutcome authenticate(final HttpServerExchange hse, final SecurityContext sc) {
        try {
            var token = getToken(hse);

            if (token != null) {
                if (base64Encoded) {
                    token = StringUtils.newStringUtf8(Base64.getUrlDecoder().decode(token));
                }

                final var verifiedJwt = jwtVerifier.verify(token);

                final var subject = verifiedJwt.getClaim(usernameClaim).asString();

                if (subject == null) {
                    LOGGER.debug("username not specified with claim {}", usernameClaim);
                    sc.authenticationFailed("JwtAuthenticationManager", "username not specified");
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }

                final Set<String> actualRoles = new LinkedHashSet<>();

                if (rolesClaim != null) {
                    final Claim _roles = verifiedJwt.getClaim(rolesClaim);

                    if (_roles != null && !_roles.isNull()) {
                        try {
                            final String[] __roles = _roles.asArray(String.class);

                            if (__roles != null) {
                                actualRoles.addAll(Arrays.asList(__roles));
                            } else {
                                LOGGER.debug("roles is not an array: {}", _roles.asString());
                                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                            }
                        } catch (final JWTDecodeException ex) {
                            LOGGER.warn("Jwt cannot get roles from claim {}, extepected an array of strings: {}", rolesClaim, _roles.toString());
                        }
                    }
                }

                if (this.fixedRoles != null) {
                    actualRoles.addAll(this.fixedRoles);
                }

                if (this.extraJwtVerifier != null) {
                    this.extraJwtVerifier.accept(Pair.of(hse, verifiedJwt));
                }

                final var jwtPayload = new String(Base64.getUrlDecoder().decode(verifiedJwt.getPayload()), StandardCharsets.UTF_8);

                final var account = new JwtAccount(subject, actualRoles, jwtPayload);

                sc.authenticationComplete(account, "JwtAuthenticationManager", false);

                Request.of(hse).addXForwardedHeader("Jwt-Payload", jwtPayload);

                return AuthenticationMechanismOutcome.AUTHENTICATED;
            }

        } catch (final JWTVerificationException ex) {
            LOGGER.debug("│  ├─ Jwt not verified: {}", ex.getMessage());
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
    public void addConsumer(final Consumer<Pair<HttpServerExchange, DecodedJWT>> extraJwtVerifier) {
        this.extraJwtVerifier = extraJwtVerifier;
    }

    private String getToken(final HttpServerExchange hse) {
        final var _authHeader = hse.getRequestHeaders().get(HttpHeaders.AUTHORIZATION);

        if (_authHeader != null && !_authHeader.isEmpty()) {
            final String authHeader = _authHeader.getFirst();

            if (authHeader.startsWith(JWT_AUTH_HEADER_PREFIX)) {
                return authHeader.substring(7);
            }
        }

        return null;
    }

    private Algorithm getAlgorithm(final String name, final String key) throws CertificateException {
        if (name == null || key == null) {
            throw new IllegalArgumentException("algorithm and key are required.");
        } else if (name.startsWith("HMAC") || name.startsWith("HS")) {
            return getHMAC(name, key.getBytes(StandardCharsets.UTF_8));
        } else if (name.startsWith("RS")) {
            return getRSA(name, key);
        } else {
            throw new IllegalArgumentException("unknown algorithm " + name);
        }
    }

    private Algorithm getHMAC(final String name, final byte[] key) throws IllegalArgumentException {
        return switch (name) {
            case "HMAC256", "HS256" -> Algorithm.HMAC256(key);
            case "HMAC384", "HS384" -> Algorithm.HMAC384(key);
            case "HMAC512", "HS512" -> Algorithm.HMAC512(key);
            default -> throw new IllegalArgumentException("unknown HMAC algorithm " + name);
        };
    }

    private Algorithm getRSA(final String name, final String key)
            throws IllegalArgumentException, CertificateException {
        final var rsaKey = getRSAPublicKey(key);

        return switch (name) {
            case "RSA256", "RS256" -> Algorithm.RSA256(rsaKey, null);
            case "RSA384", "RS384" -> Algorithm.RSA384(rsaKey, null);
            case "RSA512", "RS512" -> Algorithm.RSA512(rsaKey, null);
            default -> throw new IllegalArgumentException("unknown HMAC algorithm " + name);
        };
    }

    private RSAPublicKey getRSAPublicKey(final String key) throws CertificateException {
        final var fact = CertificateFactory.getInstance("X.509");
        final var is = new ByteArrayInputStream(Base64.getDecoder().decode(key));
        final var cer = (X509Certificate) fact.generateCertificate(is);
        return (RSAPublicKey) cer.getPublicKey();
    }
}
