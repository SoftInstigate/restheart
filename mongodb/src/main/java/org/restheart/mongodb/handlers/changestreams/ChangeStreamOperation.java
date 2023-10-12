/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.mongodb.handlers.changestreams;

import java.util.ArrayList;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.exchange.InvalidMetadataException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class ChangeStreamOperation {

    public static final String STREAM_ELEMENT_NAME = "streams";
    public static final String URI_ELEMENT_NAME = "uri";
    public static final String STAGES_ELEMENT_NAME = "stages";

    /**
     *
     * @param collProps
     * @return
     * @throws InvalidMetadataException
     */
    public static List<ChangeStreamOperation> getFromJson(BsonDocument collProps) throws InvalidMetadataException {
        if (collProps == null) {
            return null;
        }

        ArrayList<ChangeStreamOperation> ret = new ArrayList<>();

        var _streams = collProps.get(STREAM_ELEMENT_NAME);

        if (_streams == null) {
            return ret;
        }

        if (!_streams.isArray()) {
            throw new InvalidMetadataException("element '"
                    + STREAM_ELEMENT_NAME
                    + "' is not an array list." + _streams);
        }

        var streams = _streams.asArray();

        for (var _query : streams.getValues()) {
            if (!_query.isDocument()) {
                throw new InvalidMetadataException("element '"
                        + STREAM_ELEMENT_NAME
                        + "' is not valid." + _query);
            }

            ret.add(new ChangeStreamOperation(_query.asDocument()));
        }

        return ret;
    }

    private final String uri;
    private final BsonArray stages;

    /**
     *
     * @param properties
     * @throws org.restheart.exchange.InvalidMetadataException
     */
    public ChangeStreamOperation(BsonDocument properties) throws InvalidMetadataException {
        var _uri = properties.get(URI_ELEMENT_NAME);

        if (!properties.containsKey(URI_ELEMENT_NAME)) {
            throw new InvalidMetadataException("query does not have '" + URI_ELEMENT_NAME + "' property");
        }

        this.uri = _uri.asString().getValue();

        var _stages = properties.get(STAGES_ELEMENT_NAME);

        if (_stages == null || !_stages.isArray()) {
            throw new InvalidMetadataException("query /" + this.uri
                    + "has invalid '" + STAGES_ELEMENT_NAME
                    + "': " + _stages
                    + "; must be an array of stage objects");
        }

        // chekcs that the _stages array elements are all documents
        if (_stages.asArray().stream()
                .anyMatch(s -> !s.isDocument())) {
            throw new InvalidMetadataException("query /" + this.uri
                    + "has invalid '" + STAGES_ELEMENT_NAME
                    + "': " + _stages
                    + "; must be an array of stage objects");
        }

        if (!properties.containsKey(URI_ELEMENT_NAME)) {
            throw new InvalidMetadataException("query does not have '" + URI_ELEMENT_NAME + "' property");
        }

        this.stages = _stages.asArray();
    }

    /**
     * @return the uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * @return the stages
     */
    public BsonArray getStages() {
        return stages;
    }
}
