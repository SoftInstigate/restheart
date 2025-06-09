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
package org.restheart.utils;

/**
 * Utility class providing helper methods for lambda expressions and functional programming.
 * This class contains utilities for handling checked exceptions within lambda expressions
 * and other functional programming constructs.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LambdaUtils {
    /**
     * Allows throwing checked exceptions from lambda expressions without declaring them.
     * This method uses the "sneaky throws" technique to bypass Java's checked exception
     * handling in lambda expressions and functional interfaces.
     *
     * <p>This is particularly useful when working with functional interfaces like Consumer,
     * Predicate, or Function that don't allow checked exceptions in their method signatures.</p>
     *
     * @param ex the throwable to be thrown
     * @see <a href="https://www.baeldung.com/java-sneaky-throws">Baeldung: Sneaky Throws</a>
     */
    public static void throwsSneakyException(Throwable ex) {
        sneakyThrow(ex);
    }

    /**
     * Internal method that performs the actual sneaky throw by casting the throwable
     * to an unchecked exception type. This bypasses the compiler's checked exception
     * verification.
     *
     * @param <E> the exception type (inferred by the compiler)
     * @param e the throwable to be thrown
     * @throws E the exception cast to the inferred type
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
