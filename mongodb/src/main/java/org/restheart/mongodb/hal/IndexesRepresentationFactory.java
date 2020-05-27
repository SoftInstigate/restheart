/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.hal;

import io.undertow.server.HttpServerExchange;
import static java.lang.Math.toIntExact;
import org.bson.BsonArray;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import static org.restheart.exchange.ExchangeKeys.FS_FILES_SUFFIX;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.exchange.IllegalQueryParamenterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class IndexesRepresentationFactory extends AbstractRepresentationFactory {
    
    private static final Logger LOGGER
            = LoggerFactory.getLogger(IndexesRepresentationFactory.class);

    public IndexesRepresentationFactory() {
    }
    
    /**
     *
     * @param exchange
     * @param embeddedData
     * @param size
     * @return
     * @throws IllegalQueryParamenterException
     */
    @Override
     public Resource getRepresentation(
            HttpServerExchange exchange,
            BsonArray embeddedData,
            long size)
            throws IllegalQueryParamenterException {
        var request = MongoRequest.of(exchange);
        
        String requestPath = URLUtils.removeTrailingSlashes(
                request.getUnmappedRequestUri());
        
        String queryString = exchange.getQueryString() == null
                || exchange.getQueryString().isEmpty()
                ? ""
                : "?" + URLUtils.decodeQueryString(exchange.getQueryString());
        
        Resource rep;
        
        if (request.isFullHalMode()) {
            rep = new Resource(requestPath + queryString);
        } else {
            rep = new Resource();
        }
        
        if (size >= 0) {
            rep.addProperty("_size", new BsonInt32(toIntExact(size)));
        }
        
        if (embeddedData != null) {
            long count = embeddedData.stream()
                    .filter(props -> props != null)
                    .filter(props -> props.isDocument())
                    .map(props -> props.asDocument())
                    .filter((props) -> props.keySet().stream()
                    .anyMatch((k) -> k.equals("id") || k.equals("_id")))
                    .count();
            
            rep.addProperty("_returned", new BsonInt32(toIntExact(count)));
            
            if (!embeddedData.isEmpty()) {
                embeddedDocuments(
                        embeddedData,
                        requestPath,
                        rep,
                        request.isFullHalMode());
            }
        }
        
        if (request.isFullHalMode()) {
            rep.addProperty("_type",
                    new BsonString(request.getType().name()));
            
            if (request.isParentAccessible()) {
                // this can happen due to mongo-mounts mapped URL
                if (request.getCollectionName().endsWith(
                        FS_FILES_SUFFIX)) {
                    rep.addLink(new Link(
                            "rh:bucket",
                            URLUtils.getParentPath(requestPath)));
                } else {
                    rep.addLink(new Link(
                            "rh:coll",
                            URLUtils.getParentPath(requestPath)));
                }
            }
            
            rep.addLink(new Link("rh:indexes", requestPath));
        }
        
        return rep;
    }
    
    private static void embeddedDocuments(
            BsonArray embeddedData,
            String requestPath,
            Resource rep,
            boolean isHal) {
        embeddedData.stream()
                .filter(d -> d != null)
                .filter(d -> d.isDocument())
                .map(d -> d.asDocument())
                .forEach((d) -> {
                    BsonValue _id = d.get("_id");
                    
                    if (_id != null
                            && (_id.isString()
                            || _id.isObjectId())) {
                        Resource nrep = new Resource();
                        
                        if (isHal) {
                            nrep.addProperty("_type",
                                    new BsonString(TYPE.INDEX.name()));
                        }
                        
                        nrep.addProperties(d);
                        
                        rep.addChild("rh:index", nrep);
                    } else {
                        rep.addWarning("index with _id "
                                + _id
                                + (_id == null
                                        ? " "
                                        : " of type "
                                        + _id.getBsonType().name())
                                + "filtered out. Indexes can only "
                                + "have ids of type String");
                        
                        LOGGER.debug("index missing string _id field", d);
                    }
                });
    }
}
