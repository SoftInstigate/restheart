/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The {@code resource} utility of the SDK: reads a file shipped next to the plugin, an email
 * template for instance. There is no {@code fs} in GraalJS, and this is the one file access a
 * plugin legitimately needs.
 *
 * <p>Paths are relative to the plugin directory, the one with {@code package.json}, and stay
 * inside it: {@code ../} out of it is refused.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhResource {
    private final Path root;

    /**
     * @param root the plugin directory
     */
    public RhResource(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * @param name the file, relative to the plugin directory
     * @return its content as UTF-8 text
     */
    public String text(String name) {
        try {
            return Files.readString(resolve(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read resource " + name, e);
        }
    }

    /**
     * @param name the file, relative to the plugin directory
     * @return its bytes
     */
    public byte[] bytes(String name) {
        try {
            return Files.readAllBytes(resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read resource " + name, e);
        }
    }

    /**
     * @param name the file, relative to the plugin directory
     * @return whether it exists and is a regular file inside the plugin directory
     */
    public boolean exists(String name) {
        try {
            return Files.isRegularFile(resolve(name));
        } catch (IllegalArgumentException outside) {
            return false;
        }
    }

    Path resolve(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("resource name is required");
        }

        var p = root.resolve(name).normalize();

        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("resource " + name + " is outside the plugin directory");
        }

        return p;
    }
}
