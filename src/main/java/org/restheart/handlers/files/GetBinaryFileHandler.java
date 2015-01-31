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
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.document.GetDocumentHandler;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class GetBinaryFileHandler extends GetDocumentHandler {

    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    private static final Logger LOGGER = LoggerFactory.getLogger(GetBinaryFileHandler.class);

    private static final int _16K = 16 * 1024;

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        DBObject document = findDocument(context);

        if (document == null) {
            LOGGER.error("Document <{}> does not exist", context.getDocumentId());
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "Document does not exist");
            return;
        }

        if (isBinaryFile(document)) {
            LOGGER.info("filename = {}", document.get("filename"));
            streamBinaryFile(exchange, context, document);
        } else {
            LOGGER.warn("Document <{}> is not a GridFS object!");
            // TODO: could throwing an exception be a better option?
            super.handleRequest(exchange, context);
        }
    }

    protected boolean isBinaryFile(DBObject document) {
        return document.containsField("filename") && document.containsField("chunkSize");
    }

    protected DBObject findDocument(RequestContext context) {
        BasicDBObject query = new BasicDBObject("_id", context.getDocumentId());
        DBObject document = dbsDAO.getCollection(context.getDBName(), context.getCollectionName()).findOne(query);
        return document;
    }

    private void streamBinaryFile(HttpServerExchange exchange, RequestContext context, DBObject document) throws IOException {
        String contentLength = document.get("length").toString();
        LOGGER.debug("@@@ content length = {}", contentLength);
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
