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
package org.restheart.security.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
abstract class AbstractSecurityManager {

    AbstractSecurityManager() {
    }
    
    abstract Consumer<? super Map<String, Object>> consumeConfiguration();

    final void init(Map<String, Object> arguments, String type) throws FileNotFoundException {
        final String confFilePath = extractConfigFilePath(arguments);
        final Map<String, Object> conf = (Map<String, Object>) new Yaml().load(new FileInputStream(new File(confFilePath)));

        List<Map<String, Object>> users = extractUsers(conf, type);

        users.stream().forEach(consumeConfiguration());
    }

    final String extractConfigFilePath(Map<String, Object> arguments) throws IllegalArgumentException {
        if (arguments == null) {
            throw new IllegalArgumentException("missing required arguments conf-file");
        }
        Object _confFilePath = arguments.getOrDefault("conf-file", "security.yml");
        if (_confFilePath == null || !(_confFilePath instanceof String)) {
            throw new IllegalArgumentException("missing required arguments conf-file");
        }
        String confFilePath = (String) _confFilePath;
        if (!confFilePath.startsWith("/")) {
            // this is to allow specifying the configuration file path relative to the jar (also working when running from classes)
            URL location = this.getClass().getProtectionDomain().getCodeSource().getLocation();
            File locationFile = new File(location.getPath());
            confFilePath = locationFile.getParent() + File.separator + confFilePath;
        }
        return confFilePath;
    }

    final List<Map<String, Object>> extractUsers(final Map<String, Object> conf, String type) throws IllegalArgumentException {
        Object _users = conf.get(type);
        if (_users == null || !(_users instanceof List)) {
            throw new IllegalArgumentException("wrong configuration file format. missing mandatory '" + type + "' section.");
        }
        List<Map<String, Object>> users = (List<Map<String, Object>>) _users;
        return users;
    }

}
