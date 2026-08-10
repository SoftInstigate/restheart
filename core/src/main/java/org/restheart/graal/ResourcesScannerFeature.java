/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
import java.net.URISyntaxException;
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
import org.restheart.utils.ResourcesExtractor;

/**
 * GraalVM Feature that scans the classpath at build time to discover
 * resource directories and their files. This information is stored in
 * {@link ResourcesExtractor} so that at runtime, embedded static resources
 * can be extracted even though {@code ClassLoader.getResources()} does
 * not work for directories in native images.
 */
public class ResourcesScannerFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // Scan the classpath for resource directories
        access.getApplicationClassPath().forEach(entry -> {
            try {
                if (Files.isDirectory(entry)) {
                    scanDirectory(entry);
                } else if (entry.toString().endsWith(".jar")) {
                    scanJar(entry);
                }
            } catch (Exception e) {
                // ignore errors for individual entries
            }
        });
    }

    private void scanDirectory(Path root) throws IOException {
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
                    ResourcesExtractor.registerNativeImageResource(dir, fileName);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void scanJar(Path jarPath) throws IOException, URISyntaxException {
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
                        ResourcesExtractor.registerNativeImageResource(dir, fileName);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Override
    public String getDescription() {
        return "Scans classpath resources at build time for native image directory enumeration";
    }
}
