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
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.time.Instant;
import org.bson.types.ObjectId;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.document.DocumentRepresentationFactory;
import org.restheart.handlers.document.GetDocumentHandler;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtilis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class GetFileHandler extends GetDocumentHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GetFileHandler.class);

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        ObjectId objectId = null;
        String stringObjectId = null;

        if (ObjectId.isValid(context.getDocumentId())) {
            objectId = new ObjectId(context.getDocumentId());
        } else {
            // the id is not an acutal ObjectId
            stringObjectId = context.getDocumentId();
        }

        final BasicDBObject query = objectId != null
                ? new BasicDBObject("_id", objectId)
                : new BasicDBObject("_id", stringObjectId);

        DBObject document = dbsDAO.getCollection(context.getDBName(), context.getCollectionName()).findOne(query);

        if (document == null) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "document does not exist");
            return;
        }

        HeaderMap headers = exchange.getRequestHeaders();

        HeaderValues headerValues = headers.get(new HttpString("Accept"));
        LOGGER.info("@@@ headerValues = {}", headerValues.toString());
        LOGGER.info("@@@ getCollectionName = {}", context.getCollectionName());
        if (headerValues != null && headerValues.size() > 0 && headerValues.contains("application/octet-stream")) {
            // check if this is a file
            boolean isFile = document.containsField("filename") && document.containsField("chunkSize");
            LOGGER.info("@@@ filename = {}", document.get("filename"));
            if (isFile) {
                String bucket = extractBucket(context.getCollectionName());
                // read the file from GridFS
                GridFS gridfs = new GridFS(dbsDAO.getDB(context.getDBName()), bucket);
                GridFSDBFile dbsfile = gridfs.findOne((String)document.get("filename"));
                ReadableByteChannel inputChannel = Channels.newChannel(dbsfile.getInputStream());
                StreamSinkChannel responseChannel = exchange.getResponseChannel();
                
                fastCopy(inputChannel, responseChannel);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
                exchange.setResponseCode(HttpStatus.SC_OK);
                exchange.endExchange();
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

        String requestPath = URLUtilis.removeTrailingSlashes(exchange.getRequestPath());

        ResponseHelper.injectEtagHeader(exchange, document);
        exchange.setResponseCode(HttpStatus.SC_OK);

        DocumentRepresentationFactory.sendDocument(requestPath, exchange, context, document);
        exchange.endExchange();
    }

    static String extractBucket(String collectionName) {
        String bucket = collectionName.split("\\.")[0];
        return bucket;
    }
    
    static void fastCopy(final ReadableByteChannel src, final WritableByteChannel dest) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(32 * 1024);
        
        while(src.read(buffer) != -1) {
            buffer.flip();
            dest.write(buffer);
            buffer.compact();
        }
        
        buffer.flip();
        
        while(buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }
}
