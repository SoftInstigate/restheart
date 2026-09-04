/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.security.authorizers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.List;

import org.restheart.exchange.Request;
import org.restheart.plugins.security.RequestDescriptor;
import org.restheart.security.BaseAccount;

import io.undertow.connector.ByteBufferPool;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.ServerConnection;
import io.undertow.server.ServerConnection.CloseListener;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;

/**
 * Builds a {@link Request} from a {@link RequestDescriptor} for {@link MongoAclAuthorizer}/
 * {@link FileAclAuthorizer} to evaluate their existing, unmodified predicate-based {@code
 * isAllowed(Request)} logic against — see restheart#722.
 *
 * <p>Verified against Undertow 2.3.10's actual bytecode (decompiled, not guessed) before writing
 * this: the predicates RESTHeart's ACL DSL compiles to ({@code path-prefix(...)}, {@code
 * method(...)}, ...) call only plain field getters on {@link HttpServerExchange} ({@code
 * getRelativePath()}, {@code getRequestMethod()}, {@code getQueryParameters()}, ...) — no I/O —
 * and {@link HttpServerExchange}'s constructor never invokes any method on the {@link
 * ServerConnection} it's given. So {@link NoopServerConnection} — throwing {@link
 * UnsupportedOperationException} from every method — is safe: if some future predicate type or
 * ACL feature ever does need real I/O here, it fails loudly instead of silently misbehaving.
 *
 * <p>Entirely private to {@code restheart-security}: no other module ever needs to know a
 * synthetic exchange is involved.
 */
final class SyntheticRequestFactory {

    private SyntheticRequestFactory() {
    }

    static Request<?> from(RequestDescriptor descriptor) {
        var exchange = new HttpServerExchange(new NoopServerConnection());

        exchange.setRequestMethod(HttpString.tryFromString(descriptor.method()));
        exchange.setRequestPath(descriptor.path());
        exchange.setRelativePath(descriptor.path());
        exchange.setRequestURI(descriptor.path());
        exchange.setRequestScheme(descriptor.scheme() == null ? "http" : descriptor.scheme());

        descriptor.queryParameters().forEach((name, values) -> exchange.getQueryParameters().put(name, new ArrayDeque<>(values)));
        descriptor.headers().forEach((name, values) -> exchange.getRequestHeaders().addAll(HttpString.tryFromString(name), List.copyOf(values)));
        descriptor.cookies().forEach((name, value) -> exchange.setRequestCookie(new CookieImpl(name, value)));

        if (descriptor.remoteAddress() != null) {
            exchange.setSourceAddress(new InetSocketAddress(descriptor.remoteAddress(), 0));
        }

        exchange.setSecurityContext(new PrincipalOnlySecurityContext(descriptor.principal()));

        return new SyntheticRequest(exchange);
    }

    private static final class SyntheticRequest extends Request<Void> {
        SyntheticRequest(HttpServerExchange exchange) {
            super(exchange);
        }

        @Override
        public String getContentType() {
            return null;
        }
    }

    /** Reports the descriptor's already-authenticated principal — the only thing ACL evaluation actually reads off a security context. */
    private static final class PrincipalOnlySecurityContext implements SecurityContext {
        private final BaseAccount principal;

        PrincipalOnlySecurityContext(BaseAccount principal) {
            this.principal = principal;
        }

        @Override
        public boolean isAuthenticated() {
            return principal != null;
        }

        @Override
        public Account getAuthenticatedAccount() {
            return principal;
        }

        @Override
        public boolean authenticate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean login(String username, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAuthenticationRequired() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAuthenticationRequired() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addAuthenticationMechanism(AuthenticationMechanism mechanism) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuthenticationMechanism> getAuthenticationMechanisms() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMechanismName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IdentityManager getIdentityManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void authenticationComplete(Account account, String mechanismName, boolean cachingRequired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void authenticationFailed(String message, String mechanismName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerNotificationReceiver(NotificationReceiver receiver) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeNotificationReceiver(NotificationReceiver receiver) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Never actually invoked — see this class's javadoc. Every method throws {@link
     * UnsupportedOperationException} rather than returning a made-up value, so an unexpected call
     * fails loudly instead of silently misbehaving.
     */
    private static final class NoopServerConnection extends ServerConnection {
        @Override
        public Pool<java.nio.ByteBuffer> getBufferPool() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBufferPool getByteBufferPool() {
            throw new UnsupportedOperationException();
        }

        @Override
        public XnioWorker getWorker() {
            throw new UnsupportedOperationException();
        }

        @Override
        public XnioIoThread getIoThread() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isContinueResponseSupported() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void terminateRequestChannel(HttpServerExchange exchange) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean supportsOption(Option<?> option) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getOption(Option<T> option) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws IOException {
            // no-op: never opened anything real
        }

        @Override
        public SocketAddress getPeerAddress() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketAddress getLocalAddress() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OptionMap getUndertowOptions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getBufferSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.undertow.server.SSLSessionInfo getSslSessionInfo() {
            return null;
        }

        @Override
        public void setSslSessionInfo(io.undertow.server.SSLSessionInfo sessionInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addCloseListener(CloseListener listener) {
            // no-op: never closed for real
        }

        @Override
        public ChannelListener.Setter<? extends ConnectedChannel> getCloseSetter() {
            return null;
        }

        @Override
        public String getTransportProtocol() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isRequestTrailerFieldsSupported() {
            return false;
        }

        @Override
        protected StreamConnection upgradeChannel() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ConduitStreamSinkChannel getSinkChannel() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ConduitStreamSourceChannel getSourceChannel() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected StreamSinkConduit getSinkConduit(HttpServerExchange exchange, StreamSinkConduit conduit) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean isUpgradeSupported() {
            return false;
        }

        @Override
        protected boolean isConnectSupported() {
            return false;
        }

        @Override
        protected void exchangeComplete(HttpServerExchange exchange) {
            // no-op
        }

        @Override
        protected void setUpgradeListener(HttpUpgradeListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void setConnectListener(HttpUpgradeListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void maxEntitySizeUpdated(HttpServerExchange exchange) {
            // no-op
        }
    }
}
