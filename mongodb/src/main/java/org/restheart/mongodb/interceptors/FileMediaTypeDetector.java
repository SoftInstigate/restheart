/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.interceptors;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;

import java.io.IOException;

import org.apache.tika.Tika;
import org.bson.BsonString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * detects the file content type using Tika
 */
@RegisterPlugin(name = "fileMediaTypeDetector",
    description = "add metadata.contentType property to write requests involving files using Tika.detect()",
    priority = Integer.MIN_VALUE,
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class FileMediaTypeDetector implements MongoInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(FileMediaTypeDetector.class);

    private static final String CONTENT_TYPE = "contentType";

    private static final Tika TIKA = new Tika();

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var content = request.getContent().asDocument();
        var fileInputStream = request.getFileInputStream();

        try {
            if (content.get(CONTENT_TYPE) == null && fileInputStream != null) {
                final var contentType = TIKA.detect(fileInputStream);
                if (contentType != null) {
                    content.append(CONTENT_TYPE, new BsonString(contentType));
                }
            }
        } catch (IOException ioe) {
            response.addWarning("error detecting content type");
            LOGGER.warn("error detecting content type of file", ioe);
            return;
        }

        if (content.get(CONTENT_TYPE) == null && fileInputStream != null) {
            final var contentType = TIKA.detect(fileInputStream);
            if (contentType != null) {
                content.append(CONTENT_TYPE, new BsonString(contentType));
            }
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return (request.isFile() || request.isFilesBucket()) && request.isWriteDocument();
    }
}
