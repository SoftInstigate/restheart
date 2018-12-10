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
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ExchangeHelper {

    // other constants
    public static final String SLASH = "/";
    public static final String PATCH = "PATCH";
    public static final String UNDERSCORE = "_";

    private final HttpServerExchange wrapped;

    private static final AttachmentKey<String> RAW_CONTENT_KEY = AttachmentKey.create(String.class);
    private static final AttachmentKey<Integer> RESPONSE_STATUS_CODE_KEY = AttachmentKey.create(Integer.class);
    private static final AttachmentKey<String> RESPONSE_CONTENT_TYPE_KEY = AttachmentKey.create(String.class);
    private static final AttachmentKey<JsonObject> RESPONSE_JSON_CONTENT_KEY = AttachmentKey.create(JsonObject.class);
    private static final AttachmentKey<Boolean> IN_ERROR_KEY = AttachmentKey.create(Boolean.class);
    private static final AttachmentKey<Long> REQUEST_START_TIME_KEY = AttachmentKey.create(Long.class);

    
    public ExchangeHelper(HttpServerExchange exchange) {
        this.wrapped = exchange;
    }

    private static METHOD selectRequestMethod(HttpString _method) {
        if (Methods.GET.equals(_method)) {
            return METHOD.GET;
        } else if (Methods.POST.equals(_method)) {
            return METHOD.POST;
        } else if (Methods.PUT.equals(_method)) {
            return METHOD.PUT;
        } else if (Methods.DELETE.equals(_method)) {
            return METHOD.DELETE;
        } else if (PATCH.equals(_method.toString())) {
            return METHOD.PATCH;
        } else if (Methods.OPTIONS.equals(_method)) {
            return METHOD.OPTIONS;
        } else {
            return METHOD.OTHER;
        }
    }

    /**
     *
     * @return method
     */
    public METHOD getMethod() {
        return selectRequestMethod(wrapped.getRequestMethod());
    }

    /**
     * @return the rawContent
     */
    public String getRawContent() {
        return wrapped.getAttachment(RAW_CONTENT_KEY);
    }

    /**
     * @param rawContent the rawContent to set
     */
    public void setRawContent(String rawContent) {
        wrapped.putAttachment(RAW_CONTENT_KEY, rawContent);
    }

    /**
     * @return the responseStatusCode
     */
    public int getResponseStatusCode() {
        return wrapped.getAttachment(RESPONSE_STATUS_CODE_KEY);
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setResponseStatusCode(int responseStatusCode) {
        wrapped.putAttachment(RESPONSE_STATUS_CODE_KEY, responseStatusCode);
    }

    /**
     * @return the responseJsonContent
     */
    public JsonObject getResponseJsonContent() {
        return wrapped.getAttachment(RESPONSE_JSON_CONTENT_KEY);
    }

    /**
     * @param responseJsonContent the responseJsonContent to set
     */
    public void setResponseContent(JsonObject responseJsonContent) {
        wrapped.putAttachment(RESPONSE_JSON_CONTENT_KEY, responseJsonContent);
    }

    /**
     * @return the responseContentType
     */
    public String getResponseContentType() {
        return wrapped.getAttachment(RESPONSE_CONTENT_TYPE_KEY);
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setResponseContentType(String responseContentType) {
        wrapped.putAttachment(RESPONSE_CONTENT_TYPE_KEY, responseContentType);
    }

    /**
     * @return the requestStartTime
     */
    public Long getRequestStartTime() {
        return wrapped.getAttachment(REQUEST_START_TIME_KEY);
    }

    /**
     * @param requestStartTime the requestStartTime to set
     */
    public void setRequestStartTime(Long requestStartTime) {
        wrapped.putAttachment(REQUEST_START_TIME_KEY, requestStartTime);
    }

    /**
     * @return the inError
     */
    public boolean isInError() {
        return wrapped.getAttachment(IN_ERROR_KEY);
    }

    /**
     * @param inError the inError to set
     */
    public void setInError(boolean inError) {
        wrapped.putAttachment(IN_ERROR_KEY, inError);
    }

    /**
     * @return the authenticatedAccount
     */
    public Account getAuthenticatedAccount() {
        return this.wrapped.getSecurityContext().getAuthenticatedAccount();
    }

    public enum METHOD {
        GET, POST, PUT, DELETE, PATCH, OPTIONS, OTHER
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.DELETE
     */
    public boolean isDelete() {
        return getMethod() == METHOD.DELETE;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.GET
     */
    public boolean isGet() {
        return getMethod() == METHOD.GET;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.OPTIONS
     */
    public boolean isOptions() {
        return getMethod() == METHOD.OPTIONS;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PATCH
     */
    public boolean isPatch() {
        return getMethod() == METHOD.PATCH;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.POST
     */
    public boolean isPost() {
        return getMethod() == METHOD.POST;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PUT
     */
    public boolean isPut() {
        return getMethod() == METHOD.PUT;
    }
}
