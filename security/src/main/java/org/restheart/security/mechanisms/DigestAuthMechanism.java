/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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

import static io.undertow.UndertowMessages.MESSAGES;
import io.undertow.security.api.NonceManager;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.DigestAlgorithm;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.DigestAuthorizationToken;
import static io.undertow.security.impl.DigestAuthorizationToken.parseHeader;
import io.undertow.security.impl.DigestQop;
import io.undertow.security.impl.SimpleNonceManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import static io.undertow.util.Headers.AUTHENTICATION_INFO;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.DIGEST;
import static io.undertow.util.Headers.NEXT_NONCE;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import io.undertow.util.HexConverter;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.restheart.ConfigurationException;
import org.restheart.handlers.QueryStringRebuilder;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link io.undertow.server.HttpHandler} to handle HTTP Digest authentication,
 * both according to RFC-2617 and draft update to allow additional algorithms to
 * be used.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RegisterPlugin(
        name = "digestAuthMechanism",
        description = "handles the digest authentication scheme",
        enabledByDefault = false)
public class DigestAuthMechanism implements AuthMechanism {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(DigestAuthMechanism.class);

    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    public DigestAuthMechanism() throws ConfigurationException {
        this("RESTHeart Realm",
                "localhost",
                "digestAuthMechanism",
                null);
    }

    @InjectConfiguration
    @InjectPluginsRegistry
    public void init(final Map<String, Object> args,
            PluginsRegistry pluginsRegistry)
            throws ConfigurationException {
        this.realmName = arg(args, "realm");
        this.domain = arg(args, "domain");

        // the authenticator specified in auth mechanism configuration
        this.identityManager = pluginsRegistry
                .getAuthenticator(arg(args, "authenticator"))
                .getInstance();
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange,
            SecurityContext securityContext) {
        if (exchange.getRequestHeaders().contains(SILENT_HEADER_KEY)
                || exchange.getQueryParameters()
                        .containsKey(SILENT_QUERY_PARAM_KEY)) {
            return new ChallengeResult(true, UNAUTHORIZED);
        } else {
            return _sendChallenge(exchange, securityContext);
        }
    }

    private static final String DIGEST_PREFIX = DIGEST + " ";
    private static final int PREFIX_LENGTH = DIGEST_PREFIX.length();
    private static final String OPAQUE_VALUE = "00000000000000000000000000000000";
    private static final byte COLON = ':';

    // private final String mechanismName;
    private IdentityManager identityManager;

    private static final Set<DigestAuthorizationToken> MANDATORY_REQUEST_TOKENS;

    static {
        Set<DigestAuthorizationToken> mandatoryTokens = EnumSet.noneOf(DigestAuthorizationToken.class);
        mandatoryTokens.add(DigestAuthorizationToken.USERNAME);
        mandatoryTokens.add(DigestAuthorizationToken.REALM);
        mandatoryTokens.add(DigestAuthorizationToken.NONCE);
        mandatoryTokens.add(DigestAuthorizationToken.DIGEST_URI);
        mandatoryTokens.add(DigestAuthorizationToken.RESPONSE);

        MANDATORY_REQUEST_TOKENS = Collections.unmodifiableSet(mandatoryTokens);
    }

    /**
     * The {@link List} of supported algorithms, this is assumed to be in
     * priority order.
     */
    private final List<DigestAlgorithm> supportedAlgorithms;
    private final List<DigestQop> supportedQops;
    private final String qopString;
    private String realmName;
    private String domain;
    private final NonceManager nonceManager;

    // Where do session keys fit? Do we just hang onto a session key or keep visiting the user store to check if the password
    // has changed?
    // Maybe even support registration of a session so it can be invalidated?
    // 2013-05-29 - Session keys will be cached, where a cached key is used the IdentityManager is still given the
    //              opportunity to check the Account is still valid.
    public DigestAuthMechanism(
            final String mechanismName,
            final List<DigestAlgorithm> supportedAlgorithms, final List<DigestQop> supportedQops,
            final String realmName, final String domain, final NonceManager nonceManager) {
        this(supportedAlgorithms, supportedQops, realmName, domain, nonceManager, mechanismName);
    }

    public DigestAuthMechanism(final List<DigestAlgorithm> supportedAlgorithms, final List<DigestQop> supportedQops,
            final String realmName, final String domain, final NonceManager nonceManager, final String mechanismName) {
        this(supportedAlgorithms, supportedQops, realmName, domain, nonceManager, mechanismName, null);
    }

