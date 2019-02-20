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
            throws PluginConfigurationException;

    /**
     * get the configuration args parsing the file specified in the Plugin
     * argument 'conf-file'
     */
    @SuppressWarnings("unchecked")
    public void init(Map<String, Object> arguments, String type)
            throws FileNotFoundException, PluginConfigurationException {
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
                    + "relative to the directory containing the uIAM jar file.");
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
