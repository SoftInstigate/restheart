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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.io.FileSystem;

/**
 * The polyglot {@link FileSystem} of a plugin context: resolves {@code restheart:sdk} to the
 * SDK module, served from memory, and delegates everything else to the default file system.
 *
 * <p>How: GraalJS has two module loaders. The default one hands a specifier with a scheme to
 * the file system as a URI, which this class maps. The npm-compatible loader, active when a
 * plugin has {@code node_modules} and so {@code js.commonjs-require}, parses the specifier as
 * a URL first and fails on an opaque one like {@code restheart:sdk} before any file system is
 * asked (a {@code NullPointerException} in {@code getAssociatedDefaultFormat}). So the
 * specifier is rewritten in the plugin's own sources as they are read, to the absolute
 * {@link #SDK_PATH}, which both loaders resolve through this file system. Files under
 * {@code node_modules} are served untouched, and a package named {@code restheart:sdk}
 * installed there is never consulted: the import never reaches package resolution.</p>
 *
 * <p>The failure of the npm-compatible loader is a GraalJS bug, reported as
 * <a href="https://github.com/oracle/graaljs/issues/1147">oracle/graaljs#1147</a>. The rewrite
 * can go once the GraalJS version RESTHeart uses has the fix, if the fix hands such a
 * specifier to the file system as the default loader does: the URI mapping of
 * {@link #parsePath(URI)} then serves both loaders. A fix that only turns the exception into
 * a guest error is not enough.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SdkFileSystem implements FileSystem {
    /** The absolute path the SDK module is served at. */
    public static final String SDK_PATH = "/__restheart__/sdk.mjs";

    private static final Pattern SPECIFIER = Pattern.compile("(from\\s*|import\\s*\\(?\\s*)([\"'])" + Pattern.quote(RhRuntime.SDK_SPECIFIER) + "\\2");
    private static final String NODE_MODULES = "node_modules";

    private final FileSystem delegate = FileSystem.newDefaultFileSystem();
    private final Path sdkPath = Path.of(SDK_PATH);
    private final Path pluginRoot;
    private final byte[] sdkSource;

    /**
     * @param pluginRoot the plugin directory: the sources under it, outside {@code node_modules}, get the rewrite
     * @param sdkSource the SDK module source for this plugin
     */
    public SdkFileSystem(Path pluginRoot, String sdkSource) {
        this.pluginRoot = pluginRoot == null ? null : pluginRoot.toAbsolutePath().normalize();
        this.sdkSource = sdkSource.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Rewrites the {@code restheart:sdk} specifier of every import to {@link #SDK_PATH}.
     *
     * @param source a module source
     * @return the source with the specifier rewritten, the same string when it has none
     */
    public static String rewrite(String source) {
        Matcher m = SPECIFIER.matcher(source);
        return m.find() ? m.replaceAll("$1$2" + SDK_PATH + "$2") : source;
    }

    private boolean isSdk(Path path) {
        return path != null && (path.equals(sdkPath) || path.endsWith(sdkPath.getFileName()) && path.getParent() != null
                && path.getParent().getFileName() != null && "__restheart__".equals(path.getParent().getFileName().toString()));
    }

    private boolean isPluginSource(Path path) {
        if (pluginRoot == null || path == null) {
            return false;
        }

        var p = path.toAbsolutePath().normalize();
        var name = p.getFileName() == null ? "" : p.getFileName().toString();

        if (!(name.endsWith(".mjs") || name.endsWith(".js")) || !p.startsWith(pluginRoot)) {
            return false;
        }

        for (var element : pluginRoot.relativize(p)) {
            if (NODE_MODULES.equals(element.toString())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Path parsePath(URI uri) {
        if ("restheart".equals(uri.getScheme()) && "sdk".equals(uri.getSchemeSpecificPart())) {
            return sdkPath;
        }
        return delegate.parsePath(uri);
    }

    @Override
    public Path parsePath(String path) {
        if (RhRuntime.SDK_SPECIFIER.equals(path)) {
            return sdkPath;
        }
        return delegate.parsePath(path);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        if (isSdk(path)) {
            if (modes.contains(AccessMode.WRITE)) {
                throw new IOException("the sdk module is read-only");
            }
            return;
        }
        delegate.checkAccess(path, modes, linkOptions);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        delegate.createDirectory(dir, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        delegate.delete(path);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        if (isSdk(path)) {
            return new BytesChannel(sdkSource);
        }

        if (isPluginSource(path)) {
            var source = Files.readString(path, StandardCharsets.UTF_8);
            var rewritten = rewrite(source);
            if (!rewritten.equals(source)) {
                return new BytesChannel(rewritten.getBytes(StandardCharsets.UTF_8));
            }
        }

        return delegate.newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return delegate.newDirectoryStream(dir, filter);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return isSdk(path) ? sdkPath : delegate.toAbsolutePath(path);
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        return isSdk(path) ? sdkPath : delegate.toRealPath(path, linkOptions);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        if (isSdk(path)) {
            var attrs = new LinkedHashMap<String, Object>();
            var epoch = FileTime.fromMillis(0);
            attrs.put("isRegularFile", true);
            attrs.put("isDirectory", false);
            attrs.put("isSymbolicLink", false);
            attrs.put("isOther", false);
            attrs.put("size", (long) sdkSource.length);
            attrs.put("lastModifiedTime", epoch);
            attrs.put("lastAccessTime", epoch);
            attrs.put("creationTime", epoch);
            return attrs;
        }
        return delegate.readAttributes(path, attributes, options);
    }

    @Override
    public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
        delegate.setCurrentWorkingDirectory(currentWorkingDirectory);
    }

    @Override
    public String getSeparator() {
        return delegate.getSeparator();
    }

    @Override
    public String getPathSeparator() {
        return delegate.getPathSeparator();
    }

    @Override
    public String getMimeType(Path path) {
        return isSdk(path) ? "application/javascript+module" : delegate.getMimeType(path);
    }

    @Override
    public Path getTempDirectory() {
        return delegate.getTempDirectory();
    }

    @Override
    public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
        if (isSdk(path1) || isSdk(path2)) {
            return isSdk(path1) && isSdk(path2);
        }
        return delegate.isSameFile(path1, path2, options);
    }

    /**
     * A read-only channel over bytes in memory.
     */
    static final class BytesChannel implements SeekableByteChannel {
        private final byte[] data;
        private long position = 0;
        private boolean open = true;

        BytesChannel(byte[] data) {
            this.data = data;
        }

        @Override
        public int read(ByteBuffer dst) {
            if (position >= data.length) {
                return -1;
            }
            var n = (int) Math.min(dst.remaining(), data.length - position);
            dst.put(data, (int) position, n);
            position += n;
            return n;
        }

        @Override
        public int write(ByteBuffer src) {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            position = newPosition;
            return this;
        }

        @Override
        public long size() {
            return data.length;
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
