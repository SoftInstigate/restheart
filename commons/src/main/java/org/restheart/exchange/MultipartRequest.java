/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.exchange;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.MalformedMessageException;
import io.undertow.util.MultipartParser;

/**
 * Request with a {@code multipart/form-data} body, parsed into Undertow's {@link FormData}.
 *
 * <p>The body is parsed by Undertow's {@link MultipartParser}. Parts are kept in
 * memory with bulk copies up to {@link #DEFAULT_MEMORY_THRESHOLD} bytes; larger
 * file parts are written to a temporary file with bulk writes, deleted when the
 * exchange completes. Undertow's {@code MultiPartParserDefinition} is not used:
 * its in-memory path copies one byte at a time through a synchronized
 * {@code ByteArrayOutputStream.write(int)}, and its file path costs a temporary
 * file per upload (#797).</p>
 *
 * <p>The boundary is validated per RFC 2046 before parsing. A missing or invalid
 * boundary, or a malformed or incomplete body, is a {@link BadRequestException}
 * (400).</p>
 *
 * <p>The body comes from {@link BufferedContent} when the core read it in
 * advance, otherwise from the blocking input stream: services using this
 * request are expected to be blocking, see {@link org.restheart.plugins.MultipartService}.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.15.0
 */
public class MultipartRequest extends ServiceRequest<FormData> {

    /** Parts up to this size, in bytes, are kept in memory. */
    public static final long DEFAULT_MEMORY_THRESHOLD = 1024 * 1024;

    /** RFC 2046: a boundary is 1 to 70 characters long. */
    private static final int MAX_BOUNDARY_LENGTH = 70;

    /** Same default as Undertow's form parsers. */
    private static final int MAX_VALUES = 1000;

    private static final int READ_BUFFER_SIZE = 16 * 1024;

    private final long memoryThreshold;

    protected MultipartRequest(HttpServerExchange exchange, long memoryThreshold) {
        super(exchange);
        this.memoryThreshold = memoryThreshold;
    }

    /**
     * @param exchange the exchange
     * @return a new MultipartRequest keeping parts up to {@link #DEFAULT_MEMORY_THRESHOLD} in memory
     */
    public static MultipartRequest init(HttpServerExchange exchange) {
        return new MultipartRequest(exchange, DEFAULT_MEMORY_THRESHOLD);
    }

    /**
     * @param exchange the exchange
     * @param memoryThreshold parts larger than this, in bytes, are written to a temporary file
     * @return a new MultipartRequest
     */
    public static MultipartRequest init(HttpServerExchange exchange, long memoryThreshold) {
        return new MultipartRequest(exchange, memoryThreshold);
    }

    /**
     * @param exchange the exchange
     * @return the MultipartRequest bound to the exchange
     */
    public static MultipartRequest of(HttpServerExchange exchange) {
        return of(exchange, MultipartRequest.class);
    }

    @Override
    public FormData parseContent() throws IOException, BadRequestException {
        final var exchange = getExchange();
        final var buffered = BufferedContent.get(exchange);
        final InputStream body = buffered != null ? new ByteArrayInputStream(buffered) : exchange.getInputStream();

        return parse(exchange, body);
    }

    /**
     * Parses a multipart/form-data body.
     *
     * @param exchange the exchange, for the Content-Type, the buffer pool and the cleanup of temporary files
     * @param body the request body
     * @return the parsed form data
     * @throws BadRequestException if the boundary is missing or invalid, or the body is malformed or incomplete
     * @throws IOException if reading the body or writing a temporary file fails
     */
    protected FormData parse(HttpServerExchange exchange, InputStream body) throws BadRequestException, IOException {
        final var boundary = boundary(exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE));

        if (boundary == null) {
            throw new BadRequestException("missing or invalid multipart boundary");
        }

        final var parts = new Parts(memoryThreshold);

        exchange.addExchangeCompleteListener((ex, next) -> {
            parts.deleteFiles();
            next.proceed();
        });

        final var parser = MultipartParser.beginParse(exchange.getConnection().getByteBufferPool(), parts,
                boundary.getBytes(StandardCharsets.US_ASCII), StandardCharsets.UTF_8.name());

        final var buffer = new byte[READ_BUFFER_SIZE];

        try {
            int read;
            while ((read = body.read(buffer)) != -1) {
                if (read > 0) {
                    parser.parse(ByteBuffer.wrap(buffer, 0, read));
                }
            }
        } catch (MalformedMessageException mme) {
            throw new BadRequestException("malformed multipart body", mme);
        } catch (UncheckedIOException uioe) {
            throw uioe.getCause();
        } catch (RuntimeException re) {
            // a part header that cannot be parsed
            throw new BadRequestException("malformed multipart body", re);
        }

