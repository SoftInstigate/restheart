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
package org.restheart.mongodb.handlers.aggregation;

import java.util.ArrayList;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.mongodb.metadata.InvalidMetadataException;
import org.restheart.utils.JsonUtils;

/**
 * represents a map reduce.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AggregationPipeline extends AbstractAggregationOperation {

    /**
     *
     */
    public static final String STAGES_ELEMENT_NAME = "stages";

    /**
     *
     */
    public static final String ALLOW_DISK_USER_ELEMENT_NAME = "allowDiskUse";
    
    private final BsonArray stages;
    private final BsonBoolean allowDiskUse;

    /**
     * @param properties the json properties object. It must include the
     * following properties:
     * <ul>
     * <li><code>stages</code></li>
     * </ul>
     * <strong>Note</strong> that the dollar prefixed operators in the stages
     * must be underscore escaped, e.g. "_$exits"
     * <p>
     * Example:      <code>
     *
     * aggrs: [
     * {
     *   "type":"pipeline",
     *   "uri":"test_ap",
     *   "allowDiskUse": false,
     *   "stages":
     *     [
     *       {"_$match": { "name": { "_$exists": true}}},
     *       {"_$group": { "_id": "$name", "avg_age": {"_$avg": "$age"} }}
     *     ]
     * }]
     * </code>
     * @throws org.restheart.mongodb.metadata.InvalidMetadataException
     */
    public AggregationPipeline(BsonDocument properties)
            throws InvalidMetadataException {
        super(properties);

        BsonValue _stages = properties.get(STAGES_ELEMENT_NAME);

        if (_stages == null || !_stages.isArray()) {
            throw new InvalidMetadataException("query /" + getUri()
                    + "has invalid '" + STAGES_ELEMENT_NAME
                    + "': " + _stages
                    + "; must be an array of stage objects");
        }

        // chekcs that the _stages array elements are all documents
        if (_stages.asArray().stream()
                .anyMatch(s -> !s.isDocument())) {
            throw new InvalidMetadataException("query /" + getUri()
                    + "has invalid '" + STAGES_ELEMENT_NAME
                    + "': " + _stages
                    + "; must be an array of stage objects");
        }
        
        this.stages = _stages.asArray();
        
        BsonValue _allowDiskUse = properties.get(ALLOW_DISK_USER_ELEMENT_NAME);

        if (_allowDiskUse != null && !_allowDiskUse.isBoolean()) {
            throw new InvalidMetadataException("query /" + getUri()
                    + "has invalid '" + ALLOW_DISK_USER_ELEMENT_NAME
                    + "': " + _allowDiskUse
                    + "; must be boolean");
        }

        this.allowDiskUse = _allowDiskUse != null 
                ? _allowDiskUse.asBoolean()
                : BsonBoolean.FALSE;
    }

    /**
     * @return the stages
     */
    public BsonArray getStages() {
        return stages;
    }

    /**
     * @param avars RequestContext.getAggregationVars()
     * @return the stages, with unescaped operators and bound variables
     * @throws org.restheart.mongodb.metadata.InvalidMetadataException
     * @throws org.restheart.mongodb.handlers.aggregation.QueryVariableNotBoundException
     */
    public List<BsonDocument> getResolvedStagesAsList(BsonDocument avars)
            throws InvalidMetadataException, QueryVariableNotBoundException {
        BsonArray replacedStages = bindAggregationVariables(
                JsonUtils.unescapeKeys(stages), avars)
                .asArray();

        List<BsonDocument> ret = new ArrayList<>();

        replacedStages.stream().filter((stage) -> (stage.isDocument()))
                .forEach((stage) -> {
                    ret.add(stage.asDocument());
                });
        
        return ret;
    }

    /**
     * @return the allowDiskUse
     */
    public BsonBoolean getAllowDiskUse() {
        return allowDiskUse;
    }
}
