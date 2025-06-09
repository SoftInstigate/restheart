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

import com.google.common.reflect.TypeToken;
import java.lang.reflect.Type;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;

/**
 * Interface for resolving request and response types at runtime using reflection.
 *
 * <p>ExchangeTypeResolver provides a mechanism for plugins to determine their concrete
 * request and response types at runtime. This is particularly useful for the plugin
 * framework to understand what types of exchanges a plugin can handle without requiring
 * explicit type declarations.</p>
 *
 * <h2>Purpose</h2>
 * <p>This interface solves the problem of type erasure in Java generics. Since generic
 * type information is lost at runtime, this interface uses Google Guava's {@link TypeToken}
 * to capture and preserve the actual type parameters used by implementing classes.</p>
 *
 * <h2>How It Works</h2>
 * <p>The interface uses the "super type token" pattern to capture generic type information:</p>
 * <ul>
 *   <li>Creates an anonymous subclass of {@link TypeToken}</li>
 *   <li>Passes the implementing class to the TypeToken constructor</li>
 *   <li>TypeToken uses reflection to extract the actual type parameters</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>This interface is typically implemented indirectly through plugin interfaces like
 * {@link Service} and {@link Interceptor}. Plugins don't usually need to override these
 * methods unless they have special type resolution requirements.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class UserService implements JsonService {
 *     // The framework can determine that this service handles:
 *     // - JsonRequest as the request type
 *     // - JsonResponse as the response type
 * }
 *
 * // Custom implementation with specific types
 * public class CustomService implements Service<BsonRequest, BsonResponse> {
 *     // Types are automatically resolved as BsonRequest and BsonResponse
 * }
 * }</pre>
 *
 * <h2>Implementation Note</h2>
 * <p>The default implementations use serial version UIDs to suppress warnings about
 * serialization compatibility. These TypeToken instances are used only for type resolution
 * and are never actually serialized.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <R> the request type, must extend {@link Request}
 * @param <S> the response type, must extend {@link Response}
 * @see TypeToken
 * @see Service
 * @see Interceptor
 */
public interface ExchangeTypeResolver<R extends Request<?>, S extends Response<?>> {
    /**
     * Resolves the concrete request type handled by this plugin.
     *
     * <p>This method uses reflection to determine the actual request type parameter
     * used when implementing this interface. The resolved type is used by the framework
     * to match incoming requests with appropriate handlers.</p>
     *
     * @return the {@link Type} representing the request class handled by this plugin
     */
    default Type requestType() {
        return new TypeToken<R>(getClass()) {
			private static final long serialVersionUID = 8363463867743712134L;
        }.getType();
    }

    /**
     * Resolves the concrete response type produced by this plugin.
     *
     * <p>This method uses reflection to determine the actual response type parameter
     * used when implementing this interface. The resolved type is used by the framework
     * to properly initialize response objects for the plugin.</p>
     *
     * @return the {@link Type} representing the response class produced by this plugin
     */
    default Type responseType() {
        return new TypeToken<S>(getClass()) {
            private static final long serialVersionUID = -2478049152751095135L;
        }.getType();
    }
}
