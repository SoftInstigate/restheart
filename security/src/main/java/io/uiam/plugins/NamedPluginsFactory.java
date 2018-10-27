/*
 * uIAM - the IAM for microservices
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
package io.uiam.plugins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.uiam.Bootstrapper;
import io.uiam.Configuration;
import io.uiam.cache.Cache;
import io.uiam.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class NamedPluginsFactory {

    private static final String SEPARATOR = "_@_@_";

    public static final String GROUP_KEY = "group";
    public static final String INTERFACE_KEY = "interface";
    public static final String SINGLETONS_KEY = "singletons";

    public static final String SINGLETON_NAME_KEY = "name";
    public static final String SINGLETON_CLASS_KEY = "class";
    public static final String SINGLETON_ARGS_KEY = "args";

    private static final Logger LOGGER = LoggerFactory.getLogger(NamedPluginsFactory.class);

    private static final Cache<String, Object> SINGLETONS_CACHE = CacheFactory.createLocalCache(Integer.MAX_VALUE, Cache.EXPIRE_POLICY.NEVER, -1);
    private static final Cache<String, JsonElement> ARGS_CACHE = CacheFactory.createLocalCache(Integer.MAX_VALUE, Cache.EXPIRE_POLICY.NEVER, -1);

    private static NamedPluginsFactory HOLDER;

    public static synchronized NamedPluginsFactory getInstance() {
        if (HOLDER == null) {
            HOLDER = new NamedPluginsFactory();
        }

        return HOLDER;
    }

    @SuppressWarnings("unchecked")
    private NamedPluginsFactory() {
        List<Map<String, Object>> mdNS = Bootstrapper.getConfiguration().getNamedPlugins();

        mdNS.forEach(group -> {
            Object _gname = group.get(GROUP_KEY);
            Object _ginterfaze = group.get(INTERFACE_KEY);
            Object _singletons = group.get(SINGLETONS_KEY);

            if (_gname == null || !(_gname instanceof String)) {
                throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; the property " + GROUP_KEY + " is not a String");
            }

            if (_ginterfaze == null || !(_ginterfaze instanceof String)) {
                throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; the property " + INTERFACE_KEY + " is not a String");
            }

            if (_singletons == null || !(_singletons instanceof List)) {
                throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; the property " + SINGLETONS_KEY + " is not a List");
            }

            String gName = (String) _gname;
            String ginterfaze = (String) _ginterfaze;
            List singletons = (List) _singletons;

            Class interfazeClass;

            try {
                interfazeClass = Class.forName(ginterfaze);
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; interface class " + ginterfaze + " not found", ex);
            }

            if (!interfazeClass.isInterface()) {
                throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; interface class " + INTERFACE_KEY + " is not an interface");
            }

            singletons.forEach(_entry -> {
                if (!(_entry instanceof Map)) {
                    throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; the property " + SINGLETONS_KEY + " is not a List of Maps");
                }

                Map entry = (Map) _entry;

                Object _sName = entry.get(SINGLETON_NAME_KEY);
                Object _sClazz = entry.get(SINGLETON_CLASS_KEY);

                if (_sName == null || !(_sName instanceof String)) {
                    throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; the property " + SINGLETONS_KEY + "." + SINGLETON_NAME_KEY + " is not a String");
                }

                if (_sClazz == null || !(_sClazz instanceof String)) {
                    throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; the property " + SINGLETONS_KEY + "." + INTERFACE_KEY + " is not a String");
                }

                String sName = (String) _sName;
                String sClazz = (String) _sClazz;

                Class singletonClass;
                Object singleton;

                try {
                    singletonClass = Class.forName(sClazz);

                    singleton = singletonClass
                            .getConstructor()
                            .newInstance();
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                    throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; error instantiation singleton of class " + sClazz, ex);
                }

                if (!interfazeClass.isAssignableFrom(singletonClass)) {
                    throw new IllegalArgumentException("wrong configuration for " + Configuration.NAMED_SINGLETONS_KEY + "; the singleton of class " + sClazz + " does not implements the group interface " + ginterfaze);
                }

                LOGGER.debug("added singleton {} of class {} to group {}", sName, sClazz, gName);
                SINGLETONS_CACHE.put(gName + SEPARATOR + sName, singleton);

                Object _args = entry.get(SINGLETON_ARGS_KEY);

                JsonElement args = null;
                
                Gson gson = new GsonBuilder().create();

                if (_args != null && _args instanceof Map) {
                    Map<String, String> __args = (Map) _args;
                    args = gson.toJsonTree(__args);
                }

                ARGS_CACHE.put(gName + SEPARATOR + sName, args);
            });

        });
    }

    public Object get(String group, String name) throws IllegalArgumentException {
        Optional op = SINGLETONS_CACHE.get(group + SEPARATOR + name);

        if (op == null) {
            throw new IllegalArgumentException("no singleton configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new IllegalArgumentException("no singleton configured with name: " + name);
        }
    }

    public JsonElement getArgs(String group, String name) {
        Optional<JsonElement> op = ARGS_CACHE.get(group + SEPARATOR + name);

        if (op == null || !op.isPresent()) {
            return null;
        }

        return op.get();
    }
}
