/*
 * RESTHeart - the data REST API server
 * Copyright (C) SoftInstigate Srl
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
package org.restheart.handlers.files;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.util.HttpString;
import org.bson.types.ObjectId;
import org.restheart.db.Database;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import static org.restheart.handlers.files.GetBinaryFileHandler.extractBucketName;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import org.restheart.utils.UnsupportedDocumentIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.restheart.utils.URLUtils.getReferenceLink;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class PostBinaryFileHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostBinaryFileHandler.class);
    private final FormParserFactory formParserFactory;

    public PostBinaryFileHandler() {
        super();
        this.formParserFactory = FormParserFactory.builder().build();
    }

    public PostBinaryFileHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
        this.formParserFactory = FormParserFactory.builder().build();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        exchange.setResponseCode(500);

        FormDataParser parser = this.formParserFactory.createParser(exchange);
        FormData data = parser.parseBlocking();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Form fields received:");
            data.forEach((String field) -> {
                if (!data.getFirst(field).isFile()) {
                    LOGGER.debug("   name: '{}', value: '{}'", field, data.getFirst(field).getValue());
                }
            });
        }

        final String fileFieldName = findFile(data);

        if (fileFieldName == null) {
            String errMsg = "This request does not contain any file";
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg);
            return;
        }

        final DBObject props;

        try {
            props = findProps(data);
        } catch (JSONParseException jpe) {
            String errMsg = "The properties field is not valid json";
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, jpe);
            return;
        }

        ObjectId now = new ObjectId();
        Object _id = props.get("_id");

        // id
        if (_id == null) {
            _id = now;
        } else {
            try {
                URLUtils.checkId(_id);
            } catch (UnsupportedDocumentIdException udie) {
                String errMsg = "the type of _id in content body is not supported: " + _id.getClass().getSimpleName();
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, udie);
                return;
            }
        }
        
        // remove from the properties the fields that are managed directly by the GridFs
        props.removeField("_id");
        props.removeField("filename");
        props.removeField("chunkSize");
        props.removeField("uploadDate");
        props.removeField("length");
        props.removeField("md5");
        
        // it makes sure the client doesn't change this field
        props.put("_created_on", now);
        
        // add etag
        props.put("_etag", new ObjectId());

        // contentType
        Object _contentType  = props.removeField("contentType");
        String contentType;
        
        if (_contentType != null && _contentType instanceof String) {
            contentType = (String) _contentType;
        } else {
            contentType = null;
        }
        
        FormData.FormValue file = data.getFirst(fileFieldName);

        if (file.isFile()) {
            if (file.getFile() != null) {
                final String bucket = extractBucketName(context.getCollectionName());
                GridFS gridfs = new GridFS(getDatabase().getDB(context.getDBName()), bucket);
                GridFSInputFile gfsFile = gridfs.createFile(file.getFile());
                
                gfsFile.setId(_id);
                gfsFile.setContentType(contentType);
                gfsFile.setFilename(file.getFileName());
                
                props.toMap().keySet().stream().forEach(k -> gfsFile.put((String)k, props.get((String)k)));
                
                gfsFile.save();

                exchange.setResponseCode(201);
            }
        }
        
        // insert the Location handler
        exchange.getResponseHeaders()
                .add(HttpString.tryFromString("Location"),
                        getReferenceLink(context, exchange.getRequestURL(), _id));

        exchange.endExchange();
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

    /**
     * Search request for a field named 'properties' which contains JSON
     *
     * @param data
     * @return the parsed DBObject from the form data or an empty DBObject
     * the etag value)
     */
    private DBObject findProps(final FormData data) throws JSONParseException {
        DBObject result = new BasicDBObject();
        if (data.getFirst("properties") != null) {
            String metadataString = data.getFirst("properties").getValue();
            if (metadataString != null) {
                result = (DBObject) JSON.parse(metadataString);
            }
        }

        return result;
    }
}
