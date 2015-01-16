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
package org.restheart.db.entity;

import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class PostDocumentEntity implements Entity {

    public final HttpServerExchange exchange;
    public final String dbName;
    public final String collName;
    public final DBObject content;
    public final ObjectId requestEtag;

    public PostDocumentEntity(HttpServerExchange exchange, String dbName, String collName, DBObject content, ObjectId requestEtag) {
        assert exchange != null && dbName != null && collName != null && content != null && requestEtag != null;
        this.collName = collName;
        this.content = content;
        this.dbName = dbName;
        this.exchange = exchange;
        this.requestEtag = requestEtag;
    }

}
