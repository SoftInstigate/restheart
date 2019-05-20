/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.restheart.security.ConfigurationException;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * An helper class that simplifies getting the Plugin args from a configuration
 * file.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
abstract public class FileConfigurablePlugin implements ConfigurablePlugin {

    private static final org.slf4j.Logger LOGGER = LoggerFactory
            .getLogger(FileConfigurablePlugin.class);

    public FileConfigurablePlugin() {
    }

    public abstract Consumer<? super Map<String, Object>> consumeConfiguration()
            throws ConfigurationException;

    /**
     * get the configuration args parsing the file specified in the Plugin
     * argument 'conf-file'
     */
    @SuppressWarnings("unchecked")
    public void init(Map<String, Object> arguments, String type)
            throws FileNotFoundException, ConfigurationException {
        InputStream is = null;
        try {
            final String confFilePath = extractConfigFilePath(arguments);
            is = new FileInputStream(new File(java.net.URLDecoder
                    .decode(confFilePath, "utf-8")));

            final Map<String, Object> conf = (Map<String, Object>) new Yaml()
                    .load(is);

            List<Map<String, Object>> confItems = extractConfArgs(conf, type);
            confItems.stream().forEach(consumeConfiguration());
        }
        catch (FileNotFoundException ex) {
            LOGGER.error("*** cannot find the file {} "
                    + "specified in the configuration.",
                    extractConfigFilePath(arguments));

            LOGGER.error("*** note that the path must be either absolute or "
                    + "relative to the directory containing the file restheart-security.jar");
            throw ex;
        }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }

        finally {
            try {
                if (is != null) {
                    is.close();
                }
            }
            catch (IOException ex) {
                LOGGER.warn("Can't close the InputStream", ex);
            }
        }
    }

    String extractConfigFilePath(Map<String, Object> arguments)
            throws IllegalArgumentException {
        if (arguments == null) {
            throw new IllegalArgumentException(
                    "missing required arguments conf-file");
        }

        Object _confFilePath = arguments.getOrDefault("conf-file", "security.yml");

        if (_confFilePath == null || !(_confFilePath instanceof String)) {
            throw new IllegalArgumentException(
                    "missing required arguments conf-file");
        }

        String confFilePath = (String) _confFilePath;
        if (!confFilePath.startsWith("/")) {
            // this is to allow specifying the configuration file path 
            // relative to the jar (also working when running from classes)
            URL location = this.getClass().getProtectionDomain()
                    .getCodeSource()
                    .getLocation();

            File locationFile = new File(location.getPath());
            confFilePath = locationFile.getParent()
                    + File.separator
                    + confFilePath;
        }
        return confFilePath;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> extractConfArgs(final Map<String, Object> conf,
            String type)
            throws IllegalArgumentException {
        Object args = conf.get(type);

        if (args == null || !(args instanceof List)) {
            throw new IllegalArgumentException(
                    "wrong configuration file format. missing mandatory '"
                    + type
                    + "' section.");
        }

        List<Map<String, Object>> users = (List<Map<String, Object>>) args;
        return users;
    }
}
