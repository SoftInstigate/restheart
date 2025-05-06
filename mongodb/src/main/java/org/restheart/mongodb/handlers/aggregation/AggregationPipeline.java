/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.mongodb.handlers.aggregation;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.restheart.exchange.InvalidMetadataException;

/**
 * represents an aggregation pipeline.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AggregationPipeline extends AbstractAggregationOperation {

    /**
     * the stages property name
     */
    public static final String STAGES_ELEMENT_NAME = "stages";

    /**
     * the allowDiskUse property name
     */
    public static final String ALLOW_DISK_USER_ELEMENT_NAME = "allowDiskUse";

    private final BsonArray stages;
    private final BsonBoolean allowDiskUse;

    /**
     * Constructor from aggregation definition in collection metadata
     *
     *
     * @param properties the json object defining the aggregation; it must include the
     * array {@code stages}. Note: dollar prefixed operators in the stages
     * must be underscore escaped, e.g. "_$exits". Example:
     *
     * <pre>
    {
        "type": "pipeline",
        "uri": "test_ap",
        "allowDiskUse": false,
        "stages": [ { "_$match": { "name": { "_$exists": true } } } ]
    }
     </pre>
     *
     * @throws org.restheart.exchange.InvalidMetadataException
     */
    public AggregationPipeline(BsonDocument properties) throws InvalidMetadataException {
        super(properties);

        var _stages = properties.get(STAGES_ELEMENT_NAME);

        if (_stages == null || !_stages.isArray()) {
            throw new InvalidMetadataException("query /" + getUri() + "has invalid '" + STAGES_ELEMENT_NAME + "': " + _stages + "; must be an array of stage objects");
        }

        // chekcs that the _stages array elements are all documents
        if (_stages.asArray().stream().anyMatch(s -> !s.isDocument())) {
            throw new InvalidMetadataException("query /" + getUri() + "has invalid '" + STAGES_ELEMENT_NAME + "': " + _stages + "; must be an array of stage objects");
        }

        this.stages = _stages.asArray();

        var _allowDiskUse = properties.get(ALLOW_DISK_USER_ELEMENT_NAME);

        if (_allowDiskUse != null && !_allowDiskUse.isBoolean()) {
            throw new InvalidMetadataException("query /" + getUri() + "has invalid '" + ALLOW_DISK_USER_ELEMENT_NAME + "': " + _allowDiskUse + "; must be boolean");
        }

        this.allowDiskUse = _allowDiskUse != null ? _allowDiskUse.asBoolean() : BsonBoolean.FALSE;
    }

    /**
     * @return the stages
     */
    public BsonArray getStages() {
        return stages;
    }

    public BsonArray removeOptionalNotBoundStages(BsonDocument avars) {
        return stages;
    }

    /**
     * @return the allowDiskUse
     */
    public BsonBoolean getAllowDiskUse() {
        return allowDiskUse;
    }
}
