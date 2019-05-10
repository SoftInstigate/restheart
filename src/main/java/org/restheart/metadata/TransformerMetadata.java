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
package org.restheart.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.metadata.InvalidMetadataException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class TransformerMetadata {

    public static final String RTS_ELEMENT_NAME = "rts";

    public static final String RT_PHASE_ELEMENT_NAME = "phase";
    public static final String RT_NAME_ELEMENT_NAME = "name";
    public static final String RT_SCOPE_ELEMENT_NAME = "scope";
    public static final String RT_ARGS_ELEMENT_NAME = "args";

    public static List<TransformerMetadata> getFromJson(BsonDocument props) throws InvalidMetadataException {
        BsonValue _rts = props.get(RTS_ELEMENT_NAME);

        if (_rts == null || !_rts.isArray()) {
            throw new InvalidMetadataException((_rts == null ? "missing '" : "invalid '") + RTS_ELEMENT_NAME + "' element; it must be an array");
        }

        BsonArray rts = _rts.asArray();

        List<TransformerMetadata> ret = new ArrayList<>();

        for (BsonValue o : rts.getValues()) {
            if (o.isDocument()) {
                ret.add(getSingleFromJson(o.asDocument()));
            } else {
                throw new InvalidMetadataException("invalid '" + RTS_ELEMENT_NAME + "'. Array elements must be json objects");
            }
        }

        return ret;
    }

    private static TransformerMetadata getSingleFromJson(BsonDocument props) throws InvalidMetadataException {
        BsonValue _phase = props.get(RT_PHASE_ELEMENT_NAME);
        BsonValue _scope = props.get(RT_SCOPE_ELEMENT_NAME);
        BsonValue _name = props.get(RT_NAME_ELEMENT_NAME);
        BsonValue _args = props.get(RT_ARGS_ELEMENT_NAME);
        if (_phase == null || !_phase.isString()) {
            throw new InvalidMetadataException((_phase == null ? "missing '" : "invalid '") + RT_PHASE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(PHASE.values()));
        }
        PHASE phase;
        try {
            phase = PHASE.valueOf(_phase.asString().getValue());
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("invalid '" + RT_PHASE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(PHASE.values()));
        }

        SCOPE scope = null;

        if (phase == PHASE.RESPONSE) {
            if (_scope == null || !_scope.isString()) {
                throw new InvalidMetadataException("invalid '" + RT_SCOPE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(SCOPE.values()));
            }

            try {
                scope = SCOPE.valueOf(_scope.asString().getValue());
            } catch (IllegalArgumentException iae) {
                throw new InvalidMetadataException("invalid '" + RT_SCOPE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(SCOPE.values()));
            }
        }

        if (_name == null || !_name.isString()) {
            throw new InvalidMetadataException((_name == null ? "missing '" : "invalid '") + RT_NAME_ELEMENT_NAME + "' element");
        }
        String name = _name.asString().getValue();

        return new TransformerMetadata(phase, scope, name, _args);
    }

    private final String name;
    private final PHASE phase;
    private final SCOPE scope;
    private final BsonValue args;

    /**
     *
     * @param phase
     * @param scope
     * @param name the name of the transfromer as specified in the yml
     * configuration file
     * @param args
     */
    public TransformerMetadata(PHASE phase, SCOPE scope, String name, BsonValue args) {
        this.phase = phase;
        this.scope = scope;
        this.name = name;
        this.args = args;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the phase
     */
    public PHASE getPhase() {
        return phase;
    }

    /**
     * @return the scope
     */
    public SCOPE getScope() {
        return scope;
    }

    /**
     * @return the args
     */
    public BsonValue getArgs() {
        return args;
    }

    public enum PHASE {
        REQUEST, RESPONSE
    }

    public enum SCOPE {
        THIS, CHILDREN
    }
}
