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
package org.restheart.mongodb.handlers.changestreams;

import java.util.ArrayList;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.utils.BsonUtils;

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
    public static List<ChangeStreamOperation>
            getFromJson(BsonDocument collProps)
            throws InvalidMetadataException {
        if (collProps == null) {
            return null;
        }

        ArrayList<ChangeStreamOperation> ret = new ArrayList<>();

        BsonValue _streams = collProps.get(STREAM_ELEMENT_NAME);

        if (_streams == null) {
            return ret;
        }

        if (!_streams.isArray()) {
            throw new InvalidMetadataException("element '"
                    + STREAM_ELEMENT_NAME
                    + "' is not an array list." + _streams);
        }

        BsonArray streams = _streams.asArray();

        for (BsonValue _query : streams.getValues()) {
            if (!_query.isDocument()) {
                throw new InvalidMetadataException("element '"
                        + STREAM_ELEMENT_NAME
                        + "' is not valid." + _query);
            }

            ret.add(new ChangeStreamOperation(_query.asDocument()));
        }

        return ret;
    }

    /**
     * checks if the aggregation variable start with $ this is not allowed since
     * the client would be able to modify the aggregation stages
     *
     * @param aVars RequestContext.getAggregationVars()
     */
    public static void checkAggregationVariables(BsonValue aVars) throws SecurityException {
        if (aVars == null) {
            return;
        }
        if (aVars.isDocument()) {
            BsonDocument _obj = aVars.asDocument();

            _obj.forEach((key, value) -> {
                if (key.startsWith("$")) {
                    throw new SecurityException(
                            "aggregation variables cannot include operators");
                }

                if (value.isDocument()
                        || value.isArray()) {
                    checkAggregationVariables(value);
                }
            });
        } else if (aVars.isArray()) {
            aVars.asArray().getValues().stream()
                    .filter(el -> (el.isDocument() || el.isArray()))
                    .forEachOrdered(ChangeStreamOperation::checkAggregationVariables);
        }
    }

    private final String uri;
    private final BsonArray stages;

    /**
     *
     * @param properties
     * @throws org.restheart.exchange.InvalidMetadataException
     */
    public ChangeStreamOperation(BsonDocument properties)
            throws InvalidMetadataException {
        BsonValue _uri = properties.get(URI_ELEMENT_NAME);

        if (!properties.containsKey(URI_ELEMENT_NAME)) {
            throw new InvalidMetadataException("query does not have '"
                    + URI_ELEMENT_NAME + "' property");
        }

        this.uri = _uri.asString().getValue();

        BsonValue _stages = properties.get(STAGES_ELEMENT_NAME);

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

    /**
     * @param vars RequestContext.getAggregationVars()
     * @return the stages, with unescaped operators and bound variables
     * @throws org.restheart.exchange.InvalidMetadataException
     * @throws org.restheart.exchange.QueryVariableNotBoundException
     */
    public List<BsonDocument> getResolvedStagesAsList(BsonDocument vars)
            throws InvalidMetadataException, QueryVariableNotBoundException {
        BsonArray replacedStages = bindAggregationVariables(
            BsonUtils.unescapeKeys(stages), vars).asArray();

        List<BsonDocument> ret = new ArrayList<>();

        replacedStages.stream().filter((stage) -> (stage.isDocument()))
                .forEach((stage) -> {
                    ret.add(stage.asDocument());
                });

        return ret;
    }


    /**
     * @param obj
     * @param aVars RequestContext.getAggregationVars()
     * @return the json object where the variables ({"_$var": "var") are
     * replaced with the values defined in the avars URL query parameter
     * @throws org.restheart.exchange.InvalidMetadataException
     * @throws org.restheart.exchange.QueryVariableNotBoundException
     */
    protected BsonValue bindAggregationVariables(
            BsonValue obj,
            BsonDocument aVars)
            throws InvalidMetadataException, QueryVariableNotBoundException {
        if (obj == null) {
            return null;
        }

        if (obj.isDocument()) {
            BsonDocument _obj = obj.asDocument();

            if (_obj.size() == 1 && _obj.get("$var") != null) {
                BsonValue varName = _obj.get("$var");

                if (!(varName.isString())) {
                    throw new InvalidMetadataException("wrong variable name "
                            + varName.toString());
                }

                if (aVars == null
                        || aVars.get(varName.asString().getValue()) == null) {
                    throw new QueryVariableNotBoundException("variable "
                            + varName.asString().getValue() + " not bound");
                }

                return aVars.get(varName.asString().getValue());
            } else {
                BsonDocument ret = new BsonDocument();

                for (String key : _obj.keySet()) {
                    ret.put(key,
                            bindAggregationVariables(
                                    _obj.get(key), aVars));
                }

                return ret;
            }
        } else if (obj.isArray()) {
            BsonArray ret = new BsonArray();

            for (BsonValue el : obj.asArray().getValues()) {
                if (el.isDocument()) {
                    ret.add(bindAggregationVariables(el, aVars));
                } else if (el.isArray()) {
                    ret.add(bindAggregationVariables(el, aVars));
                } else {
                    ret.add(el);
                }
            }

            return ret;

        } else {
            return obj;
        }
    }

}
