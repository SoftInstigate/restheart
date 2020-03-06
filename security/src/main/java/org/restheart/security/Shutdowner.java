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
package org.restheart.security;

import com.sun.akuma.CLibrary;
import java.nio.file.Path;
import org.restheart.ConfigurationException;
import org.restheart.security.utils.FileUtils;
import org.restheart.security.utils.OSChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Shutdowner {

    private static final Logger LOGGER = LoggerFactory.getLogger(Shutdowner.class);

    public static void main(final String[] args) {
        if (askingForHelp(args)) {
            printHelp();
            System.exit(0);
        }

        if (OSChecker.isWindows()) {
            LOGGER.error("shutdown is not supported on windows.");
            System.exit(-5);
        }

        try {
            shutdown(args);
        } catch (IllegalStateException ise) {
            LOGGER.error("RESTHeart Security instance pid file not found.");
            printHelp();
        }
    }

    protected static void shutdown(final String[] args) {
        if (FileUtils.getConfigurationFilePath(args) == null) {
            LOGGER.info("Shutting down RESTHeart Security instance run without configuration file");
        } else if (FileUtils.getPropertiesFilePath(args) == null) {
            LOGGER.info("Shutting down RESTHeart Security instance run with configuration file {}", 
                    FileUtils.getConfigurationFilePath(args));
        } else {
            LOGGER.info("Shutting down RESTHeart Security instance run with configuration file {} and property file {}", 
                    FileUtils.getConfigurationFilePath(args),
                    FileUtils.getPropertiesFilePath(args));
        }

        Path pidFilePath = FileUtils.getPidFilePath(FileUtils.getFileAbsolutePathHash(
                FileUtils.getConfigurationFilePath(args),
                FileUtils.getPropertiesFilePath(args)));
        
        int pid = FileUtils.getPidFromFile(pidFilePath);

        if (pid < 0) {
            throw new IllegalStateException("RESTHeart instance pid file not found: " + pidFilePath.toString());
        } else {
            LOGGER.info("Pid file {}", pidFilePath);
        }

        CLibrary.LIBC.kill(pid, 15); // 15 is SIGTERM

        LOGGER.info("SIGTERM signal sent to RESTHeart instance with pid {} ", pid);

        Configuration conf;

        try {
            conf = FileUtils.getConfiguration(args, true);
            LOGGER.info("Check log file {}", conf.getLogFilePath());
        } catch (ConfigurationException ex) {
            LOGGER.warn(ex.getMessage());
        }
    }

    private static boolean askingForHelp(final String[] args) {
        for (String arg : args) {
            if (arg.equals("--help")) {
                return true;
            }
        }

        return false;
    }

    static void printHelp() {
        LOGGER.info("usage: java -cp restheart-security.jar org.restheart.security.Shutdowner [configuration file] [-e properties file].");
        LOGGER.info("java -cp restheart-security.jar org.restheart.security.Shutdowner --help \u2192 prints this help message and exits.");
        LOGGER.info("java -cp restheart-security.jar org.restheart.security.Shutdowner \u2192 shutdown RESTHeart Security instance run without specifying the configuration file.");
        LOGGER.info(" java -cp restheart-security.jar org.restheart.security.Shutdowner restheart-security.yml -e default.properties \u2192 shutdown RESTHeart Security instance run with configuration and properties files.");
        LOGGER.info("NOTE: shutdown is not supported on windows.");
    }

    private Shutdowner() {
    }
}
