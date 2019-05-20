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
package org.restheart.security.utils;

import static com.sun.akuma.CLibrary.LIBC;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.restheart.security.Configuration;
import org.restheart.security.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class FileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    private static final Path DEFAULT_PID_DIR = new File("/var/run").toPath();
    private static final Path TMP_DIR = new File(System.getProperty("java.io.tmpdir")).toPath();

    public static Path getFileAbsoultePath(String path) {
        if (path == null) {
            return null;
        }

        return FileSystems.getDefault().getPath(path).toAbsolutePath();
    }

    public static int getFileAbsoultePathHash(Path path) {
        if (path == null) {
            return 0;
        }

        return Objects.hash(path.toString());
    }
    
    public static Configuration getConfiguration(String[] args) throws ConfigurationException {
        return getConfiguration(getConfigurationFilePath(args), false);
    }

    public static Configuration getConfiguration(String[] args, boolean silent) throws ConfigurationException {
        return getConfiguration(getConfigurationFilePath(args), silent);
    }

    public static Configuration getConfiguration(Path configurationFilePath, boolean silent) throws ConfigurationException {
        if (configurationFilePath != null) {
            return new Configuration(configurationFilePath, silent);
        } else {
            return new Configuration();
        }
    }

    public static Path getConfigurationFilePath(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (!arg.equals("--fork")) {
                    return getFileAbsoultePath(arg);
                }
            }
        }

        return null;
    }

    public static Path getTmpDirPath() {
        return TMP_DIR;
    }

    public static Path getPidFilePath(int configurationFileHash) {
        if (OSChecker.isWindows()) {
            return null;
        }
        
        if (Files.isWritable(DEFAULT_PID_DIR)) {
            return DEFAULT_PID_DIR.resolve("restheart-security-" + configurationFileHash + ".pid");
        } else {
            return TMP_DIR.resolve("restheart-security-" + configurationFileHash + ".pid");
        }
    }

    public static void createPidFile(Path pidFile) {
        if (OSChecker.isWindows()) {
            LOGGER.warn("this method is not supported on windows.");
            throw new IllegalStateException("createPidFile() is not supported on windows.");
        }

        try {
            try (FileWriter fw = new FileWriter(pidFile.toFile())) {
                
                fw.write(String.valueOf(LIBC.getpid()));
                fw.close();
            }
        } catch (IOException e) {
            LOGGER.warn("error writing pid file", e);
        }
    }

    public static int getPidFromFile(Path pidFile) {
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(pidFile.toFile()))) {
                String line = br.readLine();

                return Integer.parseInt(line);
            }
        } catch(FileNotFoundException fne) {
            LOGGER.debug("pid file not found", fne);
            return -1;
        }
        catch (IOException e) {
            LOGGER.debug("error reading the pid file", e);
            return -2;
        }
        catch (NumberFormatException e) {
            LOGGER.debug("unexpected content in pid file", e);
            return -3;
        }
    }

    private FileUtils() {
    }
}