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
package org.restheart.handlers.injectors;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
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
import org.restheart.hal.Representation;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.ChannelReader;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BodyInjectorHandler extends PipedHttpHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(BodyInjectorHandler.class);

    static final String PROPERTIES = "properties";
    static final String _ID = "_id";
    static final String CONTENT_TYPE = "contentType";
    static final String FILENAME = "filename";

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
    public void handleRequest(final HttpServerExchange exchange, final RequestContext context) throws Exception {
        if (context.getMethod() == RequestContext.METHOD.GET
                || context.getMethod() == RequestContext.METHOD.OPTIONS
                || context.getMethod() == RequestContext.METHOD.DELETE) {
            getNext().handleRequest(exchange, context);
            return;
        }

        // check the content type
        HeaderValues contentTypes = exchange.getRequestHeaders().get(Headers.CONTENT_TYPE);

        if (isPutFileRequest(context) || isPostFilesbucketRequest(context)) {
            if (unsupportedContentTypeForFiles(contentTypes)) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, ERROR_INVALID_CONTENTTYPE_FILE);
                return;
            }
        } else if (unsupportedContentType(contentTypes)) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, ERROR_INVALID_CONTENTTYPE);
            return;
        }

        DBObject content;

        if (isNotFormData(contentTypes)) { // json or hal+json
            final String contentString = ChannelReader.read(exchange.getRequestChannel());

            try {
                Object _content = JSON.parse(contentString);

                if (_content == null
                        || _content instanceof BasicDBObject
                        || _content instanceof BasicDBList) {
                    content = (DBObject) _content;
                } else {
                    throw new IllegalArgumentException("JSON parser returned a " + _content.getClass().getSimpleName() + ". Must be a json object.");
                }
            } catch (JSONParseException | IllegalArgumentException ex) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "Invalid JSON", ex);
                return;
            }
        } else { // multipart form -> file
            FormDataParser parser = this.formParserFactory.createParser(exchange);

            if (parser == null) {
                String errMsg = "There is no form parser registered for the request content type";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg);
                return;
            }

            FormData formData;

            try {
                formData = parser.parseBlocking();
            } catch (IOException ioe) {
                String errMsg = "Error parsing the multipart form: data could not be read";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, ioe);
                return;
            }

            try {
                content = extractProperties(formData);
            } catch (JSONParseException | IllegalArgumentException ex) {
                String errMsg = "Invalid data: 'properties' field is not a valid JSON";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, ex);
                return;
            }

            final String fileField = extractFileField(formData);

            if (fileField == null) {
                String errMsg = "This request does not contain any binary file";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg);
                return;
            }

            final File file = formData.getFirst(fileField).getFile();

            putFilename(formData.getFirst(fileField).getFileName(), file.getName(), content);

            LOGGER.debug("@@@ content = " + content.toString());

            context.setFile(file);

            injectContentTypeFromFile(content, file);
        }

        if (content == null) {
            context.setContent(null);
        } else {
            if (content instanceof BasicDBList) {
                ((BasicDBList) content).stream().forEach(_doc -> {
                    if (_doc instanceof BasicDBObject) {
                        Object _id = ((BasicDBObject)_doc).get(_ID);

                        try {
                            checkId((BasicDBObject) _doc);
                        } catch (UnsupportedDocumentIdException udie) {
                            String errMsg = "the type of _id in content body is not supported: " + (_id == null ? "" : _id.getClass().getSimpleName());
                            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, udie);
                        }
                    } else {
                        String errMsg = "the content must be either an object or an array of objects";
                        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg);
                    }
                });
            } else if (content instanceof BasicDBObject) {
                Object _id = content.get(_ID);

                try {
                    checkId((BasicDBObject) content);
                } catch (UnsupportedDocumentIdException udie) {
                    String errMsg = "the type of _id in content body is not supported: " + (_id == null ? "" : _id.getClass().getSimpleName());
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, udie);
                    return;
                }

                filterJsonContent(content, context);
            }

            context.setContent(content);
        }

        getNext().handleRequest(exchange, context);
    }

    private Object checkId(BasicDBObject doc) throws UnsupportedDocumentIdException {
        Object _id = doc.get(_ID);

        if (_id != null) {
            URLUtils.checkId(_id);
        }

        return _id;
    }

    /**
     * put the filename into target DBObject
     *
     * If filename is not null and properties don't have a filename then put the
     * filename.
     *
     * If filename is not null but properties contain a filename key then put
     * the properties filename value.
     *
     * If both filename is null and properties don't contain a filename then use
     * the default value.
     *
     * @param formDataFilename
     * @param defaultFilename
     * @param target
     */
    protected static void putFilename(final String formDataFilename, final String defaultFilename, final DBObject target) {
        // a filename attribute in optional properties overrides the provided part's filename 
        String filename = target.containsField(FILENAME) && target.get(FILENAME) instanceof String
                ? (String) target.get(FILENAME)
                : formDataFilename;
        if (filename == null || filename.isEmpty()) {
            LOGGER.debug("No filename in neither multipart content disposition header nor in properties! Using default value");
            filename = defaultFilename;
        }
        target.put(FILENAME, filename);
    }

    private static boolean isPostFilesbucketRequest(final RequestContext context) {
        return context.getType() == RequestContext.TYPE.FILES_BUCKET && context.getMethod() == RequestContext.METHOD.POST;
    }

    private static boolean isPutFileRequest(final RequestContext context) {
        return context.getType() == RequestContext.TYPE.FILE && context.getMethod() == RequestContext.METHOD.PUT;
    }

    /**
     *
     * @param contentTypes
     * @return true if the content-type is NOT form data
     */
    private static boolean isNotFormData(final HeaderValues contentTypes) {
        return contentTypes.stream()
                .noneMatch(ct -> ct.startsWith(Representation.APP_FORM_URLENCODED_TYPE)
                        || ct.startsWith(Representation.MULTIPART_FORM_DATA_TYPE));
    }

    /**
     * Clean-up the JSON content, filtering out reserved keys
     *
     * @param content
     * @param ctx
     */
    private static void filterJsonContent(final DBObject content, final RequestContext ctx) {
        filterOutReservedKeys(content, ctx);
    }

    /**
     * Filter out reserved keys, removing them from request
     *
     * The _ prefix is reserved for RESTHeart-generated properties (_id is
     * allowed)
     *
     * @param content
     * @param context
     */
    private static void filterOutReservedKeys(final DBObject content, final RequestContext context) {
        final HashSet<String> keysToRemove = new HashSet<>();
        content.keySet().stream()
                .filter(key -> key.startsWith("_") && !key.equals(_ID))
                .forEach(key -> {
                    keysToRemove.add(key);
                });

        keysToRemove.stream().map(keyToRemove -> {
            content.removeField(keyToRemove);
            return keyToRemove;
        }).forEach(keyToRemove -> {
            context.addWarning("Reserved field " + keyToRemove + " was filtered out from the request");
        });
    }

    private static void injectContentTypeFromFile(final DBObject content, final File file) throws IOException {
        if (content.get(CONTENT_TYPE) == null && file != null) {
            final String contentType = detectMediaType(file);
            if (contentType != null) {
                content.put(CONTENT_TYPE, contentType);
            }
        }
    }

    /**
     * true is the content-type is unsupported
     *
     * @param contentTypes
     * @return
     */
    private static boolean unsupportedContentType(final HeaderValues contentTypes) {
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
    private static boolean unsupportedContentTypeForFiles(final HeaderValues contentTypes) {
        return contentTypes == null
                || contentTypes.isEmpty()
                || contentTypes.stream().noneMatch(
                        ct -> ct.startsWith(Representation.APP_FORM_URLENCODED_TYPE)
                        || ct.startsWith(Representation.MULTIPART_FORM_DATA_TYPE));
    }

    /**
     * Search the request for a field named 'properties' which must contain
     * valid JSON
     *
     * @param formData
     * @return the parsed DBObject from the form data or an empty DBObject
     */
    protected static DBObject extractProperties(final FormData formData) throws JSONParseException {
        DBObject properties = new BasicDBObject();

        final String propsString = formData.getFirst(PROPERTIES) != null
                ? formData.getFirst(PROPERTIES).getValue()
                : null;

        if (propsString != null) {
            properties = (DBObject) JSON.parse(propsString);
        }

        return properties;
    }

    /**
     * Find the name of the first file field in this request
     *
     * @param formData
     * @return the first file field name or null
     */
    private static String extractFileField(final FormData formData) {
        String fileField = null;
        for (String f : formData) {
            if (formData.getFirst(f) != null && formData.getFirst(f).isFile()) {
                fileField = f;
                break;
            }
        }
        return fileField;
    }

    /**
     * Detect the file's mediatype
     *
     * @param file input file
     * @return the content-type as a String
     * @throws IOException
     */
    public static String detectMediaType(File file) throws IOException {
        return new Tika().detect(file);
    }
}
