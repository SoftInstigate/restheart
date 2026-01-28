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
package org.restheart.utils;

/**
 * Constants enumerating the HTTP status codes with their corresponding reason phrases.
 * This class provides comprehensive coverage of HTTP status codes defined in multiple RFCs:
 * 
 * <ul>
 * <li>RFC 1945 (HTTP/1.0)</li>
 * <li>RFC 2616 (HTTP/1.1)</li>
 * <li>RFC 2518 (WebDAV)</li>
 * <li>RFC 6585 (Additional HTTP Status Codes)</li>
 * </ul>
 * 
 * <p>Each status code is defined as a public static final integer constant with the
 * naming convention SC_[STATUS_NAME]. The class also provides a lookup mechanism
 * to retrieve the standard English reason phrase for any supported status code.</p>
 * 
 * <p>The status codes are organized into the standard HTTP categories:</p>
 * <ul>
 * <li><strong>1xx Informational:</strong> Request received, continuing process</li>
 * <li><strong>2xx Success:</strong> The action was successfully received, understood, and accepted</li>
 * <li><strong>3xx Redirection:</strong> Further action must be taken to complete the request</li>
 * <li><strong>4xx Client Error:</strong> The request contains bad syntax or cannot be fulfilled</li>
 * <li><strong>5xx Server Error:</strong> The server failed to fulfill an apparently valid request</li>
 * </ul>
 *
 * @author Unascribed
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @version $Id: HttpStatus.java 155418 2005-02-26 13:01:52Z dirkv $
 */
public class HttpStatus {

    // -------------------------------------------------------- Class Variables
    /**
     * Reason phrases lookup table.
     */
    private static final String[][] REASON_PHRASES = new String[][]{
        new String[0],
        new String[3],
        new String[8],
        new String[8],
        new String[30],
        new String[8]
    };

    // -------------------------------------------------------------- Constants
    // --- 1xx Informational ---
    /**
     * {@literal <tt>}100 Continue{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_CONTINUE = 100;
    /**
     * {@literal <tt>}101 Switching Protocols{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_SWITCHING_PROTOCOLS = 101;
    /**
     * {@literal <tt>}102 Processing{@literal <tt/>} (WebDAV - RFC 2518)
     */
    public static final int SC_PROCESSING = 102;

    // --- 2xx Success ---
    /**
     * {@literal <tt>}200 OK{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_OK = 200;
    /**
     * {@literal <tt>}201 Created{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_CREATED = 201;
    /**
     * {@literal <tt>}202 Accepted{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_ACCEPTED = 202;
    /**
     * {@literal <tt>}203 Non Authoritative Information{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;
    /**
     * {@literal <tt>}204 No Content{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_NO_CONTENT = 204;
    /**
     * {@literal <tt>}205 Reset Content{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_RESET_CONTENT = 205;
    /**
     * {@literal <tt>}206 Partial Content{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_PARTIAL_CONTENT = 206;
    /**
     * {@literal <tt>}207 Multi-Status{@literal <tt/>} (WebDAV - RFC 2518) or {@literal <tt>}207 Partial Update
     * OK{@literal <tt/>} (HTTP/1.1 - draft-ietf-http-v11-spec-rev-01?)
     */
    public static final int SC_MULTI_STATUS = 207;

