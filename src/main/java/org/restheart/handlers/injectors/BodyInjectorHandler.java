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

import com.mongodb.BasicDBObject;
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
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import org.apache.tika.Tika;
import org.restheart.utils.URLUtils;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BodyInjectorHandler extends PipedHttpHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(BodyInjectorHandler.class);

    private static final String ERROR_INVALID_CONTENTTYPE = "Content-Type must be either: "
            + Representation.HAL_JSON_MEDIA_TYPE
            + " or " + Representation.JSON_MEDIA_TYPE;

    private static final String ERROR_INVALID_CONTENTTYPE_FILE = "Content-Type must be either: "
            + Representation.APP_FORM_URLENCODED_TYPE
            + " or " + Representation.MULTIPART_FORM_DATA_TYPE;

    private final FormParserFactory formParserFactory;

    /**
     * Creates a new instance of BodyInjectorHandler
     *
     * @param next
     */
    public BodyInjectorHandler(PipedHttpHandler next) {
        super(next);
        this.formParserFactory = FormParserFactory.builder().build();
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
            getNext().handleRequest(exchange, context);
            return;
        }

        // check the content type
        HeaderValues contentTypes = exchange.getRequestHeaders().get(Headers.CONTENT_TYPE);

        if ((context.getType() == RequestContext.TYPE.FILE && context.getMethod() == RequestContext.METHOD.PUT)
                || (context.getType() == RequestContext.TYPE.FILES_BUCKET && context.getMethod() == RequestContext.METHOD.POST)) {
            if (unsupportedContentTypeFiles(contentTypes)) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, ERROR_INVALID_CONTENTTYPE_FILE);
                return;
            }
        } else {
            if (unsupportedContentType(contentTypes)) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, ERROR_INVALID_CONTENTTYPE);
                return;
            }
        }

        DBObject content;

        if (isNotFormData(contentTypes)) { // json or hal+json
            final String contentString = workaroundAngularJSIssue1463(ChannelReader.read(exchange.getRequestChannel()));

            try {
                content = (DBObject) JSON.parse(contentString);
            } catch (JSONParseException | IllegalArgumentException ex) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "Invalid data", ex);
                return;
            }
        } else { // multipart form -> file
            FormDataParser parser = this.formParserFactory.createParser(exchange);

            if (parser == null) {
                String errMsg = "This request is not form encoded";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg);
                return;
            }

            FormData data;

            try {
                data = parser.parseBlocking();
            } catch (IOException ioe) {
                String errMsg = "Error parsing the multipart form";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, ioe);
                return;
            }

            try {
                content = findProps(data);
            } catch (JSONParseException | IllegalArgumentException ex) {
                String errMsg = "Invalid data";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, ex);
                return;
            }

            final String fileFieldName = findFile(data);

            if (fileFieldName == null) {
                String errMsg = "This request does not contain any file";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg);
                return;
            }

            File file = data.getFirst(fileFieldName).getFile();

            context.setFile(file);

            injectContentTypeFromFile(content, file);
        }

        if (content == null) {
            context.setContent(null);
        } else {
            filterJsonContent(content, context);

            Object _id = content.get("_id");

            if (_id != null) {
                try {
                    URLUtils.checkId(_id);
                } catch (UnsupportedDocumentIdException udie) {
                    String errMsg = "the type of _id in content body is not supported: " + _id.getClass().getSimpleName();
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, udie);
                    return;
                }
            }
        }

        getNext().handleRequest(exchange, context);
    }

    /**
     * need this to workaroung angularjs issue
     * https://github.com/angular/angular.js/issues/1463
     * 
     * TODO: allow enabling the workaround via configuration option
     *
     * @param content
     * @return
     */
    private static String workaroundAngularJSIssue1463(String contentString) {
        if (contentString == null)
            return null;
        
        String ret = contentString.replaceAll("\"€oid\" *:", "\"\\$oid\" :");
        
        if (!ret.equals(contentString)) {
            LOGGER.debug("Replaced €oid alias with $oid in message body. This is to workaround angularjs issue 1463 (https://github.com/angular/angular.js/issues/1463)");
        }
        
        return ret;
    }

    /**
     *
     * @param contentTypes
     * @return false if the content-type is form data
     */
    private static boolean isNotFormData(HeaderValues contentTypes) {
        return contentTypes.stream()
                .noneMatch(ct -> ct.startsWith(Representation.APP_FORM_URLENCODED_TYPE)
                        || ct.startsWith(Representation.MULTIPART_FORM_DATA_TYPE));
    }

    /**
     * Clean-up the JSON content, filtering out reserved keys and injecting the
     * request content in the ctx
     *
     * @param content
     * @param ctx
     */
    private void filterJsonContent(DBObject content, RequestContext ctx) {
        filterOutReservedKeys(content, ctx);
        ctx.setContent(content);
    }

    /**
     * Filter out reserved keys, removoing them from request
     *
     * @param content
     * @param context
     */
    private void filterOutReservedKeys(DBObject content, RequestContext context) {
        final HashSet<String> keysToRemove = new HashSet<>();
        content.keySet().stream()
                .filter(key -> key.startsWith("_") && !key.equals("_id"))
                .forEach(key -> {
                    keysToRemove.add(key);
                });

        keysToRemove.stream().map(keyToRemove -> {
            content.removeField(keyToRemove);
            return keyToRemove;
        }).forEach(keyToRemove -> {
            context.addWarning("the reserved field " + keyToRemove + " was filtered out from the request");
        });
    }

    private void injectContentTypeFromFile(DBObject content, File file) throws IOException {
        if (content.get("contentType") != null) {
            return;
        }

        String contentType;

        if (file == null) {
            return;
        } else {
            contentType = detectMediaType(file);
        }

        if (content == null && contentType != null) {
            content = new BasicDBObject();
        }

        content.put("contentType", contentType);
    }

    /**
     * true is the content-type is unsupported
     *
     * @param contentTypes
     * @return
     */
    private static boolean unsupportedContentType(HeaderValues contentTypes) {
        return contentTypes == null
                || contentTypes.isEmpty()
                || contentTypes.stream().noneMatch(
                        ct -> ct.startsWith(Representation.HAL_JSON_MEDIA_TYPE)
                        || ct.startsWith(Representation.JSON_MEDIA_TYPE));
    }

    /**
     * true is the content-type is unsupported
     *
     * @param contentTypes
     * @return
     */
    private static boolean unsupportedContentTypeFiles(HeaderValues contentTypes) {
        return contentTypes == null
                || contentTypes.isEmpty()
                || contentTypes.stream().noneMatch(
                        ct -> ct.startsWith(Representation.APP_FORM_URLENCODED_TYPE)
                        || ct.startsWith(Representation.MULTIPART_FORM_DATA_TYPE));
    }

    /**
     * Search request for a field named 'properties' which contains JSON
     *
     * @param data
     * @return the parsed DBObject from the form data or an empty DBObject the
     * etag value)
     */
    private DBObject findProps(final FormData data) throws JSONParseException {
        DBObject result = new BasicDBObject();
        if (data.getFirst("properties") != null) {
            String propsString = data.getFirst("properties").getValue();
            if (propsString != null) {
                result = (DBObject) JSON.parse(propsString);
            }
        }

        return result;
    }

    /**
     * Find the name of the first file field in this request
     *
     * @param data
     * @return the file field name or null
     */
    private String findFile(final FormData data) {
        String fileField = null;
        for (String f : data) {
            if (data.getFirst(f) != null && data.getFirst(f).isFile()) {
                fileField = f;
                break;
            }
        }
        return fileField;
    }

    public static String detectMediaType(File data) throws IOException {
        Tika tika = new Tika();
        return tika.detect(data);
    }
}
