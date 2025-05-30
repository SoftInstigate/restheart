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
package org.restheart.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
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

    public static Path getFileAbsolutePath(String path) {
        if (path == null) {
            return null;
        }

        return FileSystems.getDefault().getPath(path).toAbsolutePath();
    }

    public static int getFileAbsolutePathHash(Path confFilePath, Path propFilePath) {
        if (confFilePath == null) {
            return 0;
        }

        return Objects.hash(confFilePath, propFilePath);
    }

    public static Configuration getConfiguration(String[] args) throws ConfigurationException {
        return getConfiguration(args, false);
    }

    public static Configuration getConfiguration(String[] args, boolean silent) throws ConfigurationException {
        return Configuration.Builder.build(getConfigurationFilePath(args), getOverrideFilePath(args), false, silent);
    }

    public static Path getConfigurationFilePath(String[] args) {
        if (args != null) {
            for (var arg : args) {
                if (!arg.startsWith("-")) {
                    return getFileAbsolutePath(arg);
                }
            }
        }

        return null;
    }

    public static Path getOverrideFilePath(String[] args) {
        if (args != null) {
            var _args = Arrays.asList(args);

            var opt = _args.indexOf("-o");

            return opt < 0
                    ? null
                    : _args.size() <= opt+1
                    ? null
                    : getFileAbsolutePath(_args.get(opt+1));
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
            return DEFAULT_PID_DIR.resolve("restheart-" + configurationFileHash + ".pid");
        } else {
            return TMP_DIR.resolve("restheart-" + configurationFileHash + ".pid");
        }
    }

    public static void createPidFile(Path pidFile) {
        if (OSChecker.isWindows()) {
            LOGGER.warn("this method is not supported on Windows.");
            throw new IllegalStateException("createPidFile() is not supported on Windows.");
        }
        try (FileWriter fw = new FileWriter(pidFile.toFile())) {
            fw.write(String.valueOf(ProcessHandle.current().pid()));
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
        } catch (FileNotFoundException fne) {
            LOGGER.debug("pid file not found", fne);
            return -1;
        } catch (IOException e) {
            LOGGER.debug("error reading the pid file", e);
            return -2;
        } catch (NumberFormatException e) {
            LOGGER.debug("unexpected content in pid file", e);
            return -3;
        }
    }

    public static Set<Entry<Object, Object>> findManifestInfo() {
        Set<Entry<Object, Object>> result = null;
        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
                    .getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL manifestUrl = resources.nextElement();
                Manifest manifest = new Manifest(manifestUrl.openStream());
                Attributes mainAttributes = manifest.getMainAttributes();
                String implementationTitle = mainAttributes.getValue("Implementation-Title");
                if (implementationTitle != null && implementationTitle.toLowerCase().startsWith("restheart")) {
                    result = mainAttributes.entrySet();
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
        return result;
    }

    private FileUtils() {
    }
}
