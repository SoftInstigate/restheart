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
import com.mongodb.gridfs.GridFSDBFile;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import org.bson.types.ObjectId;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.document.DocumentRepresentationFactory;
import org.restheart.handlers.document.GetDocumentHandler;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class GetFileHandler extends GetDocumentHandler {

    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    private static final Logger LOGGER = LoggerFactory.getLogger(GetFileHandler.class);

    private static final int _16K = 16 * 1024;

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        BasicDBObject query = new BasicDBObject("_id", context.getDocumentId());

        DBObject document = dbsDAO.getCollection(context.getDBName(), context.getCollectionName()).findOne(query);

        if (document == null) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "document does not exist");
            return;
        }

        if (isBinaryRequest(extractHeaderValues(exchange, context))) {
            boolean isFile = document.containsField("filename") && document.containsField("chunkSize");
            LOGGER.info("@@@ filename = {}", document.get("filename"));
            if (isFile) {
                handleFile(exchange, context, document);
                return;
            }
        }

        Object etag = document.get("_etag");

        if (etag != null && ObjectId.isValid("" + etag)) {
            ObjectId _etag = new ObjectId("" + etag);

            document.put("_lastupdated_on", Instant.ofEpochSecond(_etag.getTimestamp()).toString());

            // in case the request contains the IF_NONE_MATCH header with the current etag value,
            // just return 304 NOT_MODIFIED code
            if (RequestHelper.checkReadEtag(exchange, etag.toString())) {
                ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_MODIFIED);
                return;
            }
        }

        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());

        ResponseHelper.injectEtagHeader(exchange, document);
        exchange.setResponseCode(HttpStatus.SC_OK);

        DocumentRepresentationFactory.sendDocument(requestPath, exchange, context, document);
        exchange.endExchange();
    }

    private HeaderValues extractHeaderValues(HttpServerExchange exchange, RequestContext context) {
        HeaderMap headers = exchange.getRequestHeaders();
        HeaderValues headerValues = headers.get(new HttpString("Accept"));
        LOGGER.info("@@@ headerValues = {}", headerValues);
        LOGGER.info("@@@ getCollectionName = {}", context.getCollectionName());
        return headerValues;
    }

    private static boolean isBinaryRequest(HeaderValues headerValues) {
        return headerValues != null && headerValues.size() > 0 && headerValues.contains(APPLICATION_OCTET_STREAM);
    }

    private void handleFile(HttpServerExchange exchange, RequestContext context, DBObject document) throws IOException {
        String contentLength = document.get("length").toString();
        LOGGER.info("@@@ content length = {}", contentLength);
        String bucket = extractBucket(context.getCollectionName());
        // read the file from GridFS
        GridFS gridfs = new GridFS(dbsDAO.getDB(context.getDBName()), bucket);
        GridFSDBFile dbsfile = gridfs.findOne((String) document.get("filename"));

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, APPLICATION_OCTET_STREAM);
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, contentLength);
        exchange.setResponseCode(HttpStatus.SC_OK);

        copy(dbsfile.getInputStream(), exchange.getOutputStream());

        exchange.endExchange();
    }

    void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[_16K];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    static String extractBucket(String collectionName) {
        return collectionName.split("\\.")[0];
    }
}
