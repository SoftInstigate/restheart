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
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.types.Code;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RepresentationTransformLogic {
    public enum PHASE {
        REQUEST, RESPONSE
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(RepresentationTransformLogic.class);

    public static final String RTLS_ELEMENT_NAME = "rtls";

    public static final String RTL_ID_ELEMENT_NAME = "_id";
    public static final String RTL_PHASE_ELEMENT_NAME = "phase";
    public static final String RTL_CODE_ELEMENT_NAME = "code";

    public static final String REPRESENTATION_TRANSFORM_LOGIC_ELEMENT_NAME = "transformers";

    //TODO need to compile scripts to boost performance http://docs.oracle.com/javase/8/docs/api/javax/script/CompiledScript.html
    // "--no-java", is very important! otherwise js can call anthing including java.lang.System.exit()0;
    private static final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(new String[] { "--no-java" });
    
    private final String id;
    private final Code code;
    private final PHASE phase;

    /**
     *
     * @param id
     * @param phase
     * @param code
     */
    public RepresentationTransformLogic(String id, PHASE phase, Code code) {
        this.id = id;
        this.phase = phase;
        this.code = code;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
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

    public void exectute(Bindings bindings) throws ScriptException {
        engine.eval(code.getCode(), bindings);
    }

    public static List<RepresentationTransformLogic> getFromJson(DBObject collProps) throws InvalidMetadataException {
        ArrayList<RepresentationTransformLogic> ret = new ArrayList<>();

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
                ret.add(getRelFromJson((DBObject) _rtl));
            }
        }

        return ret;
    }

    private static RepresentationTransformLogic getRelFromJson(DBObject content) throws InvalidMetadataException {
        Object _id = content.get(RTL_ID_ELEMENT_NAME);
        Object _phase = content.get(RTL_PHASE_ELEMENT_NAME);
        Object _code = content.get(RTL_CODE_ELEMENT_NAME);

        if (_id == null || !(_id instanceof String)) {
            throw new InvalidMetadataException((_id == null ? "missing '" : "invalid '") + RTL_ID_ELEMENT_NAME + "' element.");
        }

        String id = (String) _id;

        if (_phase == null || !(_phase instanceof String)) {
            throw new InvalidMetadataException((_phase == null ? "missing '" : "invalid '") + RTL_PHASE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(PHASE.values()));
        }

        PHASE phase;

        try {
            phase = PHASE.valueOf((String) _phase);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("invalid '" + RTL_PHASE_ELEMENT_NAME + "' element; acceptable values are: " + Arrays.toString(PHASE.values()));
        }

        if (_code == null || !(_code instanceof Code)) {
            throw new InvalidMetadataException((_code == null ? "missing '" : "invalid '") + RTL_CODE_ELEMENT_NAME + "' element.");
        }

        Code code = (Code) _code;

        // check code
        try {
            Object o = System.getSecurityManager();
            engine.eval(code.getCode(), getTestBindings());
        } catch (ScriptException se) {
            throw new InvalidMetadataException("invalid javascript code", se);
        }

        return new RepresentationTransformLogic(id, phase, code);
    }
    
    private static Bindings getTestBindings() {
        Bindings testBindings = new SimpleBindings();

        // bind the LOGGER
        testBindings.put("$LOGGER", LOGGER);
        
        // bind the content json
        
        DBObject json = new BasicDBObject();
        
        testBindings.put("$content", json);

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
        testBindings.put("$user", "user");
        
        // bing usefull objects
        testBindings.put("$timestamp", new org.bson.types.BSONTimestamp());
        testBindings.put("$currentDate", new Date());
        
        return testBindings;
    }
}
