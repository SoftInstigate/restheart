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
import org.restheart.Bootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ResourcesExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ResourcesExtractor.class);

    /**
     *
     * @param resourcePath
     * @return
     * @throws URISyntaxException
     */
    public static boolean isResourceInJar(String resourcePath) throws URISyntaxException {
        URI uri = Bootstrapper.class.getClassLoader().getResource(resourcePath).toURI();

        return uri.toString().startsWith("jar:");
    }

    /**
     *
     * @param resourcePath
     * @param tempDir
     * @throws URISyntaxException
     * @throws IOException
     */
    public static void deleteTempDir(String resourcePath, File tempDir) throws URISyntaxException, IOException {
        if (isResourceInJar(resourcePath) && tempDir.exists()) {
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
    public static File extract(String resourcePath) throws IOException, URISyntaxException, IllegalStateException {
        //File jarFile = new File(ResourcesExtractor.class.getProtectionDomain().getCodeSource().getLocation().getPath());

        if (Bootstrapper.class.getClassLoader().getResource(resourcePath) == null) {
            logger.warn("no resource to extract from path  {}", resourcePath);
            throw new IllegalStateException("no resource to extract from path " + resourcePath);
        }

        URI uri = Bootstrapper.class.getClassLoader().getResource(resourcePath).toURI();

        if (isResourceInJar(resourcePath)) {
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
                    logger.warn("failted to delete directory " + file.getPath());
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
                        logger.warn("failted to delete file " + file.getPath());
                    }
                }
            }

        } else {
            //if file, then delete it
            boolean deleted = file.delete();

            if (!deleted) {
                logger.warn("failted to delete file " + file.getPath());
            }
        }
    }

    private ResourcesExtractor() {
    }
}
