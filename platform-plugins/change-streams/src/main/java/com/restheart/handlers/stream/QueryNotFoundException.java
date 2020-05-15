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
package com.restheart.handlers.stream;


/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class QueryNotFoundException extends Exception {

    /**
     *
     */
    public QueryNotFoundException() {
        super();
    }

    /**
     *
     * @param message
     */
    public QueryNotFoundException(String message) {
        super(message);
    }

    /**
     *
     * @param message
     * @param cause
     */
    public QueryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
