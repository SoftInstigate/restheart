/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart.utils;

import com.softinstigate.restheart.Configuration;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class FileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    private static final File DEFAULT_PID_DIR = new File("/var/run");
    private static final File DEFAULT_PID_FILE = new File("/var/run/restheart.pid");
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

    public static Configuration getConfiguration(String[] args) {
        return getConfiguration(getConfigurationFilePath(args));
    }

    public static Configuration getConfiguration(Path configurationFilePath) {
        if (configurationFilePath != null) {
            return new Configuration(configurationFilePath);
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
        if (Files.isWritable(DEFAULT_PID_DIR.toPath())) {
            return DEFAULT_PID_FILE.toPath();
        } else {
            return TMP_DIR.resolve("restheart-" + configurationFileHash + ".pid");
        }
    }

    public static void createPidFile(Path pidFile) {
        if (OSChecker.isWindows()) {
            LOGGER.warn("this method is not supported on windows.");
            throw new IllegalStateException("createPidFile() is not supported on windows.");
        }

        try {
            FileWriter fw = null;
            
            try {
                fw = new FileWriter(pidFile.toFile());
                
                fw.write(String.valueOf(LIBC.getpid()));
                fw.close();
            } finally {
                if (fw != null) {
                    fw.close();
                }
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
}