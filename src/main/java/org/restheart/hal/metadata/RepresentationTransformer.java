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
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.script.Bindings;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.types.Code;
import org.restheart.hal.Representation;
import org.restheart.handlers.RequestContext;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(RepresentationTransformer.class);

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
     * @param code
     */
    public RepresentationTransformer(PHASE phase, SCOPE scope, Code code) {
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

    public static List<RepresentationTransformer> getFromJson(DBObject collProps, boolean checkCode) throws InvalidMetadataException {
        ArrayList<RepresentationTransformer> ret = new ArrayList<>();

        Object _rtls = collProps.get(RTLS_ELEMENT_NAME);

        if (_rtls == null) {
            return ret;
        }

        if (!(_rtls instanceof BasicDBList)) {
            throw new InvalidMetadataException("element '" + RTLS_ELEMENT_NAME + "' is not an array list." + _rtls);
        }

        BasicDBList rtls = (BasicDBList) _rtls;

        for (Object _rtl : rtls.toArray()) {
            if (!(_rtl instanceof DBObject)) {
                throw new InvalidMetadataException("elements of '" + RTLS_ELEMENT_NAME + "' array, must be json objects");
            } else {
                if (checkCode) {
                    checkCodeFromJson((DBObject) _rtl);
                }

                ret.add(getRelFromJson((DBObject) _rtl));
            }
        }

        return ret;
    }

    private static void checkCodeFromJson(DBObject content) throws InvalidMetadataException {
        Object _code = content.get(RTL_CODE_ELEMENT_NAME);

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
    }

    private static RepresentationTransformer getRelFromJson(DBObject content) throws InvalidMetadataException {
        Object _phase = content.get(RTL_PHASE_ELEMENT_NAME);
        Object _scope = content.get(RTL_SCOPE_ELEMENT_NAME);
        Object _code = content.get(RTL_CODE_ELEMENT_NAME);

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
            throw new InvalidMetadataException((_code == null ? "missing '" : "invalid '") + RTL_CODE_ELEMENT_NAME + "' element.");
        }

        Code code = (Code) _code;

        return new RepresentationTransformer(phase, scope, code);
    }

    private static Bindings getTestBindings() {
        Bindings testBindings = new SimpleBindings();

        // bind the LOGGER
        testBindings.put("$LOGGER", LOGGER);
        
        testBindings.put("$user", "user");
        testBindings.put("$userRoles", new String[0]);
        testBindings.put("$resourceType", RequestContext.TYPE.DOCUMENT.name());

        // bind the content json
        testBindings.put("$content", new BasicDBObject());
        testBindings.put("$responseContent", new Representation("#").asDBObject());

        testBindings.put("$dateTime", new Date().toString());
        testBindings.put("$localIp", "127.0.0.1");
        testBindings.put("$localPort", "8080");
        testBindings.put("$localServerName", "localServerName");
        testBindings.put("$queryString", "queryString");
        testBindings.put("$relativePath", "/test/test");
        testBindings.put("$remoteIp", "remoteIp");
        // TODO add more headers
        testBindings.put("$etag", "etag");
        testBindings.put("$remoteUser", "remoteUser");
        testBindings.put("$requestList", "requestList");
        testBindings.put("$requestMethod", "GET");
        testBindings.put("$requestProtocol", "http");
        testBindings.put("$requestURL", "http://127.0.0.1:8080/test/test");
        testBindings.put("$responseCode", "200");
        // TODO add more headers
        testBindings.put("$location", "/test/test/dsfs");

        // bing usefull objects
        testBindings.put("$timestamp", new org.bson.types.BSONTimestamp());
        testBindings.put("$currentDate", new Date());

        return testBindings;
    }
}
