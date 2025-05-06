/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.exchange;

import java.io.IOException;

import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Base class for Request implementations that can be used in service requests.
 *
 * Only one request object can be instantiated per exchage. The request object
 * is instantiated by ServiceExchangeInitializer using the requestInitializer()
 * function defined by the handling service
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> generic type
 */
public abstract class ServiceRequest<T> extends Request<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRequest.class);
    private static final AttachmentKey<ServiceRequest<?>> REQUEST_KEY = AttachmentKey.create(ServiceRequest.class);

    protected T content;

    protected ServiceRequest(HttpServerExchange exchange) {
        this(exchange, false);
    }

    /**
     *
     * An intialized request is attached to the exchange using the REQUEST_KEY
     *
     * With dontAttach=true, instantiates the ServiceRequest without attaching the request
     * to the exchange
     *
     * @param exchange
     * @param dontAttach true, if the request won't be attached to the exchange
     *
     */
    ServiceRequest(HttpServerExchange exchange, boolean dontAttach) {
        super(exchange);
        setContentInjected(false);

        if (!dontAttach) {
            if (exchange.getAttachment(REQUEST_KEY) != null) {
                throw new IllegalStateException("Error instantiating request object "
                    + getClass().getSimpleName()
                    + ", "
                    + exchange.getAttachment(REQUEST_KEY).getClass().getSimpleName()
                    + " already bound to the exchange");
            }

            exchange.putAttachment(REQUEST_KEY, this);
        }
    }

    public static ServiceRequest<?> of(HttpServerExchange exchange) {
        var ret = exchange.getAttachment(REQUEST_KEY);

        if (ret == null) {
            throw new IllegalStateException("Request not initialized");
        }

        return ret;
    }

    @SuppressWarnings("unchecked")
    public static <R extends ServiceRequest<?>> R of(HttpServerExchange exchange, Class<R> type) {
        var ret = exchange.getAttachment(REQUEST_KEY);

        if (ret == null) {
            throw new IllegalStateException("Request not initialized");
        }

        if (type.isAssignableFrom(ret.getClass())) {
            return (R) ret;
        } else {
            throw new IllegalStateException("Request bound to exchange is not "
                + "of the specified type,"
                + " expected " + type.getSimpleName()
                + " got " + ret.getClass().getSimpleName());
        }
    }

    /**
     * Retrieves the content of the request. If the content has not been previously read, this method
     * invokes {@code parseContent()} to parse and attach the content to the request.
     *
     * If an error occurs during the parsing of the content, the request is marked as errored, indicating that the
     * content could not be successfully parsed and attached.
     *
     * @return the content of the request, which may be newly parsed or previously retrieved
     * @throws org.restheart.exchange.BadRequestException
     */
    public T getContent() throws BadRequestException {
        if (!isContentInjected()) {
            LOGGER.trace("getContent() called but content has not been injected yet. Let's inject it.");

            try {
                setContent(parseContent());
            } catch(BadRequestException bre) {
                this.setInError(true);
                Response.of(wrapped).setInError(bre.getStatusCode(), bre.getMessage(), bre);
                throw bre;
            } catch(IOException ioe) {
                if (!isInError()) { // parseContent() might have already marked the request as errored
                    this.setInError(true);
                    Response.of(wrapped).setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "error reading request content", ioe);
                }
                // wrap ioe in unchecked exception
                throw new RuntimeException(ioe);
            }
        }

        return this.content;
    }

    public void setContent(T content) {
        this.content = content;
        setContentInjected(true);
    }

    /**
     * Parses the content from the exchange and converts it into an instance of the specified type {@code T}.
     *
     * This method retrieves data from the exchange, interprets it according to the expected format, and attempts
     * to convert this data into an object of type {@code T}.
     *
     * @return an instance of {@code T} representing the parsed content
     * @throws java.io.IOException if an IO error occurs
     * @throws org.restheart.exchange.BadRequestException if the content does not match the expected format for type {@code T}
     */
    public abstract T parseContent() throws IOException, BadRequestException;

    /**
     *
     * @param serviceName
     * @return true if the request is handled by the specified Service
     */
    public boolean isHandledBy(String serviceName) {
        return serviceName == null
            ? false
            : serviceName.equals(getPipelineInfo().getName());
    }

    /**
     * CONTENT_INJECTED is true if the request body has been already
     * injected. calling setContent() and setFileInputStream() sets
     * CONTENT_INJECTED to true.
     *
     * calling getContent() or getFileInputStream() when CONTENT_INJECTED=false
     * triggers content injection via MongoRequestContentInjector
     */
    public static final AttachmentKey<Boolean> CONTENT_INJECTED = AttachmentKey.create(Boolean.class);

    public final boolean isContentInjected() {
        return this.wrapped.getAttachment(CONTENT_INJECTED);
    }

    public final void setContentInjected(boolean value) {
        this.wrapped.putAttachment(CONTENT_INJECTED, value);
    }
}
