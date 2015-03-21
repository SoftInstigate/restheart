/*
 * RESTHeart - the data REST API server
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

import com.mongodb.BasicDBObject;
import java.util.Date;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.restheart.hal.Representation;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class ScriptMetadata {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptMetadata.class);
    
    // "--no-java", is very important! otherwise js can use every Java class including calling java.lang.System.exit();
    protected static final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(new String[]{"--no-java"});

    protected final CompiledScript script;

    public ScriptMetadata(String script) throws ScriptException {
        this.script = ((Compilable) engine).compile(script);
    }

    public Object evaluate(Bindings bindings) throws ScriptException {
        Object ret = evaluateInNewScope(script, bindings);
        return ret;
    }
    
    public static Object evaluate(String script, Bindings bindings) throws ScriptException {
        return engine.eval(script, bindings);
    }

    // ref https://blogs.oracle.com/nashorn/entry/improving_nashorn_startup_time_using 
    private Object evaluateInNewScope(CompiledScript script, Bindings bindings) throws ScriptException {
        ScriptContext context = new SimpleScriptContext();
        context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        
        return script.eval(context);
    }
    
    static Bindings getTestBindings() {
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
