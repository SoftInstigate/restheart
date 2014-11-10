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
package com.softinstigate.restheart.utils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.bson.types.ObjectId;

/**
 *
 * @author uji
 */
public class RequestHelper {
    public static boolean checkReadEtag(HttpServerExchange exchange, String etag) {
        if (etag == null) {
            return false;
        }

        HeaderValues vs = exchange.getRequestHeaders().get(Headers.IF_NONE_MATCH);

        return vs == null || vs.getFirst() == null ? false : vs.getFirst().equals(etag);
    }

    public static ObjectId getWriteEtag(HttpServerExchange exchange) {
        HeaderValues vs = exchange.getRequestHeaders().get(Headers.IF_MATCH);

        return vs == null || vs.getFirst() == null ? null : getEtagAsObjectId(vs.getFirst());
    }

    public static ObjectId getEtagAsObjectId(Object etag) {
        if (etag == null) {
            return null;
        }

        if (ObjectId.isValid("" + etag)) {
            return new ObjectId("" + etag);
        }
        else {
            return new ObjectId();
        }
    }
}
