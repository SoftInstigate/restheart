/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class IllegalQueryParameterException extends Exception {

	private static final long serialVersionUID = 3012988294234123826L;

	/**
     *
     * @param message
     */
    public IllegalQueryParameterException(String message) {
        super(message);
    }

    /**
     *
     * @param message
     * @param cause
     */
    public IllegalQueryParameterException(String message, Throwable cause) {
        super(message, cause);
    }
}
