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
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import static com.restheart.net.Client.RESPONSE_BODY_KEY;
import io.undertow.client.ClientResponse;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Response {
    private final ClientResponse wrapped;
    
    public static Response wrap(ClientResponse wrapped) {
        return new Response(wrapped);
    }
    
    private Response(ClientResponse cresp) {
        this.wrapped = cresp;
    }
    
    public HeaderMap getResponseHeaders() {
        return wrapped.getResponseHeaders();
    }
    
    public HttpString getProtocol() {
        return wrapped.getProtocol();
    }

    public int getStatusCode() {
        return wrapped.getResponseCode();
    }

    public String getStatus() {
        return wrapped.getStatus();
    }
    
    public String getBody() {
        return wrapped.getAttachment(RESPONSE_BODY_KEY);
    }
    
    public JsonElement getBodyAsJson() throws JsonParseException {
        return JsonParser.parseString(getBody());
    }

    @Override
    public String toString() {
        return "Response{" +
                "responseHeaders=" + getResponseHeaders() +
                ", responseCode=" + getStatusCode() +
                ", status='" + getStatus() + '\'' +
                ", protocol=" + getProtocol() +
                '}';
    }
}
