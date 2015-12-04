/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart;

import com.sun.akuma.CLibrary;
import org.restheart.utils.FileUtils;
import org.restheart.utils.OSChecker;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Shutdowner {
    private static final Logger LOGGER = LoggerFactory.getLogger(Shutdowner.class);

    public static void main(final String[] args) {
        if (askingForHelp(args)) {
            LOGGER.info("usage: java -cp restheart.jar org.restheart.Shutdowner [configuration file].");
            LOGGER.info("shutdown --help\t\tprints this help message and exits.");
            LOGGER.info("shutdown\t\t\tshutdown the RESTHeart instance run without specifying the configuration file.");
            LOGGER.info("shutdown restheart.yml\tshutdown the RESTHeart instance run with the restheart.yml configuration file.");
            LOGGER.info("NOTE: shutdown is not supported on windows.");
            System.exit(0);
        }

        if (OSChecker.isWindows()) {
            LOGGER.error("shutdown is not supported on windows.");
            System.exit(-5);
        }
        
        shutdown(args);
    }
    
    protected static void shutdown(final String[] args) {
        if (FileUtils.getConfigurationFilePath(args) == null) {
            LOGGER.info("Shutting down the RESTHeart instance run without configuration file");
        } else {
            LOGGER.info("Shutting down the RESTHeart instance run with configuration file {}", FileUtils.getConfigurationFilePath(args).toString());
        }
        
        Path pidFilePath = FileUtils.getPidFilePath(FileUtils.getFileAbsoultePathHash(FileUtils.getConfigurationFilePath(args)));
            
        int pid = FileUtils.getPidFromFile(pidFilePath);

        if (pid < 0) {
            LOGGER.warn("RESTHeart instance pid file not found. Is it actually running?");
            LOGGER.info("Eventually you need to stop it using your OS tools.");
            System.exit(-1);
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
}