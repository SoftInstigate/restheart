/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.hal.metadata;

import java.util.ArrayList;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.utils.JsonUtils;

/**
 * represents a map reduce.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AggregationPipeline extends AbstractAggregationOperation {
    private final BsonArray stages;

    public static final String STAGES_ELEMENT_NAME = "stages";

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
     * aggregations: [
     * {
     *   "type":"pipeline",
     *   "uri":"test_ap",
     *   "stages":
     *     [
     *       {"_$match": { "name": { "_$exists": true}}},
     *       {"_$group": { "_id": "$name", "avg_age": {"_$avg": "$age"} }}
     *     ]
     * }]
     * </code>
     * @throws org.restheart.hal.metadata.InvalidMetadataException
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
    }

    /**
     * @return the stages
     */
    public BsonArray getStages() {
        return stages;
    }

    /**
     * @param vars RequestContext.getAggregationVars()
     * @return the stages, with unescaped operators and bound variables
     * @throws org.restheart.hal.metadata.InvalidMetadataException
     * @throws org.restheart.hal.metadata.QueryVariableNotBoundException
     */
    public List<BsonDocument> getResolvedStagesAsList(BsonDocument vars)
            throws InvalidMetadataException, QueryVariableNotBoundException {
        BsonArray replacedStages = bindAggregationVariables(
                JsonUtils.unescapeKeys(stages), vars)
                .asArray();

        List<BsonDocument> ret = new ArrayList<>();

        replacedStages.stream().filter((stage) -> (stage.isDocument()))
                .forEach((stage) -> {
                    ret.add(stage.asDocument());
                });
        
        return ret;
    }
}