    public DigestAuthMechanism(final List<DigestAlgorithm> supportedAlgorithms, final List<DigestQop> supportedQops,
            final String realmName, final String domain, final NonceManager nonceManager, final String mechanismName, final IdentityManager identityManager) {
        this.supportedAlgorithms = supportedAlgorithms;
        this.supportedQops = supportedQops;
        this.realmName = realmName;
        this.domain = domain;
        this.nonceManager = nonceManager;
        // this.mechanismName = mechanismName;
        this.identityManager = identityManager;

        if (!supportedQops.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            Iterator<DigestQop> it = supportedQops.iterator();
            sb.append(it.next().getToken());
            while (it.hasNext()) {
                sb.append(",").append(it.next().getToken());
            }
            qopString = sb.toString();
        } else {
            qopString = null;
        }
    }

    public DigestAuthMechanism(final String realmName, final String domain, final String mechanismName) {
        this(realmName, domain, mechanismName, null);
    }

    public DigestAuthMechanism(final String realmName, final String domain, final String mechanismName, final IdentityManager identityManager) {
        this(Collections.singletonList(DigestAlgorithm.MD5), Collections.singletonList(DigestQop.AUTH), realmName, domain, new SimpleNonceManager(), mechanismName, identityManager);
    }

    @SuppressWarnings("deprecation")
    private IdentityManager getIdentityManager(SecurityContext securityContext) {
        return identityManager != null ? identityManager : securityContext.getIdentityManager();
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange,
            final SecurityContext securityContext) {
        List<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.startsWith(DIGEST_PREFIX)) {
                    String digestChallenge = current.substring(PREFIX_LENGTH);

                    try {
                        DigestContext context = new DigestContext();
                        Map<DigestAuthorizationToken, String> parsedHeader = parseHeader(digestChallenge);
                        context.setMethod(exchange.getRequestMethod().toString());
                        context.setParsedHeader(parsedHeader);
                        // Some form of Digest authentication is going to occur so get the DigestContext set on the exchange.
                        exchange.putAttachment(DigestContext.ATTACHMENT_KEY, context);

                        LOGGER.trace("Found digest header {} in {}", current, exchange);

                        return handleDigestHeader(exchange, securityContext);
                    } catch (Exception e) {
                        LOGGER.debug("Error", e);
                    }
                }

