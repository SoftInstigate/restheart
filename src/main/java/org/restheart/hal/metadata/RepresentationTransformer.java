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
import javax.script.ScriptException;
import org.bson.types.Code;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RepresentationTransformer extends ScriptMetadata {
    public enum PHASE {
        REQUEST, RESPONSE
    };
    
    public enum SCOPE {
        THIS, CHILDREN
    };

    public static final String RTLS_ELEMENT_NAME = "rts";

    public static final String RTL_PHASE_ELEMENT_NAME = "phase";
    public static final String RTL_CODE_ELEMENT_NAME = "code";
    public static final String RTL_SCOPE_ELEMENT_NAME = "scope";

    private final Code code;
    private final PHASE phase;
    private final SCOPE scope;

    /**
     *
     * @param phase
     * @param scope
     * @param code
     */
    public RepresentationTransformer(PHASE phase, SCOPE scope, Code code) throws ScriptException {
        super(code.getCode());
        this.phase = phase;
        this.scope = scope;
        this.code = code;
    }

    /**
     * @return the code
     */
    public Code getCode() {
        return code;
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

    public static List<RepresentationTransformer> getFromJson(DBObject props, boolean checkCode) throws InvalidMetadataException {
        ArrayList<RepresentationTransformer> ret = new ArrayList<>();

        Object _rts = props.get(RTLS_ELEMENT_NAME);

        if (_rts == null) {
            return ret;
        }

        if (!(_rts instanceof BasicDBList)) {
            throw new InvalidMetadataException("element '" + RTLS_ELEMENT_NAME + "' is not an array list." + _rts);
        }

        BasicDBList rts = (BasicDBList) _rts;

        for (Object _rt : rts.toArray()) {
            if (!(_rt instanceof DBObject)) {
                throw new InvalidMetadataException("elements of '" + RTLS_ELEMENT_NAME + "' array, must be json objects");
            } else {
                if (checkCode) {
                    checkCodeFromJson((DBObject) _rt);
                }

                ret.add(getRtFromJson((DBObject) _rt));
            }
        }

        return ret;
    }

    private static Code checkCodeFromJson(DBObject props) throws InvalidMetadataException {
        Object _code = props.get(RTL_CODE_ELEMENT_NAME);

        if (_code == null || !(_code instanceof Code)) {
            throw new InvalidMetadataException((_code == null ? "missing '" : "invalid '") + RTL_CODE_ELEMENT_NAME + "' element.");
        }

        Code code = (Code) _code;

        // check code
        try {
            evaluate(code.getCode(), getTestBindings());
        } catch (ScriptException se) {
            throw new InvalidMetadataException("invalid javascript code", se);
        }
        
        return code;
    }

    private static RepresentationTransformer getRtFromJson(DBObject props) throws InvalidMetadataException {
        Object _phase = props.get(RTL_PHASE_ELEMENT_NAME);
        Object _scope = props.get(RTL_SCOPE_ELEMENT_NAME);
        Object _code = props.get(RTL_CODE_ELEMENT_NAME);

        if (_phase == null || !(_phase instanceof String)) {
            throw new InvalidMetadataException((_phase == null ? "missing '" : "invalid '") + RTL_PHASE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(PHASE.values()));
        }
        
        if (_scope == null || !(_scope instanceof String)) {
            throw new InvalidMetadataException((_phase == null ? "missing '" : "invalid '") + RTL_SCOPE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(SCOPE.values()));
        }
        
        PHASE phase;

        try {
            phase = PHASE.valueOf((String) _phase);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("invalid '" + RTL_PHASE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(PHASE.values()));
        }

        SCOPE scope;

        try {
            scope = SCOPE.valueOf((String) _scope);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("invalid '" + RTL_SCOPE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(SCOPE.values()));
        }

        if (_code == null || !(_code instanceof Code)) {
            throw new InvalidMetadataException((_code == null ? "missing '" : "invalid '") + RTL_CODE_ELEMENT_NAME + "' element. it must be of type $code");
        }

        Code code = (Code) _code;

        try {
            return new RepresentationTransformer(phase, scope, code);
        } catch (ScriptException ex) {
            throw new InvalidMetadataException("cannot compile scrip", ex);
        }
    }
}
