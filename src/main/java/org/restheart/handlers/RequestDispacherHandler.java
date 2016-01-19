/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.handlers;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.ExchangeCompletionListener.NextListener;
import org.restheart.handlers.metadata.CheckHandler;
import org.restheart.handlers.root.GetRootHandler;
import org.restheart.handlers.collection.DeleteCollectionHandler;
import org.restheart.handlers.collection.GetCollectionHandler;
import org.restheart.handlers.collection.PatchCollectionHandler;
import org.restheart.handlers.collection.PostCollectionHandler;
import org.restheart.handlers.collection.PutCollectionHandler;
import org.restheart.handlers.database.DeleteDBHandler;
import org.restheart.handlers.database.GetDBHandler;
import org.restheart.handlers.database.PatchDBHandler;
import org.restheart.handlers.database.PutDBHandler;
import org.restheart.handlers.document.DeleteDocumentHandler;
import org.restheart.handlers.document.GetDocumentHandler;
import org.restheart.handlers.document.PatchDocumentHandler;
import org.restheart.handlers.document.PutDocumentHandler;
import org.restheart.handlers.indexes.DeleteIndexHandler;
import org.restheart.handlers.indexes.GetIndexesHandler;
import org.restheart.handlers.indexes.PutIndexHandler;
import org.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.LocaleUtils;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.restheart.Bootstrapper;
import org.restheart.Configuration;
import org.restheart.handlers.schema.JsonMetaSchemaChecker;
import static org.restheart.handlers.RequestContext.METHOD;
import static org.restheart.handlers.RequestContext.TYPE;
import org.restheart.handlers.files.DeleteBucketHandler;
import org.restheart.handlers.files.DeleteFileHandler;
import org.restheart.handlers.files.GetFileBinaryHandler;
import org.restheart.handlers.files.GetFileHandler;
import org.restheart.handlers.files.PostBucketHandler;
import org.restheart.handlers.files.PutBucketHandler;
import org.restheart.handlers.files.PutFileHandler;
import org.restheart.handlers.metadata.ResponseTranformerMetadataHandler;
import org.restheart.handlers.metadata.BeforeWriteCheckMetadataHandler;
import org.restheart.handlers.metadata.RequestTransformerMetadataHandler;
import org.restheart.handlers.aggregation.GetAggregationHandler;
import org.restheart.handlers.metadata.AfterWriteCheckMetadataHandler;
import org.restheart.handlers.schema.JsonSchemaTransformer;
import org.restheart.handlers.metadata.TransformerHandler;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public final class RequestDispacherHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDispacherHandler.class);

    private final Map<TYPE, Map<METHOD, PipedHttpHandler>> handlersMultimap;

    private final Configuration configuration = Bootstrapper.getConfiguration();

    /**
     * Creates a new instance of RequestDispacherHandler
     */
    public RequestDispacherHandler() {
        this(true);
    }

    /**
     * Used for testing. By passing a <code>false</code> parameter then handlers
     * are not initialized and you can put your own (e.g. mocks)
     *
     * @param initialize if false then do not initialize the handlersMultimap
     */
    RequestDispacherHandler(boolean initialize) {
        super(null, null);
        this.handlersMultimap = new HashMap<>();
        if (initialize) {
            defaultInit();
        }
    }

    /**
     * Put into handlersMultimap all the default combinations of types, methods
     * and PipedHttpHandler objects
     */
    protected void defaultInit() {
        LOGGER.debug("Initialize default HTTP handlers:");
        // ROOT handlers
        putPipedHttpHandler(TYPE.ROOT, METHOD.GET, new GetRootHandler());

        // DB handlres
        putPipedHttpHandler(TYPE.DB, METHOD.GET, new GetDBHandler(new ResponseTranformerMetadataHandler(null)));
        putPipedHttpHandler(TYPE.DB, METHOD.PUT, new RequestTransformerMetadataHandler(new PutDBHandler()));
        putPipedHttpHandler(TYPE.DB, METHOD.DELETE, new DeleteDBHandler());
        putPipedHttpHandler(TYPE.DB, METHOD.PATCH, new RequestTransformerMetadataHandler(new PatchDBHandler()));

        // COLLECTION handlres
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.GET, new GetCollectionHandler(new ResponseTranformerMetadataHandler(null)));
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.POST, new BeforeWriteCheckMetadataHandler(new RequestTransformerMetadataHandler(new PostCollectionHandler(new AfterWriteCheckMetadataHandler()))));
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.PUT, new RequestTransformerMetadataHandler(new PutCollectionHandler()));
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.DELETE, new DeleteCollectionHandler());
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.PATCH, new RequestTransformerMetadataHandler(new PatchCollectionHandler()));

        // DOCUMENT handlers
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.GET, new GetDocumentHandler(new ResponseTranformerMetadataHandler(null)));
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.PUT, new BeforeWriteCheckMetadataHandler(new RequestTransformerMetadataHandler(new PutDocumentHandler(new AfterWriteCheckMetadataHandler()))));
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.DELETE, new DeleteDocumentHandler());
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.PATCH, new BeforeWriteCheckMetadataHandler(new RequestTransformerMetadataHandler(new PatchDocumentHandler(new AfterWriteCheckMetadataHandler()))));

        // COLLECTION_INDEXES handlers
        putPipedHttpHandler(TYPE.COLLECTION_INDEXES, METHOD.GET, new GetIndexesHandler());

        // INDEX handlers
        putPipedHttpHandler(TYPE.INDEX, METHOD.PUT, new PutIndexHandler());
        putPipedHttpHandler(TYPE.INDEX, METHOD.DELETE, new DeleteIndexHandler());

        // FILES_BUCKET and FILE handlers
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.GET, new GetCollectionHandler(new ResponseTranformerMetadataHandler(null)));
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.POST, new BeforeWriteCheckMetadataHandler(new RequestTransformerMetadataHandler(new PostBucketHandler())));
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.PUT, new RequestTransformerMetadataHandler(new PutBucketHandler()));
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.DELETE, new DeleteBucketHandler());

        putPipedHttpHandler(TYPE.FILE, METHOD.GET, new GetFileHandler(new ResponseTranformerMetadataHandler(null)));
        putPipedHttpHandler(TYPE.FILE_BINARY, METHOD.GET, new GetFileBinaryHandler());
        putPipedHttpHandler(TYPE.FILE, METHOD.PUT, new BeforeWriteCheckMetadataHandler(new RequestTransformerMetadataHandler(new PutFileHandler())));
        putPipedHttpHandler(TYPE.FILE, METHOD.DELETE, new DeleteFileHandler());

        // AGGREGATION handler
        putPipedHttpHandler(TYPE.AGGREGATION, METHOD.GET, new GetAggregationHandler(new ResponseTranformerMetadataHandler(null)));

        // SCHEMA handlers
        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.GET, new GetCollectionHandler(new TransformerHandler(new ResponseTranformerMetadataHandler(null), new JsonSchemaTransformer())));
        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.PUT, new RequestTransformerMetadataHandler(new PutCollectionHandler()));
        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.POST, new CheckHandler(new TransformerHandler(new PostCollectionHandler(), new JsonSchemaTransformer()), new JsonMetaSchemaChecker()));
        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.DELETE, new DeleteCollectionHandler());

        putPipedHttpHandler(TYPE.SCHEMA, METHOD.GET, new GetDocumentHandler(new TransformerHandler(new ResponseTranformerMetadataHandler(null), new JsonSchemaTransformer())));
        putPipedHttpHandler(TYPE.SCHEMA, METHOD.PUT, new CheckHandler(new TransformerHandler(new RequestTransformerMetadataHandler(new PutDocumentHandler()), new JsonSchemaTransformer()), new JsonMetaSchemaChecker()));
        putPipedHttpHandler(TYPE.SCHEMA, METHOD.DELETE, new DeleteDocumentHandler());
    }

    /**
     * Given a type and method, return the appropriate PipedHttpHandler which
     * can handle this request
     *
     * @param type
     * @param method
     * @return the PipedHttpHandler
     */
    public PipedHttpHandler getPipedHttpHandler(TYPE type, METHOD method) {
        Map<METHOD, PipedHttpHandler> methodsMap = handlersMultimap.get(type);
        return methodsMap != null ? methodsMap.get(method) : null;
    }

    /**
     * Given a type and method, put in a PipedHttpHandler
     *
     * @param type the DB type
     * @param method the HTTP method
     * @param handler the PipedHttpHandler
     */
    void putPipedHttpHandler(TYPE type, METHOD method, PipedHttpHandler handler) {
        LOGGER.debug("putPipedHttpHandler( {}, {}, {} )", type, method, getHandlerToLog(handler).getClass().getCanonicalName());
        Map<METHOD, PipedHttpHandler> methodsMap = handlersMultimap.get(type);
        if (methodsMap == null) {
            methodsMap = new HashMap<>();
            handlersMultimap.put(type, methodsMap);
        }
        methodsMap.put(method, handler);
    }

    private PipedHttpHandler getHandlerToLog(PipedHttpHandler handler) {
        if (handler instanceof BeforeWriteCheckMetadataHandler || handler instanceof RequestTransformerMetadataHandler) {
            return getHandlerToLog(handler.getNext());
        } else {
            return handler;
        }
    }

    /**
     * Code to execute before each handleRequest
     *
     * @param exchange the HttpServerExchange
     * @param context the RequestContext
     */
    protected void before(HttpServerExchange exchange, RequestContext context) {
        if (LOGGER.isDebugEnabled() || configuration.isForceRequestLogging()) {
            dumpExchange(exchange);
        }
    }

    /**
     * code to execute after each handleRequest
     *
     * @param exchange the HttpServerExchange
     * @param context the RequestContext
     */
    protected void after(HttpServerExchange exchange, RequestContext context) {
    }

    /**
     * Handle the request, delegating to the proper PipedHttpHandler
     *
     * @param exchange the HttpServerExchange
     * @param context the RequestContext
     * @throws Exception
     */
    @Override
    public final void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getType() == TYPE.ERROR) {
            LOGGER.debug("This is a bad request: returning a <{}> HTTP code", HttpStatus.SC_BAD_REQUEST);
            ResponseHelper.endExchange(exchange, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (context.getMethod() == METHOD.OTHER) {
            LOGGER.debug("This method is not allowed: returning a <{}> HTTP code", HttpStatus.SC_METHOD_NOT_ALLOWED);
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (context.isReservedResource()) {
            LOGGER.debug("The resource is reserved: returning a <{}> HTTP code", HttpStatus.SC_FORBIDDEN);
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "reserved resource");
            return;
        }

        final PipedHttpHandler httpHandler = getPipedHttpHandler(context.getType(), context.getMethod());

        if (httpHandler != null) {
            before(exchange, context);
            httpHandler.handleRequest(exchange, context);
            after(exchange, context);
        } else {
            LOGGER.error("Can't find PipedHttpHandler({}, {})", context.getType(), context.getMethod());
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * dumpExchange
     * 
     * Log a complete dump of the HttpServerExchange (both Request and Response)
     * 
     * @param exchange 
     */
    protected void dumpExchange(HttpServerExchange exchange) {
        final StringBuilder sb = new StringBuilder();
        final SecurityContext sc = exchange.getSecurityContext();
        
        sb.append("\n----------------------------REQUEST---------------------------\n");
        sb.append("               URI=").append(exchange.getRequestURI()).append("\n");
        sb.append(" characterEncoding=").append(exchange.getRequestHeaders().get(Headers.CONTENT_ENCODING)).append("\n");
        sb.append("     contentLength=").append(exchange.getRequestContentLength()).append("\n");
        sb.append("       contentType=").append(exchange.getRequestHeaders().get(Headers.CONTENT_TYPE)).append("\n");

        if (sc != null) {
            if (sc.isAuthenticated()) {
                sb.append("          authType=").append(sc.getMechanismName()).append("\n");
                sb.append("         principle=").append(sc.getAuthenticatedAccount().getPrincipal()).append("\n");
            } else {
                sb.append("          authType=none" + "\n");
            }
        }

        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            cookies.entrySet().stream().map((entry) -> entry.getValue()).forEach((cookie) -> {
                sb.append("            cookie=").append(cookie.getName()).append("=").append(cookie.getValue()).append("\n");
            });
        }
        for (HeaderValues header : exchange.getRequestHeaders()) {
            header.stream().forEach((value) -> {
                sb.append("            header=").append(header.getHeaderName()).append("=").append(value).append("\n");
            });
        }
        sb.append("            locale=").append(LocaleUtils.getLocalesFromHeader(exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE))).append("\n");
        sb.append("            method=").append(exchange.getRequestMethod()).append("\n");
        Map<String, Deque<String>> pnames = exchange.getQueryParameters();
        pnames.entrySet().stream().map((entry) -> {
            String pname = entry.getKey();
            Iterator<String> pvalues = entry.getValue().iterator();
            sb.append("         parameter=");
            sb.append(pname);
            sb.append('=');
            while (pvalues.hasNext()) {
                sb.append(pvalues.next());
                if (pvalues.hasNext()) {
                    sb.append(", ");
                }
            }
            return entry;
        }).forEach((_item) -> {
            sb.append("\n");
        });

        sb.append("          protocol=").append(exchange.getProtocol()).append("\n");
        sb.append("       queryString=").append(exchange.getQueryString()).append("\n");
        sb.append("        remoteAddr=").append(exchange.getSourceAddress()).append("\n");
        sb.append("        remoteHost=").append(exchange.getSourceAddress().getHostName()).append("\n");
        sb.append("            scheme=").append(exchange.getRequestScheme()).append("\n");
        sb.append("              host=").append(exchange.getRequestHeaders().getFirst(Headers.HOST)).append("\n");
        sb.append("        serverPort=").append(exchange.getDestinationAddress().getPort()).append("\n");

        exchange.addExchangeCompleteListener((final HttpServerExchange exchange1, final NextListener nextListener) -> {
            sb.append("--------------------------RESPONSE--------------------------\n");
            if (sc != null) {
                if (sc.isAuthenticated()) {
                    sb.append("          authType=").append(sc.getMechanismName()).append("\n");
                    sb.append("         principle=").append(sc.getAuthenticatedAccount().getPrincipal()).append("\n");
                } else {
                    sb.append("          authType=none" + "\n");
                }
            }
            sb.append("     contentLength=").append(exchange1.getResponseContentLength()).append("\n");
            sb.append("       contentType=").append(exchange1.getResponseHeaders().getFirst(Headers.CONTENT_TYPE)).append("\n");
            Map<String, Cookie> cookies1 = exchange1.getResponseCookies();
            if (cookies1 != null) {
                cookies1.values().stream().forEach((cookie) -> {
                    sb.append("            cookie=").append(cookie.getName()).append("=").append(cookie.getValue()).append("; domain=").append(cookie.getDomain()).append("; path=").append(cookie.getPath()).append("\n");
                });
            }
            for (HeaderValues header : exchange1.getResponseHeaders()) {
                header.stream().forEach((value) -> {
                    sb.append("            header=").append(header.getHeaderName()).append("=").append(value).append("\n");
                });
            }
            sb.append("            status=").append(exchange1.getStatusCode()).append("\n");
            sb.append("==============================================================");
            nextListener.proceed();
            LOGGER.debug(sb.toString());
        });
    }

}
