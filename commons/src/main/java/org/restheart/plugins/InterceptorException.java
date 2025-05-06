/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

package org.restheart.plugins;

import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class InterceptorException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = -846615677223399751L;

    int statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;

    /**
     *
     */
    public InterceptorException() {
        super();
    }

    /**
     *
     * @param statusCode
     */
    public InterceptorException(int statusCode) {
        super();
        this.statusCode = statusCode;
    }

    /**
     *
     * @param message
     */
    public InterceptorException(String message) {
        super(message);
    }

    /**
     *
     * @param message
     * @param statusCode
     */
    public InterceptorException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     *
     * @param message
     * @param cause
     */
    public InterceptorException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     *
     * @param message
     * @param statusCode
     * @param cause
     */
    public InterceptorException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * @return the statusCode
     */
    public int getStatusCode() {
        return statusCode;
    }
}
