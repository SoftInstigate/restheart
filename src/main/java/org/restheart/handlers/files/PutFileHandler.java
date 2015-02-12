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
import com.mongodb.DuplicateKeyException;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import org.restheart.db.Database;
import org.restheart.db.GridFsDAO;
import org.restheart.db.GridFsRepository;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import org.restheart.utils.UnsupportedDocumentIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class PutFileHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PutFileHandler.class);
    private final FormParserFactory formParserFactory;
    private final GridFsRepository gridFsDAO;

    public PutFileHandler() {
        super();
        this.formParserFactory = FormParserFactory.builder().build();
        this.gridFsDAO = new GridFsDAO();
    }

    public PutFileHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
        this.formParserFactory = FormParserFactory.builder().build();
        this.gridFsDAO = new GridFsDAO();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
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

        Object id = context.getDocumentId();

        if (props.get("_id") == null) {
            props.put("_id", id);
        } else if (!props.get("_id").equals(id)) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "_id in content body is different than id in URL");
            return;
        }

        try {
            URLUtils.checkId(id);
        } catch (UnsupportedDocumentIdException udie) {
            String errMsg = "the type of _id in content body is not supported: " + id.getClass().getSimpleName();
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, errMsg, udie);
            return;
        }

        FormData.FormValue file = data.getFirst(fileFieldName);

        int code;
        
        try {
            if (file.getFile() != null) {
                code = gridFsDAO.createFile(getDatabase(), context.getDBName(), context.getCollectionName(), id, props, file.getFile());
            } else {
                throw new RuntimeException("error. file data is null");
            }
        } catch (Throwable t) {
            if (t instanceof DuplicateKeyException) {
                // update not supported
                String errMsg = "file resource update is not yet implemented";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_IMPLEMENTED, errMsg);
                return;
            }

            throw t;
        }

        exchange.setResponseCode(code);
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
     * @return the parsed DBObject from the form data or an empty DBObject the
     * etag value)
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
