/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.restheart.net;

import com.google.gson.JsonElement;
import io.undertow.client.ClientRequest;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Request {
    public enum METHOD {
        GET, POST, PUT, PATCH, DELETE, OPTIONS
    }

    private final METHOD method;
    private final URI url;
    private final HeaderMap headers;
    private final String body;

    public Request(METHOD method, String url) {
        this(method, url, new HeaderMap());
    }

    public Request(METHOD method, URI url) {
        this(method, url, new HeaderMap(), null);
    }

    public Request(METHOD method, String url, HeaderMap headers)
            throws IllegalArgumentException {
        this(method, URI.create(url), headers, null);
    }

    public Request(METHOD method, URI url, HeaderMap headers, String body)
            throws IllegalArgumentException {
        this.url = url;

        this.method = method;
        this.headers = headers;
        this.body = body;

        // always add Host header
        this.headers.remove(Headers.HOST);
        this.headers.put(Headers.HOST, getHost().concat(":" + getPort()));
    }

    public Request body(String body) {
        HeaderMap newHeaders = copy(this.headers);

        addTransferEncodingHeader(newHeaders);
        return new Request(this.method, this.url, newHeaders, body);
    }

    public Request body(JsonElement body) {
        HeaderMap newHeaders = copy(this.headers);

        addTransferEncodingHeader(newHeaders);
        addJsonContentTypeHeader(newHeaders);
        return new Request(this.method, this.url, newHeaders, body.toString());
    }

    private void addJsonContentTypeHeader(HeaderMap headers) {
        headers.remove(Headers.CONTENT_TYPE);
        headers.put(Headers.CONTENT_TYPE, "application/json");
    }

    private void addTransferEncodingHeader(HeaderMap headers) {
        headers.remove(Headers.TRANSFER_ENCODING);
        headers.put(Headers.TRANSFER_ENCODING, "chunked");
    }

    public Request parameter(String name, String value) {
        String newQuery = url.getQuery();

        if (newQuery == null) {
            newQuery = name.concat("=").concat(value);
        } else {
            newQuery += "&".concat(name).concat("=").concat(value);
        }

        try {
            var newUrl = new URI(url.getScheme(),
                    url.getAuthority(),
                    url.getPath(),
                    newQuery,
                    url.getFragment());

            return new Request(method, newUrl, headers, this.getBody());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Cannot add qparam", ex);
        }
    }

    public Request header(String name, String value) {
        this.headers.add(HttpString.tryFromString(name), value);

        var newHeaders = new HeaderMap();

        for (var h : this.headers) {
            newHeaders.add(h.getHeaderName(), h.getFirst());
        }

        return new Request(this.method, this.url, newHeaders, this.getBody());
    }

    public URI getConnectionUri() {
        return getPort() > 0
                ? URI.create(getProtocol()
                        .concat("://")
                        .concat(getHost())
                        .concat(":" + getPort()))
                : URI.create(getProtocol()
                        .concat("://")
                        .concat(getHost()));
    }

    public String getProtocol() {
        return url.getScheme();
    }

    public String getHost() {
        return url.getHost();
    }

    public int getPort() {
        return url.getPort();
    }

    public String getPath() {
        var path = url.getPath().isEmpty()
                ? "/"
                : url.getPath();

        return url.getQuery() == null
                ? path
                : path.concat("?").concat(url.getQuery());
    }

    /**
     * @return the body
     */
    public String getBody() {
        return body;
    }

    ClientRequest asClientRequest() {
        var ret = new ClientRequest()
                .setMethod(HttpString.tryFromString(method.name()))
                .setPath(getPath());

        for (var h : this.headers) {
            ret.getRequestHeaders().add(h.getHeaderName(), h.getFirst());
        }

        return ret;
    }

    private HeaderMap copy(HeaderMap headers) {
        var ret = new HeaderMap();

        for (var h : headers) {
            ret.add(h.getHeaderName(), h.getFirst());
        }

        return ret;
    }

    @Override
    public String toString() {
        return "Request{"
                + "method=" + method
                + ", url=" + url
                + ", path=" + getPath()
                + ", connectionUri=" + getConnectionUri()
                + ", headers=" + headers
                + ", body=" + body
                + "}";
    }
}
