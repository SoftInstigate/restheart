/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.restheart.security.net;

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
import org.restheart.security.Bootstrapper;
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

    private static final DefaultByteBufferPool POOL
            = new DefaultByteBufferPool(true,
                    Bootstrapper.getConfiguration() == null ? 16384
                    : Bootstrapper.getConfiguration().getBufferSize(),
                    1000,
                    10,
                    100);

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
            LOGGER.error("Error executing request " + request.toString(), t);
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
