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

/**
 * Provides utilities for GraalVM native image support and detection.
 * 
 * <p>This package contains classes that help RESTHeart work seamlessly in both
 * traditional JVM environments and GraalVM native image contexts. The primary
 * focus is on runtime detection of the execution environment, allowing code
 * to adapt its behavior based on whether it's running:</p>
 * <ul>
 *   <li>In a standard JVM</li>
 *   <li>During GraalVM native image build time</li>
 *   <li>As a compiled native image at runtime</li>
 * </ul>
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Environment Detection:</strong> Determine if code is executing
 *       in a native image context without runtime dependencies on GraalVM</li>
 *   <li><strong>Build-time vs Runtime:</strong> Distinguish between native image
 *       compilation phase and execution phase</li>
 *   <li><strong>Image Type Detection:</strong> Identify whether the native image
 *       is an executable or shared library</li>
 * </ul>
 * 
 * <h2>Use Cases</h2>
 * <p>The utilities in this package are particularly useful for:</p>
 * <ul>
 *   <li>Conditional resource loading strategies (classpath vs. filesystem)</li>
 *   <li>Reflection configuration during build time</li>
 *   <li>Performance optimizations specific to native images</li>
 *   <li>Feature detection and graceful degradation</li>
 *   <li>Debug logging and diagnostics</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Adapt behavior based on execution context
 * if (ImageInfo.inImageRuntimeCode()) {
 *     // Use optimized native image code path
 *     loadResourcesFromEmbedded();
 * } else {
 *     // Use standard JVM code path
 *     loadResourcesFromClasspath();
 * }
 * 
 * // Register reflection during build time
 * if (ImageInfo.inImageBuildtimeCode()) {
 *     registerReflectionClasses();
 * }
 * }</pre>
 * 
 * <h2>Design Philosophy</h2>
 * <p>The classes in this package use reflection to detect GraalVM's ImageInfo
 * class, ensuring that RESTHeart can run in environments where GraalVM is not
 * present. All detection methods gracefully return {@code false} when running
 * in a standard JVM, allowing for clean conditional logic without runtime
 * dependencies.</p>
 * 
 * @since 1.0
 * @see org.restheart.graal.ImageInfo
 */
package org.restheart.graal;