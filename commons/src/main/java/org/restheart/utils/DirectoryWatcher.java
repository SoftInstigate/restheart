/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2024 SoftInstigate
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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryWatcher implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryWatcher.class);

    private final WatchService watchService;
    private final Map<WatchKey, Path> keys;
    private final BiConsumer<Path, Kind<Path>> onEvent;

    public DirectoryWatcher(Path rootDir, BiConsumer<Path, Kind<Path>> onEvent) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>(); // Mapping WatchKeys to the corresponding directory
        this.onEvent = onEvent;
        registerDirectoryAndSubdirectories(rootDir);
    }

    private void registerDirectoryAndSubdirectories(Path dir) throws IOException {
        // Skip directories named "node_modules"
        if (dir.getFileName().toString().equals("node_modules")) {
            LOGGER.debug("Skipping directory: {}", dir);
            return;
        }

        // Register the directory itself
        WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);

        // Walk through and register all subdirectories except "node_modules"
        Files.walk(dir).filter(Files::isDirectory).forEach(d -> {
            try {
                if (!d.getFileName().toString().equals("node_modules")) {
                    WatchKey subKey = d.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    keys.put(subKey, d);
                }
            } catch (IOException e) {
                LambdaUtils.throwsSneakyException(e);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        while (true) {
            WatchKey key;

            try {
                key = watchService.take(); // This will block until an event occurs
            } catch (InterruptedException e) {
                return;
            }

            var dir = keys.get(key);

            if (dir == null) {
                LOGGER.debug("WatchKey not recognized!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                var kind = event.kind();

                // Context for the event is the file or directory that was affected
                var ev = (WatchEvent<Path>) event;
                var name = ev.context();
                var child = dir.resolve(name);

                if (kind.equals(ENTRY_CREATE) && Files.isDirectory(child)) {
                    LOGGER.debug("Directory created: {}", child);
                    try {
                        if (!child.getFileName().toString().equals("node_modules")) {
                            registerDirectoryAndSubdirectories(child);
                        }
                    } catch (IOException e) {
                        LambdaUtils.throwsSneakyException(e);
                    }
                    this.onEvent.accept(child, ENTRY_CREATE);
                } else if (kind.equals(ENTRY_MODIFY) && Files.isRegularFile(child)) {
                    LOGGER.debug("File modified: {}", child);
                    this.onEvent.accept(child, ENTRY_MODIFY);
                } else if (kind.equals(ENTRY_DELETE)) {
                    LOGGER.debug("File or directory deleted: {}", child);
                    this.onEvent.accept(child, ENTRY_DELETE);

                    // If a directory is deleted, we should stop watching it
                    if (Files.isDirectory(child)) {
                        var childKey = keys.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(child))
                                .map(Map.Entry::getKey)
                                .findFirst().orElse(null);

                        if (childKey != null) {
                            childKey.cancel();
                            keys.remove(childKey);
                        }
                    }
                }
            }

            // Reset the key -- this step is critical to receive further watch events
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key); // Remove the key if it is no longer valid
                if (keys.isEmpty()) {
                    break; // Exit if there are no more directories to watch
                }
            }
        }
    }
}
