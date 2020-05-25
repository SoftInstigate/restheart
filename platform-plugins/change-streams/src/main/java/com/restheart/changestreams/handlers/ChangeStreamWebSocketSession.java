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
package com.restheart.changestreams.handlers;

import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 *
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */

public class ChangeStreamWebSocketSession {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(ChangeStreamWebSocketSession.class);
    
    private final String sessionId;
    private final SessionKey sessionKey;
    private final WebSocketChannel webSocketChannel;

    public ChangeStreamWebSocketSession(WebSocketChannel channel, SessionKey sessionKey) {
        this.sessionId = new SecureRandomSessionIdGenerator().createSessionId();
        this.webSocketChannel = channel;
        this.sessionKey = sessionKey;
        initChannelReceiveListener(webSocketChannel);
    }

    private void initChannelReceiveListener(WebSocketChannel channel) {
        channel.getReceiveSetter().set(
                new ChangeStreamReceiveListener(this));
        
        channel.resumeReceives();
    }

    public String getId() {
        return this.sessionId;
    }
    
    public SessionKey getSessionKey() {
        return this.sessionKey;
    }
    
    public WebSocketChannel getChannel() {
        return this.webSocketChannel;
    }

    class ChangeStreamReceiveListener extends AbstractReceiveListener {

        private final ChangeStreamWebSocketSession session;
        
        public ChangeStreamReceiveListener(ChangeStreamWebSocketSession session) {
            this.session = session;
        }
        @Override
        protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
            LOGGER.debug("Stream connection closed, sessionkey={}", sessionKey);
            GuavaHashMultimapSingleton.remove(this.session.getSessionKey(), session);
            webSocketChannel.close();
        }
    }
}
