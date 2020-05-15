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
package com.restheart.net;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClient;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.util.AttachmentKey;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.StringWriteChannelListener;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Client {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(Client.class);

    public static final AttachmentKey<String> RESPONSE_BODY_KEY = AttachmentKey.create(String.class);

    // TODO use buffer size set in configuration
    private static final DefaultByteBufferPool POOL
            = new DefaultByteBufferPool(true, 16384, 1000, 10, 100);

    private Client() {
    }

    public static Client getInstance() {
        return ClientHolder.INSTANCE;
    }

    public Response execute(Request request) throws IOException {
        try {
            return execute(XnioWorker.getContextManager().get(), 
                    request);
        } catch (Throwable t) {
            LOGGER.trace("Error executing request " + request.toString(), t);
            throw new IOException("Error executing request " + request.toString(), t);
        }
    }

    /**
     *
     * @param worker
     * @param request
     * @return
     * @throws IOException, ConnectException on connection refused
     */
    public Response execute(XnioWorker worker, Request request) throws IOException {
        final var client = UndertowClient.getInstance();

        try {
            try ( var connection = request.getProtocol().equalsIgnoreCase("https")
                    ? client.connect(request.getConnectionUri(),
                            worker,
                            Xnio.getInstance().getSslProvider(OptionMap.EMPTY),
                            POOL,
                            OptionMap.EMPTY).get()
                    : client.connect(request.getConnectionUri(),
                            worker,
                            POOL,
                            OptionMap.EMPTY).get()) {

                final CountDownLatch latch = new CountDownLatch(1);

                final List<ClientResponse> responses = new CopyOnWriteArrayList<>();

                connection.getIoThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        connection.sendRequest(
                                request.asClientRequest(),
                                createClientCallback(request, responses, latch));
                    }
                });

                latch.await(60, TimeUnit.SECONDS);

                if (responses.size() > 0) {
                    return Response.wrap(responses.get(0));
                } else {
                    throw new IOException("Error, no response");
                }
            } catch (InterruptedException ex) {
                throw new IOException(ex.getMessage(), ex);
            }
        } catch (GeneralSecurityException ex) {
            LOGGER.error("Error with SSL configuration", ex);
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private static class ClientHolder {
        private static final Client INSTANCE = new Client();
    }

    private ClientCallback<ClientExchange> createClientCallback(
            final Request request,
            final List<ClientResponse> responses,
            final CountDownLatch latch) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                if (request.getBody() != null) {
                    new StringWriteChannelListener(request.getBody())
                            .setup(result.getRequestChannel());
                }

                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(final ClientExchange result) {
                        responses.add(result.getResponse());

                        new StringReadChannelListener(result.getConnection().getBufferPool()) {

                            @Override
                            protected void stringDone(String string) {
                                result.getResponse().putAttachment(RESPONSE_BODY_KEY, string);
                                latch.countDown();
                            }

                            @Override
                            protected void error(IOException e) {
                                LOGGER.error("Error executing request {}", request, e);

                                latch.countDown();
                            }
                        }.setup(result.getResponseChannel());
                    }

                    @Override
                    public void failed(IOException e) {
                        LOGGER.error("Error executing request {}", request, e);

                        latch.countDown();
                    }
                });

                try {
                    result.getRequestChannel().shutdownWrites();
                    if (!result.getRequestChannel().flush()) {
                        result.getRequestChannel().getWriteSetter()
                                .set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, null));
                        result.getRequestChannel().resumeWrites();
                    }
                } catch (IOException e) {
                    LOGGER.error("Error executing request {}", request, e);

                    latch.countDown();
                }
            }

            @Override
            public void failed(IOException e) {
                LOGGER.error("Error executing request {}", request, e);

                latch.countDown();
            }
        };
    }
}
