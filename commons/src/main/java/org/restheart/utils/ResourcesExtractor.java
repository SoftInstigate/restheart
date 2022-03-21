/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResourcesExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(ResourcesExtractor.class);

    /**
     *
     * @param clazz the class to get the classloader from
     * @param resourcePath
     * @return
     * @throws URISyntaxException
     */
    @SuppressWarnings("rawtypes")
    public static boolean isResourceInJar(Class clazz, String resourcePath) throws URISyntaxException {
        return getClassLoader(clazz)
                .getResource(resourcePath)
                .toURI().toString()
                .startsWith("jar:");
    }

    /**
     *
     * @param clazz the class to get the classloader from
     * @param resourcePath
     * @param tempDir
     * @throws URISyntaxException
     * @throws IOException
     */
    @SuppressWarnings("rawtypes")
    public static void deleteTempDir(Class clazz, String resourcePath, File tempDir) throws URISyntaxException, IOException {
        if (isResourceInJar(clazz, resourcePath) && tempDir.exists()) {
            delete(tempDir);
        }
    }

    /**
     *
     * @param resourcePath
     * @return
     * @throws IOException
     * @throws URISyntaxException
     * @throws IllegalStateException
     */
    @SuppressWarnings("rawtypes")
    public static File extract(Class clazz, String resourcePath) throws IOException, URISyntaxException, IllegalStateException {
        //File jarFile = new File(ResourcesExtractor.class.getProtectionDomain().getCodeSource().getLocation().getPath());

        if (getClassLoader(clazz).getResource(resourcePath) == null) {
            LOG.warn("no resource to extract from path  {}", resourcePath);
            throw new IllegalStateException("no resource to extract from path " + resourcePath);
        }

        URI uri = getClassLoader(clazz).getResource(resourcePath).toURI();

        if (isResourceInJar(clazz, resourcePath)) {
            FileSystem fs = null;
            File ret = null;

            try {
                // used when run as a JAR file
                Path destinationDir = Files.createTempDirectory("restheart-");

                ret = destinationDir.toFile();

                Map<String, String> env = new HashMap<>();
                env.put("create", "false");

                fs = FileSystems.newFileSystem(uri, env);

                Path sourceDir = fs.getPath(resourcePath);

                Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        return copy(file);
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return copy(dir);
                    }

                    private FileVisitResult copy(Path fileOrDir) throws IOException {
                        if (fileOrDir.equals(sourceDir)) {
                            return FileVisitResult.CONTINUE;
                        }

                        Path destination = Paths.get(destinationDir.toString(), fileOrDir.toString().replaceAll(resourcePath + "/", ""));

                        Files.copy(fileOrDir, destination, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } finally {
                if (fs != null) {
                    fs.close();
                }
            }

            return ret;
        } else {
            // used when run from an expanded folder
            return new File(uri);
        }
    }

    private static void delete(File file) throws IOException {
        if (file.isDirectory()) {
            //directory is empty, then delete it
            if (file.list().length == 0) {
                boolean deleted = file.delete();

                if (!deleted) {
                    LOG.warn("failted to delete directory " + file.getPath());
                }
            } else {
                //list all the directory contents
                String files[] = file.list();

                for (String temp : files) {
                    //construct the file structure
                    File fileDelete = new File(file, temp);

                    //recursive delete
                    delete(fileDelete);
                }

                //check the directory again, if empty then delete it
                if (file.list().length == 0) {
                    boolean deleted = file.delete();

                    if (!deleted) {
                        LOG.warn("failted to delete file " + file.getPath());
                    }
                }
            }

        } else {
            //if file, then delete it
            boolean deleted = file.delete();

            if (!deleted) {
                LOG.warn("failted to delete file " + file.getPath());
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static ClassLoader getClassLoader(Class clazz) {
        return clazz.getClassLoader();
    }
}
