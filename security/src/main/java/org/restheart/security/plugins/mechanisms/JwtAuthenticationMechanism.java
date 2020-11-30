/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.plugins.mechanisms;

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
import io.undertow.security.api.AuthenticationMechanism.ChallengeResult;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.codec.binary.StringUtils;
import org.restheart.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.idm.JwtAccount;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.ConsumingPlugin;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * factory for JWT AuthenticationMechanism
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name="jwtAuthenticationMechanism",
        description = "handle JSON Web Token authentication")
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
    private String audience;

    @InjectConfiguration
    public void init(Map<String, Object> args) throws ConfigurationException {
        // get configuration arguments
        base64Encoded = argValue(args, "base64Encoded");
        algorithm = argValue(args, "algorithm");
        key = argValue(args, "key");
        usernameClaim = argValue(args, "usernameClaim");
        rolesClaim = argValue(args, "rolesClaim");
        fixedRoles = argValue(args, "fixedRoles");
        issuer = argValue(args, "issuer");
        audience = argValue(args, "audience");

        Algorithm _algorithm;

        try {
            _algorithm = getAlgorithm(algorithm, key);
        } catch (CertificateException | UnsupportedEncodingException ex) {
            throw new ConfigurationException("wrong JWT configuration, "
                    + "cannot setup algorithm", ex);
        }

        Verification v = JWT.require(_algorithm);

        if (audience != null) {
            v.withAudience(audience);
        }

        if (issuer != null) {
            v.withIssuer(issuer);
        }

        if (rolesClaim != null && fixedRoles != null) {
            throw new ConfigurationException("wrong JWT configuration, "
                    + "cannot set both 'rolesClaim' and 'fixedRoles'");
        }

        if (rolesClaim == null && fixedRoles == null) {
            throw new ConfigurationException("wrong JWT configuration, "
                    + "need to set either 'rolesClaim' or 'fixedRoles'");
        }

        this.jwtVerifier = v.build();
    }

    @Override
    public AuthenticationMechanism.AuthenticationMechanismOutcome
            authenticate(HttpServerExchange hse, SecurityContext sc) {
        try {
            String token = getToken(hse);

            if (token != null) {
                if (base64Encoded) {
                    token = StringUtils.newStringUtf8(
                            Base64.getUrlDecoder().decode(token));
                }

                DecodedJWT verifiedJwt = jwtVerifier.verify(token);

                String subject = verifiedJwt.getClaim(usernameClaim).asString();

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
                                LOGGER.debug("roles is not an array: {}", 
                                        _roles.asString());
                                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                            }
                        } catch (JWTDecodeException ex) {
                            LOGGER.warn("Jwt cannot get roles from claim {}, "
                                    + "extepected an array of strings: {}",
                                    rolesClaim,
                                    _roles.toString());
                        }
                    }
                } else if (this.fixedRoles != null) {
                    actualRoles.addAll(this.fixedRoles);
                }

                if (this.extraJwtVerifier != null) {
                    this.extraJwtVerifier.accept(verifiedJwt);
                }

                var jwtPayload = new String(Base64.getUrlDecoder()
                        .decode(verifiedJwt.getPayload()),
                        Charset.forName("UTF-8"));

                JwtAccount account = new JwtAccount(
                        subject,
                        actualRoles,
                        jwtPayload
                );

                sc.authenticationComplete(account,
                        "JwtAuthenticationManager", false);

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
    public ChallengeResult sendChallenge(final HttpServerExchange exchange,
            final SecurityContext securityContext) {
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
        HeaderValues _authHeader = hse.getRequestHeaders().get(HttpHeaders.AUTHORIZATION);

        if (_authHeader != null && !_authHeader.isEmpty()) {
            String authHeader = _authHeader.getFirst();

            if (authHeader.startsWith(JWT_AUTH_HEADER_PREFIX)) {
                return authHeader.substring(7);
            }
        }

        return null;
    }

    private Algorithm getAlgorithm(String name, String key)
            throws CertificateException, UnsupportedEncodingException {
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
