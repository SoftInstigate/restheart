/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RepresentationTransformer {
    public enum PHASE {
        REQUEST, RESPONSE
    };

    public enum SCOPE {
        THIS, CHILDREN
    };

    public static final String RTS_ELEMENT_NAME = "rts";

    public static final String RT_PHASE_ELEMENT_NAME = "phase";
    public static final String RT_NAME_ELEMENT_NAME = "name";
    public static final String RT_SCOPE_ELEMENT_NAME = "scope";
    public static final String RT_ARGS_ELEMENT_NAME = "args";

    private final String name;
    private final PHASE phase;
    private final SCOPE scope;
    private final DBObject args;

    /**
     *
     * @param phase
     * @param scope
     * @param name the name of the transfromer as specified in the yml
     * configuration file
     * @param args
     */
    public RepresentationTransformer(PHASE phase, SCOPE scope, String name, DBObject args) {
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
    public DBObject getArgs() {
        return args;
    }

    public static List<RepresentationTransformer> getFromJson(DBObject props) throws InvalidMetadataException {
        Object _rts = props.get(RTS_ELEMENT_NAME);

        if (!(_rts == null || _rts instanceof BasicDBList)) {
            throw new InvalidMetadataException((_rts == null ? "missing '" : "invalid '") + RTS_ELEMENT_NAME + "' element; it must be an array");
        }

        BasicDBList rts = (BasicDBList) _rts;

        List<RepresentationTransformer> ret = new ArrayList<>();

        for (Object o : rts) {
            if (o instanceof DBObject) {
                ret.add(getSingleFromJson((DBObject) o));
            } else {
                throw new InvalidMetadataException("invalid '" + RTS_ELEMENT_NAME + "'. Array elements must be json objects");
            }
        }

        return ret;
    }

    private static RepresentationTransformer getSingleFromJson(DBObject props) throws InvalidMetadataException {
        Object _phase = props.get(RT_PHASE_ELEMENT_NAME);
        Object _scope = props.get(RT_SCOPE_ELEMENT_NAME);
        Object _name = props.get(RT_NAME_ELEMENT_NAME);
        Object _args = props.get(RT_ARGS_ELEMENT_NAME);

        if (_phase == null || !(_phase instanceof String)) {
            throw new InvalidMetadataException((_phase == null ? "missing '" : "invalid '") + RT_PHASE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(PHASE.values()));
        }

        PHASE phase;

        try {
            phase = PHASE.valueOf((String) _phase);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("invalid '" + RT_PHASE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(PHASE.values()));
        }

        if (_scope == null || !(_scope instanceof String)) {
            throw new InvalidMetadataException((_phase == null ? "missing '" : "invalid '") + RT_SCOPE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(SCOPE.values()));
        }

        SCOPE scope;

        try {
            scope = SCOPE.valueOf((String) _scope);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("invalid '" + RT_SCOPE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(SCOPE.values()));
        }

        if (_name == null || !(_name instanceof String)) {
            throw new InvalidMetadataException((_name == null ? "missing '" : "invalid '") + RT_NAME_ELEMENT_NAME + "' element");
        }

        String name = (String) _name;

        if (_args == null || !(_args instanceof DBObject)) {
            throw new InvalidMetadataException((_args == null ? "missing '" : "invalid '") + RT_ARGS_ELEMENT_NAME + "' element. it must be an Object");
        }

        DBObject args = (DBObject) _args;

        return new RepresentationTransformer(phase, scope, name, args);
    }
}
