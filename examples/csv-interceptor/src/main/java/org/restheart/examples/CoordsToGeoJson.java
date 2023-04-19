/*-
 * ========================LICENSE_START=================================
 * csv-interceptor
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.examples;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.exchange.BsonFromCsvRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "coordsToGeoJson", description = "transforms cordinates array to GeoJSON point object for csv loader service")
public class CoordsToGeoJson implements Interceptor<BsonFromCsvRequest, BsonResponse> {
    @Override
    public void handle(BsonFromCsvRequest request, BsonResponse response) throws Exception {
        var docs = request.getContent();

        if (docs == null) {
            return;
        }

        docs.stream().map(doc -> doc.asDocument()).filter(doc -> doc.containsKey("lon") && doc.containsKey("lat"))
                .forEachOrdered(doc -> {
                    // get Coordinates
                    var coordinates = new BsonArray();
                    coordinates.add(doc.get("lon"));
                    coordinates.add(doc.get("lat"));

                    var point = new BsonDocument();

                    point.put("type", new BsonString("Point"));
                    point.put("coordinates", coordinates);

                    // Add the object to the document
                    doc.append("point", point);
                });
    }

    @Override
    public boolean resolve(BsonFromCsvRequest request, BsonResponse response) {
        return request.isHandledBy("csvLoader") && request.isPost();
    }
}
