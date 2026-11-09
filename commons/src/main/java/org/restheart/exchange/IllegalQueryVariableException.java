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

/**
 * Thrown when a pipeline binds a variable from a query parameter whose value contains operators,
 * with aggregation-check-operators enabled. It is the caller's error: handlers answer 400.
 *
 * <p>It extends {@link SecurityException}, so a caller that only knows the operator check
 * still fails closed.</p>
 *
 * @since 9.15.3
 */
public class IllegalQueryVariableException extends SecurityException {

    private static final long serialVersionUID = 4127630967513482154L;

    /**
     * @param name the query parameter the pipeline binds
     */
    public IllegalQueryVariableException(String name) {
        super("illegal query parameter " + name + ": aggregation variables cannot include operators");
    }
}
