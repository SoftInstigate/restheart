/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart;

import java.io.IOException;
import java.nio.file.Path;

import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
import org.restheart.utils.FileUtils;
import org.restheart.utils.OSChecker;
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
            LOGGER.error("RESTHeart instance pid file not found.");
            printHelp();
        }
    }

    protected static void shutdown(final String[] args) {
        if (FileUtils.getConfigurationFilePath(args) == null) {
            LOGGER.info("Shutting down RESTHeart instance run without configuration file");
        } else if (FileUtils.getOverrideFilePath(args) == null) {
            LOGGER.info("Shutting down RESTHeart instance run with configuration file {}", FileUtils.getConfigurationFilePath(args));
        } else {
            LOGGER.info("Shutting down RESTHeart instance run with configuration file {} and override file {}",
                    FileUtils.getConfigurationFilePath(args),
                    FileUtils.getOverrideFilePath(args));
        }

        Path pidFilePath = FileUtils.getPidFilePath(FileUtils.getFileAbsolutePathHash(
                FileUtils.getConfigurationFilePath(args),
                FileUtils.getOverrideFilePath(args)));

        int pid = FileUtils.getPidFromFile(pidFilePath);

        if (pid < 0) {
            throw new IllegalStateException("RESTHeart instance pid file not found: " + pidFilePath.toString());
        } else {
            LOGGER.info("Pid file {}", pidFilePath);
        }

        // destroy process
        ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);

        LOGGER.info("SIGTERM signal sent to RESTHeart instance with pid {} ", pid);

        Configuration conf;

        try {
            conf = FileUtils.getConfiguration(args, true);
            LOGGER.info("Check log file {}", conf.logging().logFilePath());
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
        LOGGER.info("usage: java -cp restheart.jar org.restheart.Shutdowner [configuration file] [-e properties file].");
        LOGGER.info("java -cp restheart.jar org.restheart.Shutdowner --help \u2192 prints this help message and exits.");
        LOGGER.info("java -cp restheart.jar org.restheart.Shutdowner \u2192 shutdown RESTHeart instance run without specifying the configuration file.");
        LOGGER.info(" java -cp restheart.jar org.restheart.Shutdowner restheart.yml -e default.properties \u2192 shutdown RESTHeart instance run with configuration and properties files.");
        LOGGER.info("NOTE: shutdown is not supported on windows.");
    }

    private Shutdowner() {
    }
}
