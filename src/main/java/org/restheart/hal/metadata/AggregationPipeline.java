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

import com.google.common.collect.Lists;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.List;

/**
 * represents a map reduce.
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class AggregationPipeline extends AbstractAggregationOperation {
    private final BasicDBList stages;

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
     *   "type":"aggregate",
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
    public AggregationPipeline(DBObject properties)
            throws InvalidMetadataException {
        super(properties);

        Object _stages = properties.get(STAGES_ELEMENT_NAME);

        if (_stages == null || !(_stages instanceof BasicDBList)) {
            throw new InvalidMetadataException("query /" + getUri()
                    + "has invalid '" + STAGES_ELEMENT_NAME
                    + "': " + _stages
                    + "; must be an array of stage objects");
        }

        // chekcs that the _stages BasicDBList elements are all BasicDBObjects
        if (((BasicDBList) _stages).stream()
                .anyMatch(s -> !(s instanceof BasicDBObject))) {
            throw new InvalidMetadataException("query /" + getUri()
                    + "has invalid '" + STAGES_ELEMENT_NAME
                    + "': " + _stages
                    + "; must be an array of stage objects");
        }

        this.stages = (BasicDBList) _stages;
    }

    /**
     * @return the stages
     */
    public BasicDBList getStages() {
        return stages;
    }

    /**
     * @param vars RequestContext.getQvars()
     * @return the stages, with unescaped operators and bound variables
     * @throws org.restheart.hal.metadata.InvalidMetadataException
     * @throws org.restheart.hal.metadata.QueryVariableNotBoundException
     */
    public List<DBObject> getResolvedStagesAsList(BasicDBObject vars)
            throws InvalidMetadataException, QueryVariableNotBoundException {
        Object replacedStages = bindQueryVariables(
                replaceEscapedOperators(stages), vars);

        return Lists.newArrayList(
                ((BasicDBList) replacedStages)
                .toArray(
                        new BasicDBObject[((BasicDBList) replacedStages)
                        .size()]));
    }
}