                // By this point we had a header we should have been able to verify but for some reason
                // it was not correctly structured.
                return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
            }
        }

        // No suitable header has been found in this request,
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    private AuthenticationMechanismOutcome handleDigestHeader(HttpServerExchange exchange, final SecurityContext securityContext) {
        DigestContext context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
        Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();
        // Step 1 - Verify the set of tokens received to ensure valid values.
        Set<DigestAuthorizationToken> mandatoryTokens = EnumSet.copyOf(MANDATORY_REQUEST_TOKENS);
        if (!supportedAlgorithms.contains(DigestAlgorithm.MD5)) {
            // If we don't support MD5 then the client must choose an algorithm as we can not fall back to MD5.
            mandatoryTokens.add(DigestAuthorizationToken.ALGORITHM);
        }
        if (!supportedQops.isEmpty() && !supportedQops.contains(DigestQop.AUTH)) {
            // If we do not support auth then we are mandating auth-int so force the client to send a QOP
            mandatoryTokens.add(DigestAuthorizationToken.MESSAGE_QOP);
        }

        DigestQop qop;

        // This check is early as is increases the list of mandatory tokens.
        if (parsedHeader.containsKey(DigestAuthorizationToken.MESSAGE_QOP)) {
            qop = DigestQop.forName(parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP));
            if (qop == null || !supportedQops.contains(qop)) {
                // We are also ensuring the client is not trying to force a qop that has been disabled.
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
            context.setQop(qop);
            mandatoryTokens.add(DigestAuthorizationToken.CNONCE);
            mandatoryTokens.add(DigestAuthorizationToken.NONCE_COUNT);
        }

        // Check all mandatory tokens are present.
        mandatoryTokens.removeAll(parsedHeader.keySet());
        if (mandatoryTokens.size() > 0) {
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        // Perform some validation of the remaining tokens.
        if (!realmName.equals(parsedHeader.get(DigestAuthorizationToken.REALM))) {
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        if (parsedHeader.containsKey(DigestAuthorizationToken.DIGEST_URI)) {
            String uri = parsedHeader.get(DigestAuthorizationToken.DIGEST_URI);
            String requestURI = exchange.getRequestURI();

            // since the query string is rebuilt by QueryStringRebuilder
            // we need to check against the original query string
            String originalQueryString = QueryStringRebuilder
                    .getOriginalQueryString(exchange);

            if (!originalQueryString.isEmpty()) {
                requestURI = requestURI + "?" + originalQueryString;
            }
            if (!uri.equals(requestURI)) {
                //it is possible we were given an absolute URI
                //we reconstruct the URI from the host header to make sure they match up
                //I am not sure if this is overly strict, however I think it is better
                //to be safe than sorry
                requestURI = exchange.getRequestURL();
                if (!exchange.getQueryString().isEmpty()) {
                    requestURI = requestURI + "?" + exchange.getQueryString();
                }
                if (!uri.equals(requestURI)) {
                    //just end the auth process
                    LOGGER.warn("The URI in digest token does not match the request URI");
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }
            }
        } else {
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        if (parsedHeader.containsKey(DigestAuthorizationToken.OPAQUE)) {
            if (!OPAQUE_VALUE.equals(parsedHeader.get(DigestAuthorizationToken.OPAQUE))) {
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
        }

        DigestAlgorithm algorithm;
        if (parsedHeader.containsKey(DigestAuthorizationToken.ALGORITHM)) {
            algorithm = DigestAlgorithm.forName(parsedHeader.get(DigestAuthorizationToken.ALGORITHM));
            if (algorithm == null || !supportedAlgorithms.contains(algorithm)) {
                // We are also ensuring the client is not trying to force an algorithm that has been disabled.
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
        } else {
            // We know this is safe as the algorithm token was made mandatory
            // if MD5 is not supported.
            algorithm = DigestAlgorithm.MD5;
        }

        try {
            context.setAlgorithm(algorithm);
        } catch (NoSuchAlgorithmException e) {
            /*
             * This should not be possible in a properly configured installation.
             */
            LOGGER.error("Error", e);
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        final String userName = parsedHeader.get(DigestAuthorizationToken.USERNAME);
        final IdentityManager lidentityManager = getIdentityManager(securityContext);
        final Account account;

        if (algorithm.isSession()) {
            /* This can follow one of the following: -
             *   1 - New session so use DigestCredentialImpl with the IdentityManager to
             *       create a new session key.
             *   2 - Obtain the existing session key from the session store and validate it, just use
             *       IdentityManager to validate account is still active and the current role assignment.
             */
            throw new IllegalStateException("Not yet implemented.");
        } else {
            final DigestCredential credential = new DigestCredentialImpl(context);
            account = lidentityManager.verify(userName, credential);
        }

        if (account == null) {
            // Authentication has failed, this could either be caused by the user not-existing or it
            // could be caused due to an invalid hash.
            securityContext.authenticationFailed(MESSAGES.authenticationFailed(userName), getMechanismName());
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        // Step 3 - Verify that the nonce was eligible to be used.
        if (!validateNonceUse(context, parsedHeader, exchange)) {
            // This is the right place to make use of the decision but the check needs to be much much sooner
            // otherwise a failure server
            // side could leave a packet that could be 're-played' after the failed auth.
            // The username and password verification passed but for some reason we do not like the nonce.
            context.markStale();
            // We do not mark as a failure on the security context as this is not quite a failure, a client with a cached nonce
            // can easily hit this point.
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        // We have authenticated the remote user.
        sendAuthenticationInfoHeader(exchange);
        securityContext.authenticationComplete(account, getMechanismName(), false);
        return AuthenticationMechanismOutcome.AUTHENTICATED;

        // Step 4 - Set up any QOP related requirements.
    }

    private boolean validateRequest(final DigestContext context, final byte[] ha1) {
        byte[] ha2;
        DigestQop qop = context.getQop();
        // Step 2.2 Calculate H(A2)
        if (qop == null || qop.equals(DigestQop.AUTH)) {
            ha2 = createHA2Auth(context, context.getParsedHeader());
        } else {
            ha2 = createHA2AuthInt();
        }

        byte[] requestDigest;
        if (qop == null) {
            requestDigest = createRFC2069RequestDigest(ha1, ha2, context);
        } else {
            requestDigest = createRFC2617RequestDigest(ha1, ha2, context);
        }

        byte[] providedResponse = context.getParsedHeader().get(DigestAuthorizationToken.RESPONSE).getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(requestDigest, providedResponse);
    }

    private boolean validateNonceUse(DigestContext context, Map<DigestAuthorizationToken, String> parsedHeader, final HttpServerExchange exchange) {
        String suppliedNonce = parsedHeader.get(DigestAuthorizationToken.NONCE);
        int nonceCount = -1;
        if (parsedHeader.containsKey(DigestAuthorizationToken.NONCE_COUNT)) {
            String nonceCountHex = parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT);

            nonceCount = Integer.parseInt(nonceCountHex, 16);
        }

        context.setNonce(suppliedNonce);
        // A replay attempt will need an exception.
        return (nonceManager.validateNonce(suppliedNonce, nonceCount, exchange));
    }

    private byte[] createHA2Auth(final DigestContext context, Map<DigestAuthorizationToken, String> parsedHeader) {
        byte[] method = context.getMethod().getBytes(StandardCharsets.UTF_8);
        byte[] digestUri = parsedHeader.get(DigestAuthorizationToken.DIGEST_URI).getBytes(StandardCharsets.UTF_8);

        MessageDigest digest = context.getDigest();
        try {
            digest.update(method);
            digest.update(COLON);
            digest.update(digestUri);

            return HexConverter.convertToHexBytes(digest.digest());
        } finally {
            digest.reset();
        }
    }

    private byte[] createHA2AuthInt() {
        throw new IllegalStateException("Method not implemented.");
    }

    private byte[] createRFC2069RequestDigest(final byte[] ha1, final byte[] ha2, final DigestContext context) {
        final MessageDigest digest = context.getDigest();
        final Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();

        byte[] nonce = parsedHeader.get(DigestAuthorizationToken.NONCE).getBytes(StandardCharsets.UTF_8);

        try {
            digest.update(ha1);
            digest.update(COLON);
            digest.update(nonce);
            digest.update(COLON);
            digest.update(ha2);

            return HexConverter.convertToHexBytes(digest.digest());
        } finally {
            digest.reset();
        }
    }

    private byte[] createRFC2617RequestDigest(final byte[] ha1, final byte[] ha2, final DigestContext context) {
        final MessageDigest digest = context.getDigest();
        final Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();

        byte[] nonce = parsedHeader.get(DigestAuthorizationToken.NONCE).getBytes(StandardCharsets.UTF_8);
        byte[] nonceCount = parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT).getBytes(StandardCharsets.UTF_8);
        byte[] cnonce = parsedHeader.get(DigestAuthorizationToken.CNONCE).getBytes(StandardCharsets.UTF_8);
        byte[] qop = parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP).getBytes(StandardCharsets.UTF_8);

        try {
            digest.update(ha1);
            digest.update(COLON);
            digest.update(nonce);
            digest.update(COLON);
            digest.update(nonceCount);
            digest.update(COLON);
            digest.update(cnonce);
            digest.update(COLON);
            digest.update(qop);
            digest.update(COLON);
            digest.update(ha2);

            return HexConverter.convertToHexBytes(digest.digest());
        } finally {
            digest.reset();
        }
    }

    private ChallengeResult _sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        DigestContext context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
        boolean stale = context == null ? false : context.isStale();

        StringBuilder rb = new StringBuilder(DIGEST_PREFIX);
        rb.append(Headers.REALM.toString()).append("=\"").append(realmName).append("\",");
        rb.append(Headers.DOMAIN.toString()).append("=\"").append(domain).append("\",");
        // based on security constraints.
        rb.append(Headers.NONCE.toString()).append("=\"").append(nonceManager.nextNonce(null, exchange)).append("\",");
        // Not currently using OPAQUE as it offers no integrity, used for session data leaves it vulnerable to
        // session fixation type issues as well.
        rb.append(Headers.OPAQUE.toString()).append("=\"00000000000000000000000000000000\"");
        if (stale) {
            rb.append(",stale=true");
        }
        if (supportedAlgorithms.size() > 0) {
            // This header will need to be repeated once for each algorithm.
            rb.append(",").append(Headers.ALGORITHM.toString()).append("=%s");
        }
        if (qopString != null) {
            rb.append(",").append(Headers.QOP.toString()).append("=\"").append(qopString).append("\"");
        }

        String theChallenge = rb.toString();
        HeaderMap responseHeader = exchange.getResponseHeaders();
        if (supportedAlgorithms.isEmpty()) {
            responseHeader.add(WWW_AUTHENTICATE, theChallenge);
        } else {
            supportedAlgorithms.forEach((current) -> {
                responseHeader.add(WWW_AUTHENTICATE, String.format(theChallenge, current.getToken()));
            });
        }

        return new ChallengeResult(true, UNAUTHORIZED);
    }

    public void sendAuthenticationInfoHeader(final HttpServerExchange exchange) {
        DigestContext context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
        DigestQop qop = context.getQop();
        String currentNonce = context.getNonce();
        String nextNonce = nonceManager.nextNonce(currentNonce, exchange);
        if (qop != null || !nextNonce.equals(currentNonce)) {
            StringBuilder sb = new StringBuilder();
            sb.append(NEXT_NONCE).append("=\"").append(nextNonce).append("\"");
            if (qop != null) {
                Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();
                sb.append(",").append(Headers.QOP.toString()).append("=\"").append(qop.getToken()).append("\"");
                byte[] ha1 = context.getHa1();
                byte[] ha2;

                if (qop == DigestQop.AUTH) {
                    ha2 = createHA2Auth(context);
                } else {
                    ha2 = createHA2AuthInt();
                }
                String rspauth = new String(createRFC2617RequestDigest(ha1, ha2, context), StandardCharsets.UTF_8);
                sb.append(",").append(Headers.RESPONSE_AUTH.toString()).append("=\"").append(rspauth).append("\"");
                sb.append(",").append(Headers.CNONCE.toString()).append("=\"").append(parsedHeader.get(DigestAuthorizationToken.CNONCE)).append("\"");
                sb.append(",").append(Headers.NONCE_COUNT.toString()).append("=").append(parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT));
            }

            HeaderMap responseHeader = exchange.getResponseHeaders();
            responseHeader.add(AUTHENTICATION_INFO, sb.toString());
        }

        exchange.removeAttachment(DigestContext.ATTACHMENT_KEY);
    }

    private byte[] createHA2Auth(final DigestContext context) {
        byte[] digestUri = context.getParsedHeader().get(DigestAuthorizationToken.DIGEST_URI).getBytes(StandardCharsets.UTF_8);

        MessageDigest digest = context.getDigest();
        try {
            digest.update(COLON);
            digest.update(digestUri);

            return HexConverter.convertToHexBytes(digest.digest());
        } finally {
            digest.reset();
        }
    }

    private static class DigestContext {

        static final AttachmentKey<DigestContext> ATTACHMENT_KEY = AttachmentKey.create(DigestContext.class);

        private String method;
        private String nonce;
        private DigestQop qop;
        private byte[] ha1;
        private DigestAlgorithm algorithm;
        private MessageDigest digest;
        private boolean stale = false;
        Map<DigestAuthorizationToken, String> parsedHeader;

        String getMethod() {
            return method;
        }

        void setMethod(String method) {
            this.method = method;
        }

        boolean isStale() {
            return stale;
        }

        void markStale() {
            this.stale = true;
        }

        String getNonce() {
            return nonce;
        }

        void setNonce(String nonce) {
            this.nonce = nonce;
        }

        DigestQop getQop() {
            return qop;
        }

        void setQop(DigestQop qop) {
            this.qop = qop;
        }

        byte[] getHa1() {
            return ha1;
        }

        void setHa1(byte[] ha1) {
            this.ha1 = ha1;
        }

        DigestAlgorithm getAlgorithm() {
            return algorithm;
        }

        void setAlgorithm(DigestAlgorithm algorithm) throws NoSuchAlgorithmException {
            this.algorithm = algorithm;
            digest = algorithm.getMessageDigest();
        }

        MessageDigest getDigest() {
            return digest;
        }

        Map<DigestAuthorizationToken, String> getParsedHeader() {
            return parsedHeader;
        }

        void setParsedHeader(Map<DigestAuthorizationToken, String> parsedHeader) {
            this.parsedHeader = parsedHeader;
        }

    }

    private class DigestCredentialImpl implements DigestCredential {

        private final DigestContext context;

        private DigestCredentialImpl(final DigestContext digestContext) {
            this.context = digestContext;
        }

        @Override
        public DigestAlgorithm getAlgorithm() {
            return context.getAlgorithm();
        }

        @Override
        public boolean verifyHA1(byte[] ha1) {
            context.setHa1(ha1); // Cache for subsequent use.

            return validateRequest(context, ha1);
        }

        @Override
        public String getRealm() {
            return realmName;
        }

        @Override
        public byte[] getSessionData() {
            if (!context.getAlgorithm().isSession()) {
                throw MESSAGES.noSessionData();
            }

            byte[] nonce = context.getParsedHeader().get(DigestAuthorizationToken.NONCE).getBytes(StandardCharsets.UTF_8);
            byte[] cnonce = context.getParsedHeader().get(DigestAuthorizationToken.CNONCE).getBytes(StandardCharsets.UTF_8);

            byte[] response = new byte[nonce.length + cnonce.length + 1];
            System.arraycopy(nonce, 0, response, 0, nonce.length);
            response[nonce.length] = ':';
            System.arraycopy(cnonce, 0, response, nonce.length + 1, cnonce.length);

            return response;
        }
    }
}