        if (!parser.isComplete()) {
            throw new BadRequestException("incomplete multipart body");
        }

        return parts.data;
    }

    /**
     * Extracts and validates the boundary parameter of a multipart Content-Type.
     *
     * <p>The parameter is parsed here, not with {@code Headers.extractQuotedValueFromHeader},
     * which throws {@code StringIndexOutOfBoundsException} when the header ends with an
     * empty {@code boundary=}.</p>
     *
     * @param contentType a Content-Type header value, possibly null
     * @return the boundary, or null if there is not exactly one valid boundary parameter (RFC 2046)
     */
    public static String boundary(String contentType) {
        if (contentType == null) {
            return null;
        }

        String boundary = null;

        // the boundary alphabet excludes ';', so splitting on it is safe
        final var params = contentType.split(";");
        for (int i = 1; i < params.length; i++) {
            final var param = params[i];
            final int eq = param.indexOf('=');

            if (eq < 0 || !"boundary".equalsIgnoreCase(param.substring(0, eq).trim())) {
                continue;
            }

            if (boundary != null) {
                return null; // repeated boundary parameter
            }

            boundary = unquote(param.substring(eq + 1).trim());

            if (boundary == null) {
                return null;
            }
        }

        return boundary != null && isValidBoundary(boundary) ? boundary : null;
    }

    /**
     * @return the value without the surrounding double quotes, or null if the quotes are unbalanced
     */
    private static String unquote(String value) {
        if (value.isEmpty() || value.charAt(0) != '"') {
            return value;
        }

        return value.length() >= 2 && value.charAt(value.length() - 1) == '"'
                ? value.substring(1, value.length() - 1)
                : null;
    }

    /**
     * RFC 2046: 1 to 70 characters from {@code bchars}, not ending with a space.
     */
    private static boolean isValidBoundary(String boundary) {
        final int len = boundary.length();

        if (len == 0 || len > MAX_BOUNDARY_LENGTH || boundary.charAt(len - 1) == ' ') {
            return false;
        }

        for (int i = 0; i < len; i++) {
            final char c = boundary.charAt(i);
            final boolean alnum = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');

            if (!alnum && "'()+_,-./:=? ".indexOf(c) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Collects each part and adds it to {@link FormData} as Undertow does:
     * parts with a {@code filename} become file items, the others text values.
     */
    private static final class Parts implements MultipartParser.PartHandler {
        final FormData data = new FormData(MAX_VALUES);

        private final long threshold;
        private final List<Path> files = new ArrayList<>();

        private HeaderMap headers;
        private String name;
        private String fileName;
        private byte[] content = new byte[READ_BUFFER_SIZE];
        private int size;
        private Path file;
        private FileChannel channel;

        Parts(long threshold) {
            this.threshold = threshold;
        }

        @Override
        public void beginPart(HeaderMap headers) {
            this.headers = headers;
            this.name = null;
            this.fileName = null;
            this.size = 0;
            this.file = null;
            this.channel = null;

            final var disposition = headers.getFirst(Headers.CONTENT_DISPOSITION);

            if (disposition != null && disposition.startsWith("form-data")) {
                this.name = Headers.extractQuotedValueFromHeader(disposition, "name");
                this.fileName = Headers.extractQuotedValueFromHeaderWithEncoding(disposition, "filename");
            }
        }

        @Override
        public void data(ByteBuffer buffer) throws IOException {
            if (channel == null && fileName != null && (long) size + buffer.remaining() > threshold) {
                // spill: move what is in memory to a temporary file, then append to it
                file = Files.createTempFile("restheart", "upload");
                files.add(file);
                channel = FileChannel.open(file, StandardOpenOption.WRITE);
                writeFully(ByteBuffer.wrap(content, 0, size));
                size = 0;
            }

            if (channel != null) {
                writeFully(buffer);
                return;
            }

            final int n = buffer.remaining();

            if (size + n > content.length) {
                content = Arrays.copyOf(content, Math.max(content.length * 2, size + n));
            }

            buffer.get(content, size, n);
            size += n;
        }

        private void writeFully(ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }

        @Override
        public void endPart() {
            if (name == null) {
                return; // not a form-data part
            }

            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
                data.add(name, file, fileName, headers);
            } else if (fileName != null) {
                data.add(name, Arrays.copyOf(content, size), fileName, headers);
            } else {
                data.add(name, new String(content, 0, size, StandardCharsets.UTF_8), StandardCharsets.UTF_8.name(), headers);
            }
        }

        void deleteFiles() {
            for (var f : files) {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ioe) {
                    // best effort, the file is in the temporary directory
                }
            }
        }
    }
}
