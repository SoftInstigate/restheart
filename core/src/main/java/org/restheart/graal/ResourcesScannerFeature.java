/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.graal;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

/**
 * GraalVM Feature that scans the classpath at build time to discover
 * resource directories and their files. It registers each individual
 * file as a resource and also generates a directory-index resource
 * that {@code ResourcesExtractor} can read at runtime to enumerate
 * files under a directory path.
 */
public class ResourcesScannerFeature implements Feature {

    private static final String INDEX_RESOURCE = "META-INF/native-image-resources.properties";

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        var directoryIndex = new HashMap<String, StringBuilder>();

        access.getApplicationClassPath().forEach(entry -> {
            try {
                if (Files.isDirectory(entry)) {
                    scanDirectory(entry, directoryIndex);
                } else if (entry.toString().endsWith(".jar")) {
                    scanJar(entry, directoryIndex);
                }
            } catch (Exception e) {
                // ignore errors for individual entries
            }
        });

        // Write the directory index as a resource.
        //
        // This used to round-trip through a temp file — write the string, read it
        // straight back as bytes, delete it. The file bought nothing, and on the
        // exception path it was never deleted; handing the bytes over directly is
        // both simpler and one less file in a shared temp directory.
        if (!directoryIndex.isEmpty()) {
            var sb = new StringBuilder();
            directoryIndex.forEach((dir, files) -> sb.append(dir).append("=").append(files).append("\n"));
            RuntimeResourceAccess.addResource(ResourcesScannerFeature.class.getModule(), INDEX_RESOURCE,
                    sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void scanDirectory(Path root, HashMap<String, StringBuilder> directoryIndex) throws IOException {
        if (!Files.exists(root)) return;

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relative = root.relativize(file);
                String path = relative.toString().replace('\\', '/');
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {
                    String dir = path.substring(0, lastSlash);
                    String fileName = path.substring(lastSlash + 1);
                    addToIndex(directoryIndex, dir, fileName);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void scanJar(Path jarPath, HashMap<String, StringBuilder> directoryIndex) throws IOException {
        Map<String, String> env = Collections.singletonMap("create", "false");
        URI uri = URI.create("jar:" + jarPath.toUri());

        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Path root = fs.getPath("/");
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String path = file.toString().replace('\\', '/');
                    if (path.startsWith("/")) path = path.substring(1);
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String dir = path.substring(0, lastSlash);
                        String fileName = path.substring(lastSlash + 1);
                        addToIndex(directoryIndex, dir, fileName);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void addToIndex(HashMap<String, StringBuilder> directoryIndex, String dir, String fileName) {
        directoryIndex.computeIfAbsent(dir, k -> new StringBuilder()).append(fileName).append(",");
    }

    @Override
    public String getDescription() {
        return "Scans classpath resources at build time for native image directory enumeration";
    }
}
