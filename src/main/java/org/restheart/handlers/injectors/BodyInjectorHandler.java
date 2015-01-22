/*
 * RESTHeart - the data REST API server
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
package org.restheart.handlers.injectors;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import org.restheart.hal.Representation;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.ChannelReader;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.util.HashSet;
import org.restheart.hal.HALUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BodyInjectorHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of BodyInjectorHandler
     *
     * @param next
     */
    public BodyInjectorHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getMethod() == RequestContext.METHOD.GET
                || context.getMethod() == RequestContext.METHOD.OPTIONS
                || context.getMethod() == RequestContext.METHOD.DELETE) {
            next.handleRequest(exchange, context);
            return;
        }

        // check content type
        HeaderValues contentTypes = exchange.getRequestHeaders().get(Headers.CONTENT_TYPE);

        if (unsupportedContentType(contentTypes)) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be either: " + Representation.HAL_JSON_MEDIA_TYPE 
                            + ", " + Representation.JSON_MEDIA_TYPE 
                            + ", " + Representation.APP_FORM_URLENCODED_TYPE 
                            + ", " + Representation.MULTIPART_FORM_DATA_TYPE);
            return;
        }

        String _content = ChannelReader.read(exchange.getRequestChannel());

        DBObject content;

        try {
            content = (DBObject) JSON.parse(_content);
        } catch (JSONParseException ex) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "invalid data", ex);
            return;
        }

        HashSet<String> keysToRemove = new HashSet<>();

        if (content == null) {
            context.setContent(null);
        } else {
            // filter out reserved keys
            content.keySet().stream().filter(key -> key.startsWith("_") && !key.equals("_id")).forEach(key -> {
                keysToRemove.add(key);
            });

            keysToRemove.stream().map(keyToRemove -> {
                content.removeField(keyToRemove);
                return keyToRemove;
            }).forEach(keyToRemove -> {
                context.addWarning("the reserved field " + keyToRemove + " was filtered out from the request");
            });

            //replace string that are valid ObjectIds with ObjectIds objects.
            HALUtils.replaceStringsWithObjectIds(content);

            // inject the request content in the context
            context.setContent(content);
        }

        next.handleRequest(exchange, context);
    }

    private static boolean unsupportedContentType(HeaderValues contentTypes) {
        return contentTypes == null
                || contentTypes.isEmpty()
                || contentTypes.stream().noneMatch(
                        ct -> ct.startsWith(Representation.HAL_JSON_MEDIA_TYPE)
                                || ct.startsWith(Representation.JSON_MEDIA_TYPE)
                                || ct.startsWith(Representation.APP_FORM_URLENCODED_TYPE)
                                || ct.startsWith(Representation.MULTIPART_FORM_DATA_TYPE));
    }
}
