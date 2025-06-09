/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for extracting resources from JAR files and file systems.
 * This class provides methods to extract resources from the classpath, whether they
 * are located within JAR files or in expanded directory structures. It handles
 * temporary directory creation and cleanup for JAR-based resource extraction.
 * 
 * <p>The class is particularly useful for extracting static files, configuration
 * templates, or other resources that need to be accessed as physical files
 * rather than through input streams.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResourcesExtractor {

    /** Logger instance for this class. */
    private static final Logger LOG = LoggerFactory.getLogger(ResourcesExtractor.class);

    /**
     * Determines if a resource is located within a JAR file.
     * This method checks whether the specified resource path points to a resource
     * that is packaged inside a JAR file rather than existing as a regular file
     * in the file system.
     *
     * @param clazz the class to get the classloader from
     * @param resourcePath the path to the resource to check
     * @return true if the resource is inside a JAR file, false otherwise
     * @throws URISyntaxException if the resource URI cannot be parsed
     */
    @SuppressWarnings("rawtypes")
    public static boolean isResourceInJar(Class clazz, String resourcePath) throws URISyntaxException {
        return getClassLoader(clazz)
                .getResource(resourcePath)
                .toURI().toString()
                .startsWith("jar:");
    }

    /**
     * Deletes a temporary directory if the resource was extracted from a JAR file.
     * This method provides cleanup functionality for temporary directories created
     * during resource extraction from JAR files. If the resource is not from a JAR
     * or the directory doesn't exist, no action is taken.
     *
     * @param clazz the class to get the classloader from
     * @param resourcePath the path to the resource that was extracted
     * @param tempDir the temporary directory to delete
     * @throws URISyntaxException if the resource URI cannot be parsed
     * @throws IOException if an I/O error occurs during directory deletion
     */
    @SuppressWarnings("rawtypes")
    public static void deleteTempDir(Class clazz, String resourcePath, File tempDir) throws URISyntaxException, IOException {
        if (isResourceInJar(clazz, resourcePath) && tempDir.exists()) {
            delete(tempDir);
        }
    }

    /**
     * Extracts a resource from the classpath to the file system.
     * This method handles both JAR-based resources and file system resources.
     * For JAR resources, it creates a temporary directory and extracts the entire
     * resource tree. For file system resources, it returns the existing file reference.
     * 
     * <p>When extracting from a JAR, the method creates a temporary directory and
     * recursively copies all files and subdirectories from the resource path.
     * The caller is responsible for cleaning up the temporary directory using
     * {@link #deleteTempDir(Class, String, File)}.</p>
     *
     * @param clazz the class to get the classloader from
     * @param resourcePath the path to the resource to extract
     * @return a File object pointing to the extracted resource (temporary directory for JARs)
     * @throws IOException if an I/O error occurs during extraction
     * @throws URISyntaxException if the resource URI cannot be parsed
     * @throws IllegalStateException if the specified resource path does not exist
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

                        Path destination = Paths.get(destinationDir.toString(), fileOrDir.toString().replaceAll(Pattern.quote(resourcePath) + "/", ""));

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

    /**
     * Recursively deletes a file or directory and all its contents.
     * This method handles both files and directories, recursively deleting
     * directory contents before deleting the directory itself.
     * 
     * @param file the file or directory to delete
     * @throws IOException if an I/O error occurs during deletion
     */
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

    /**
     * Gets the class loader for the specified class.
     * This helper method provides a consistent way to retrieve the class loader
     * from a given class for resource loading operations.
     * 
     * @param clazz the class to get the class loader from
     * @return the ClassLoader associated with the specified class
     */
    @SuppressWarnings("rawtypes")
    private static ClassLoader getClassLoader(Class clazz) {
        return clazz.getClassLoader();
    }
}
