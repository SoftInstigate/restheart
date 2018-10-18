/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
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
package io.uiam.handlers;

import com.google.gson.JsonObject;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplateMatch;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import io.uiam.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestContext {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(RequestContext.class);

    // other constants
    public static final String SLASH = "/";
    public static final String PATCH = "PATCH";
    public static final String UNDERSCORE = "_";
    private static final String NUL = Character.toString('\0');

    private final METHOD method;
    private final String[] pathTokens;

    private String rawContent;

    private int responseStatusCode;

    private String responseContentType;
    private JsonObject responseContent;
    
    private String whereUri = null;
    private String whatUri = null;

    private String mappedUri = null;
    private String unmappedUri = null;

    private boolean inError = false;

    private Account authenticatedAccount = null;

    private final long requestStartTime = System.currentTimeMillis();

    // path template match
    private final PathTemplateMatch pathTemplateMatch;

    /**
     *
     * @param exchange the url rewriting feature is implemented by the whatUri
     * and whereUri parameters.
     *
     * the exchange request path (mapped uri) is rewritten replacing the
     * whereUri string with the whatUri string the special whatUri value * means
     * any resource: the whereUri is replaced with /
     *
     * example 1
     *
     * whatUri = /db/mycollection whereUri = /
     *
     * then the requestPath / is rewritten to /db/mycollection
     *
     * example 2
     *
     * whatUri = * whereUri = /data
     *
     * then the requestPath /data is rewritten to /
     *
     * @param whereUri the uri to map to
     * @param whatUri the uri to map
     */
    public RequestContext(
            HttpServerExchange exchange,
            String whereUri,
            String whatUri) {
        this.whereUri = URLUtils.removeTrailingSlashes(whereUri == null ? null
                : whereUri.startsWith("/") ? whereUri
                : "/" + whereUri);

        this.whatUri = URLUtils.removeTrailingSlashes(
                whatUri == null ? null
                        : whatUri.startsWith("/")
                        || "*".equals(whatUri) ? whatUri
                        : "/" + whatUri);

        this.mappedUri = exchange.getRequestPath();

        if (exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY) != null) {
            this.pathTemplateMatch = exchange
                    .getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        } else {
            this.pathTemplateMatch = null;
        }

        this.unmappedUri = unmapUri(exchange.getRequestPath());

        // "/db/collection/document" --> { "", "mappedDbName", "collection", "document" }
        this.pathTokens = this.unmappedUri.split(SLASH);

        this.method = selectRequestMethod(exchange.getRequestMethod());
    }
    
    static METHOD selectRequestMethod(HttpString _method) {
        METHOD method;
        if (Methods.GET.equals(_method)) {
            method = METHOD.GET;
        } else if (Methods.POST.equals(_method)) {
            method = METHOD.POST;
        } else if (Methods.PUT.equals(_method)) {
            method = METHOD.PUT;
        } else if (Methods.DELETE.equals(_method)) {
            method = METHOD.DELETE;
        } else if (PATCH.equals(_method.toString())) {
            method = METHOD.PATCH;
        } else if (Methods.OPTIONS.equals(_method)) {
            method = METHOD.OPTIONS;
        } else {
            method = METHOD.OTHER;
        }
        return method;
    }

    /**
     * given a mapped uri (/some/mapping/coll) returns the canonical uri
     * (/db/coll) URLs are mapped to mongodb resources by using the mongo-mounts
     * configuration properties. note that the mapped uri can make use of path
     * templates (/some/{path}/template/*)
     *
     * @param mappedUri
     * @return
     */
    private String unmapUri(String mappedUri) {
        if (this.pathTemplateMatch == null) {
            return unmapPathUri(mappedUri);
        } else {
            return unmapPathTemplateUri(mappedUri);
        }
    }

    private String unmapPathUri(String mappedUri) {
        String ret = URLUtils.removeTrailingSlashes(mappedUri);

        if (whatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                ret = ret.replaceFirst("^" + this.whereUri, "");
            }
        } else if (!this.whereUri.equals(SLASH)) {
            ret = URLUtils.removeTrailingSlashes(
                    ret.replaceFirst("^" + this.whereUri, this.whatUri));
        } else {
            ret = URLUtils.removeTrailingSlashes(
                    URLUtils.removeTrailingSlashes(this.whatUri) + ret);
        }

        if (ret.isEmpty()) {
            ret = SLASH;
        }

        return ret;
    }

    private String unmapPathTemplateUri(String mappedUri) {
        String ret = URLUtils.removeTrailingSlashes(mappedUri);
        String rewriteUri = replaceParamsWithActualValues();

        String replacedWhatUri = replaceParamsWithinWhatUri();
        // replace params with in whatUri
        // eg what: /{account}, where: /{account/*

        // now replace mappedUri with resolved path template
        if (replacedWhatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                ret = ret.replaceFirst("^" + rewriteUri, "");
            }
        } else if (!this.whereUri.equals(SLASH)) {
            ret = URLUtils.removeTrailingSlashes(
                    ret.replaceFirst("^" + rewriteUri, replacedWhatUri));
        } else {
            ret = URLUtils.removeTrailingSlashes(
                    URLUtils.removeTrailingSlashes(replacedWhatUri) + ret);
        }

        if (ret.isEmpty()) {
            ret = SLASH;
        }

        return ret;
    }

    /**
     * given a canonical uri (/db/coll) returns the mapped uri
     * (/some/mapping/uri) relative to this context. URLs are mapped to mongodb
     * resources via the mongo-mounts configuration properties
     *
     * @param unmappedUri
     * @return
     */
    public String mapUri(String unmappedUri) {
        if (this.pathTemplateMatch == null) {
            return mapPathUri(unmappedUri);
        } else {
            return mapPathTemplateUri(unmappedUri);
        }
    }

    private String mapPathUri(String unmappedUri) {
        String ret = URLUtils.removeTrailingSlashes(unmappedUri);

        if (whatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                return this.whereUri + unmappedUri;
            }
        } else {
            ret = URLUtils.removeTrailingSlashes(
                    ret.replaceFirst("^" + this.whatUri, this.whereUri));
        }

        if (ret.isEmpty()) {
            ret = SLASH;
        }

        return ret;
    }

    private String mapPathTemplateUri(String unmappedUri) {
        String ret = URLUtils.removeTrailingSlashes(unmappedUri);
        String rewriteUri = replaceParamsWithActualValues();
        String replacedWhatUri = replaceParamsWithinWhatUri();

        // now replace mappedUri with resolved path template
        if (replacedWhatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                return rewriteUri + unmappedUri;
            }
        } else {
            ret = URLUtils.removeTrailingSlashes(
                    ret.replaceFirst("^" + replacedWhatUri, rewriteUri));
        }

        if (ret.isEmpty()) {
            ret = SLASH;
        }

        return ret;
    }

    private String replaceParamsWithinWhatUri() {
        String uri = this.whatUri;
        // replace params within whatUri
        // eg what: /{prefix}_db, where: /{prefix}/*
        for (String key : this.pathTemplateMatch
                .getParameters().keySet()) {
            uri = uri.replace(
                    "{".concat(key).concat("}"),
                    this.pathTemplateMatch
                            .getParameters().get(key));
        }
        return uri;
    }

    private String replaceParamsWithActualValues() {
        String rewriteUri;
        // path template with variables resolved to actual values
        rewriteUri = this.pathTemplateMatch.getMatchedTemplate();
        // remove trailing wildcard from template
        if (rewriteUri.endsWith("/*")) {
            rewriteUri = rewriteUri.substring(0, rewriteUri.length() - 2);
        }
        // collect params
        this.pathTemplateMatch
                .getParameters()
                .keySet()
                .stream()
                .filter(key -> !key.equals("*"))
                .collect(Collectors.toMap(
                        key -> key,
                        key -> this.pathTemplateMatch
                                .getParameters().get(key)));
        // replace params with actual values
        for (String key : this.pathTemplateMatch
                .getParameters().keySet()) {
            rewriteUri = rewriteUri.replace(
                    "{".concat(key).concat("}"),
                    this.pathTemplateMatch
                            .getParameters().get(key));
        }
        return rewriteUri;
    }


    /**
     *
     * @return URI
     * @throws URISyntaxException
     */
    public URI getUri() throws URISyntaxException {
        return new URI(Arrays.asList(pathTokens)
                .stream()
                .reduce(SLASH, (t1, t2) -> t1 + SLASH + t2));
    }

    /**
     *
     * @return method
     */
    public METHOD getMethod() {
        return method;
    }

    /**
     * @return the whereUri
     */
    public String getUriPrefix() {
        return whereUri;
    }

    /**
     * @return the whatUri
     */
    public String getMappingUri() {
        return whatUri;
    }

    /**
     * @return the rawContent
     */
    public String getRawContent() {
        return rawContent;
    }

    /**
     * @param rawContent the rawContent to set
     */
    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    /**
     *
     * The unmapped uri is the cononical uri of a mongodb resource (e.g.
     * /db/coll).
     *
     * @return the unmappedUri
     */
    public String getUnmappedRequestUri() {
        return unmappedUri;
    }

    /**
     * The mapped uri is the exchange request uri. This is "mapped" by the
     * mongo-mounts mapping paramenters.
     *
     * @return the mappedUri
     */
    public String getMappedRequestUri() {
        return mappedUri;
    }

    /**
     * if mongo-mounts specifies a path template (i.e. /{foo}/*) this returns
     * the request template parameters (/x/y => foo=x, *=y)
     *
     * @return
     */
    public Map<String, String> getPathTemplateParamenters() {
        if (this.pathTemplateMatch == null) {
            return null;
        } else {
            return this.pathTemplateMatch.getParameters();
        }
    }

    /**
     * @return the responseStatusCode
     */
    public int getResponseStatusCode() {
        return responseStatusCode;
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setResponseStatusCode(int responseStatusCode) {
        this.responseStatusCode = responseStatusCode;
    }
    
    /**
     * @return the responseContent
     */
    public JsonObject getResponseContent() {
        return responseContent;
    }

    /**
     * @param responseContentType the responseContent to set
     */
    public void setResponseContent(JsonObject responseContent) {
        this.responseContent = responseContent;
    }

    /**
     * @return the responseContentType
     */
    public String getResponseContentType() {
        return responseContentType;
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setResponseContentType(String responseContentType) {
        this.responseContentType = responseContentType;
    }


    public long getRequestStartTime() {
        return requestStartTime;
    }

    /**
     * @return the inError
     */
    public boolean isInError() {
        return inError;
    }

    /**
     * @param inError the inError to set
     */
    public void setInError(boolean inError) {
        this.inError = inError;
    }

    /**
     * @return the authenticatedAccount
     */
    public Account getAuthenticatedAccount() {
        return authenticatedAccount;
    }

    /**
     * @param authenticatedAccount the authenticatedAccount to set
     */
    public void setAuthenticatedAccount(Account authenticatedAccount) {
        this.authenticatedAccount = authenticatedAccount;
    }

    public enum METHOD {
        GET,
        POST,
        PUT,
        DELETE,
        PATCH,
        OPTIONS,
        OTHER
    }
    
    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.DELETE
     */
    public boolean isDelete() {
        return this.method == METHOD.DELETE;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.GET
     */
    public boolean isGet() {
        return this.method == METHOD.GET;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.OPTIONS
     */
    public boolean isOptions() {
        return this.method == METHOD.OPTIONS;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PATCH
     */
    public boolean isPatch() {
        return this.method == METHOD.PATCH;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.POST
     */
    public boolean isPost() {
        return this.method == METHOD.POST;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PUT
     */
    public boolean isPut() {
        return this.method == METHOD.PUT;
    }
}