    // --- 3xx Redirection ---
    /**
     * {@literal <tt>}300 Mutliple Choices{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_MULTIPLE_CHOICES = 300;
    /**
     * {@literal <tt>}301 Moved Permanently{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_MOVED_PERMANENTLY = 301;
    /**
     * {@literal <tt>}302 Moved Temporarily{@literal <tt/>} (Sometimes {@literal <tt>}Found{@literal <tt/>}) (HTTP/1.0 - RFC
     * 1945)
     */
    public static final int SC_MOVED_TEMPORARILY = 302;
    /**
     * {@literal <tt>}303 See Other{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_SEE_OTHER = 303;
    /**
     * {@literal <tt>}304 Not Modified{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_NOT_MODIFIED = 304;
    /**
     * {@literal <tt>}305 Use Proxy{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_USE_PROXY = 305;
    /**
     * {@literal <tt>}307 Temporary Redirect{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_TEMPORARY_REDIRECT = 307;

    // --- 4xx Client Error ---
    /**
     * {@literal <tt>}400 Bad Request{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_BAD_REQUEST = 400;
    /**
     * {@literal <tt>}401 Unauthorized{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_UNAUTHORIZED = 401;
    /**
     * {@literal <tt>}402 Payment Required{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_PAYMENT_REQUIRED = 402;
    /**
     * {@literal <tt>}403 Forbidden{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_FORBIDDEN = 403;
    /**
     * {@literal <tt>}404 Not Found{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_NOT_FOUND = 404;
    /**
     * {@literal <tt>}405 Method Not Allowed{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_METHOD_NOT_ALLOWED = 405;
    /**
     * {@literal <tt>}406 Not Acceptable{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_NOT_ACCEPTABLE = 406;
    /**
     * {@literal <tt>}407 Proxy Authentication Required{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;
    /**
     * {@literal <tt>}408 Request Timeout{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_REQUEST_TIMEOUT = 408;
    /**
     * {@literal <tt>}409 Conflict{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_CONFLICT = 409;
    /**
     * {@literal <tt>}410 Gone{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_GONE = 410;
    /**
     * {@literal <tt>}411 Length Required{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_LENGTH_REQUIRED = 411;
    /**
     * {@literal <tt>}412 Precondition Failed{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_PRECONDITION_FAILED = 412;
    /**
     * {@literal <tt>}413 Request Entity Too Large{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_REQUEST_TOO_LONG = 413;
    /**
     * {@literal <tt>}414 Request-URI Too Long{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_REQUEST_URI_TOO_LONG = 414;
    /**
     * {@literal <tt>}415 Unsupported Media Type{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;
    /**
     * {@literal <tt>}416 Requested Range Not Satisfiable{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    /**
     * {@literal <tt>}417 Expectation Failed{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_EXPECTATION_FAILED = 417;

    /**
     * Static constant for a 418 error.
     * {@literal <tt>}418 Unprocessable Entity{@literal <tt/>} (WebDAV drafts?) or {@literal <tt>}418
     * Reauthentication Required{@literal <tt/>} (HTTP/1.1 drafts?)
     */
    // not used
    // public static final int SC_UNPROCESSABLE_ENTITY = 418;
    /**
     * Static constant for a 419 error.
     * {@literal <tt>}419 Insufficient Space on Resource{@literal <tt/>}
     * (WebDAV - draft-ietf-webdav-protocol-05?) or {@literal <tt>}419 Proxy
     * Reauthentication Required{@literal <tt/>}
     * (HTTP/1.1 drafts?)
     */
    public static final int SC_INSUFFICIENT_SPACE_ON_RESOURCE = 419;
    /**
     * Static constant for a 420 error.
     * {@literal <tt>}420 Method Failure{@literal <tt/>}
     * (WebDAV - draft-ietf-webdav-protocol-05?)
     */
    public static final int SC_METHOD_FAILURE = 420;
    /**
     * {@literal <tt>}422 Unprocessable Entity{@literal <tt/>} (WebDAV - RFC 2518)
     */
    public static final int SC_UNPROCESSABLE_ENTITY = 422;
    /**
     * {@literal <tt>}423 Locked{@literal <tt/>} (WebDAV - RFC 2518)
     */
    public static final int SC_LOCKED = 423;
    /**
     * {@literal <tt>}424 Failed Dependency{@literal <tt/>} (WebDAV - RFC 2518)
     */
    public static final int SC_FAILED_DEPENDENCY = 424;
    /**
     * {@literal <tt>}429 Too Many Requests{@literal <tt/>} (Additional HTTP Status Codes - RFC 6585)
     */
    public static final int SC_TOO_MANY_REQUESTS = 429;


    // --- 5xx Server Error ---
    /**
     * {@literal <tt>}500 Server Error{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_INTERNAL_SERVER_ERROR = 500;
    /**
     * {@literal <tt>}501 Not Implemented{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_NOT_IMPLEMENTED = 501;
    /**
     * {@literal <tt>}502 Bad Gateway{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_BAD_GATEWAY = 502;
    /**
     * {@literal <tt>}503 Service Unavailable{@literal <tt/>} (HTTP/1.0 - RFC 1945)
     */
    public static final int SC_SERVICE_UNAVAILABLE = 503;
    /**
     * {@literal <tt>}504 Gateway Timeout{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_GATEWAY_TIMEOUT = 504;
    /**
     * {@literal <tt>}505 HTTP Version Not Supported{@literal <tt/>} (HTTP/1.1 - RFC 2616)
     */
    public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;

    /**
     * {@literal <tt>}507 Insufficient Storage{@literal <tt/>} (WebDAV - RFC 2518)
     */
    public static final int SC_INSUFFICIENT_STORAGE = 507;

