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
package org.restheart;

import org.restheart.utils.FileUtils;
import org.restheart.utils.OSChecker;
import com.sun.akuma.CLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Shutdowner {
    private static final Logger LOGGER = LoggerFactory.getLogger(Shutdowner.class);

    public static void main(final String[] args) {
        LOGGER.info("Shutdowner called...");
        
        if (askingForHelp(args)) {
            LOGGER.info("usage: java -cp restheart.jar org.restheart.Shutdowner [configuration file].");
            LOGGER.info("shutdown --help\t\tprints this help message and exits.");
            LOGGER.info("shutdown\t\t\tshutdown the RESTHeart instance run without specifying the configuration file.");
            LOGGER.info("shutdown restheart.yml\t\tshutdown the RESTHeart instance run with the restheart.yml configuration file.");
            LOGGER.info("NOTE: shutdown is not supported on windows.");
            System.exit(0);
        }

        if (OSChecker.isWindows()) {
            LOGGER.error("shutdown is not supported on windows.");
            System.exit(-5);
        }

        if (FileUtils.getConfigurationFilePath(args) == null) {
            LOGGER.info("shutting down the RESTHeart instance run without configuration file");
        } else {
            LOGGER.info("shutting down the RESTHeart instance run with configuration file {}", FileUtils.getConfigurationFilePath(args).toString());
        }
            
        int pid = FileUtils.getPidFromFile(FileUtils.getPidFilePath(FileUtils.getFileAbsoultePathHash(FileUtils.getConfigurationFilePath(args))));

        if (pid < 0) {
            LOGGER.warn("RESTHeart instance pid file not found. Is it actually running?");
            LOGGER.info("eventually you need to stop it using your OS tools.");
            System.exit(-1);
        }
        
        CLibrary.LIBC.kill(pid, 15); // 15 is SIGTERM
        
        LOGGER.info("SIGTERM signal sent to RESTHeart instance with pid {} ", pid);
        
        Configuration conf;
        
        try {
            conf = FileUtils.getConfiguration(args, true);
            LOGGER.info("you can check the log file {}", conf.getLogFilePath());
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