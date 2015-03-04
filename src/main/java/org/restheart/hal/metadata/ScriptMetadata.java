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

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class ScriptMetadata {
    // "--no-java", is very important! otherwise js can call anthing including java.lang.System.exit()0;
    private static final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(new String[]{"--no-java"});

    protected final String script;

    public ScriptMetadata(String script) {
        this.script = script;
    }

    public Object evaluate(Bindings bindings) throws ScriptException {
        Object ret = evaluateInNewScope(script, bindings);
        return ret;
    }
    
    public static Object evaluate(String script, Bindings bindings) throws ScriptException {
        return engine.eval(script, bindings);
    }

    // ref https://blogs.oracle.com/nashorn/entry/improving_nashorn_startup_time_using 
    private Object evaluateInNewScope(String script, Bindings bindings) throws ScriptException {
        ScriptContext context = new SimpleScriptContext();
        context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        
        return engine.eval(script, context);
    }
}