    // ----------------------------------------------------- Static Initializer
    /**
     * Set up status code to "reason phrase" map.
     */
    static {
        // HTTP 1.0 Server status codes -- see RFC 1945
        addStatusCodeMap(SC_OK, "OK");
        addStatusCodeMap(SC_CREATED, "Created");
        addStatusCodeMap(SC_ACCEPTED, "Accepted");
        addStatusCodeMap(SC_NO_CONTENT, "No Content");
        addStatusCodeMap(SC_MOVED_PERMANENTLY, "Moved Permanently");
        addStatusCodeMap(SC_MOVED_TEMPORARILY, "Moved Temporarily");
        addStatusCodeMap(SC_NOT_MODIFIED, "Not Modified");
        addStatusCodeMap(SC_BAD_REQUEST, "Bad Request");
        addStatusCodeMap(SC_UNAUTHORIZED, "Unauthorized");
        addStatusCodeMap(SC_FORBIDDEN, "Forbidden");
        addStatusCodeMap(SC_NOT_FOUND, "Not Found");
        addStatusCodeMap(SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        addStatusCodeMap(SC_NOT_IMPLEMENTED, "Not Implemented");
        addStatusCodeMap(SC_BAD_GATEWAY, "Bad Gateway");
        addStatusCodeMap(SC_SERVICE_UNAVAILABLE, "Service Unavailable");

        // HTTP 1.1 Server status codes -- see RFC 2048
        addStatusCodeMap(SC_CONTINUE, "Continue");
        addStatusCodeMap(SC_TEMPORARY_REDIRECT, "Temporary Redirect");
        addStatusCodeMap(SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
        addStatusCodeMap(SC_CONFLICT, "Conflict");
        addStatusCodeMap(SC_PRECONDITION_FAILED, "Precondition Failed");
        addStatusCodeMap(SC_REQUEST_TOO_LONG, "Request Too Long");
        addStatusCodeMap(SC_REQUEST_URI_TOO_LONG, "Request-URI Too Long");
        addStatusCodeMap(SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type");
        addStatusCodeMap(SC_MULTIPLE_CHOICES, "Multiple Choices");
        addStatusCodeMap(SC_SEE_OTHER, "See Other");
        addStatusCodeMap(SC_USE_PROXY, "Use Proxy");
        addStatusCodeMap(SC_PAYMENT_REQUIRED, "Payment Required");
        addStatusCodeMap(SC_NOT_ACCEPTABLE, "Not Acceptable");
        addStatusCodeMap(SC_PROXY_AUTHENTICATION_REQUIRED, "Proxy Authentication Required");
        addStatusCodeMap(SC_REQUEST_TIMEOUT, "Request Timeout");

        addStatusCodeMap(SC_SWITCHING_PROTOCOLS, "Switching Protocols");
        addStatusCodeMap(SC_NON_AUTHORITATIVE_INFORMATION, "Non Authoritative Information");
        addStatusCodeMap(SC_RESET_CONTENT, "Reset Content");
        addStatusCodeMap(SC_PARTIAL_CONTENT, "Partial Content");
        addStatusCodeMap(SC_GATEWAY_TIMEOUT, "Gateway Timeout");
        addStatusCodeMap(SC_HTTP_VERSION_NOT_SUPPORTED, "Http Version Not Supported");
        addStatusCodeMap(SC_GONE, "Gone");
        addStatusCodeMap(SC_LENGTH_REQUIRED, "Length Required");
        addStatusCodeMap(SC_REQUESTED_RANGE_NOT_SATISFIABLE, "Requested Range Not Satisfiable");
        addStatusCodeMap(SC_EXPECTATION_FAILED, "Expectation Failed");

        // WebDAV Server-specific status codes
        addStatusCodeMap(SC_PROCESSING, "Processing");
        addStatusCodeMap(SC_MULTI_STATUS, "Multi-Status");
        addStatusCodeMap(SC_UNPROCESSABLE_ENTITY, "Unprocessable Entity");
        addStatusCodeMap(SC_INSUFFICIENT_SPACE_ON_RESOURCE, "Insufficient Space On Resource");
        addStatusCodeMap(SC_METHOD_FAILURE, "Method Failure");
        addStatusCodeMap(SC_LOCKED, "Locked");
        addStatusCodeMap(SC_INSUFFICIENT_STORAGE, "Insufficient Storage");
        addStatusCodeMap(SC_FAILED_DEPENDENCY, "Failed Dependency");

        // HTTP 1.1 Additional HTTP Status Codes -- see RFC 6585
        addStatusCodeMap(SC_TOO_MANY_REQUESTS, "Too Many Requests");
    }

    // --------------------------------------------------------- Public Methods
    /**
     * Get the reason phrase for a particular status code.
     *
     * This method always returns the English text as specified in the relevent
     * RFCs and is not internationalized.
     *
     * @param statusCode the numeric status code
     * @return the reason phrase associated with the given status code or null
     * if the status code is not recognized.
     */
    public static String getStatusText(int statusCode) {

        if (statusCode < 0) {
            throw new IllegalArgumentException("status code may not be negative");
        }
        int classIndex = statusCode / 100;
        int codeIndex = statusCode - classIndex * 100;
        if (classIndex < 1 || classIndex > (REASON_PHRASES.length - 1)
                || codeIndex < 0 || codeIndex > (REASON_PHRASES[classIndex].length - 1)) {
            return null;
        }
        return REASON_PHRASES[classIndex][codeIndex];
    }

    // -------------------------------------------------------- Private Methods
    /**
     * Store the given reason phrase, by status code.
     *
     * @param statusCode The status code to lookup
     * @param reasonPhrase The reason phrase for this status code
     */
    private static void addStatusCodeMap(int statusCode, String reasonPhrase) {
        int classIndex = statusCode / 100;
        REASON_PHRASES[classIndex][statusCode - classIndex * 100] = reasonPhrase;
    }

    private HttpStatus() {
    }

}
