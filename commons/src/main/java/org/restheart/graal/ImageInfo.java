/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.graal;

/**
 * Utility class for detecting GraalVM native image context at runtime.
 * 
 * <p>This class provides static methods to determine whether code is executing
 * in a GraalVM native image context (build time or runtime) or in a standard JVM.
 * It uses reflection to check for the presence of GraalVM's ImageInfo class and
 * delegates to its methods when available.</p>
 * 
 * <p>This is particularly useful for conditional logic that needs to behave
 * differently in native image vs. JVM contexts, such as:</p>
 * <ul>
 *   <li>Resource loading strategies</li>
 *   <li>Reflection configuration</li>
 *   <li>Runtime optimizations</li>
 *   <li>Feature availability checks</li>
 * </ul>
 * 
 * <p>All methods return {@code false} when running in a standard JVM where
 * the GraalVM ImageInfo class is not available.</p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public class ImageInfo {
    /**
     * Cached reference to the GraalVM ImageInfo class, if available.
     * Will be null when running in a standard JVM.
     */
    private static Class<?> imageInfoClass = null;

    static {
        try {
            imageInfoClass = Class.forName("org.graalvm.nativeimage.ImageInfo");
        } catch(ClassNotFoundException cfe) {
            imageInfoClass = null;
        }
    }

    /**
     * Checks if code is currently executing during GraalVM native image build time.
     * 
     * <p>This method returns {@code true} only when called during the native image
     * generation process (i.e., when running {@code native-image} command).
     * This is useful for:</p>
     * <ul>
     *   <li>Registering classes for reflection</li>
     *   <li>Configuring build-time initialization</li>
     *   <li>Performing static analysis preparations</li>
     * </ul>
     * 
     * @return {@code true} if executing during native image build time,
     *         {@code false} if running in a JVM or at native image runtime
     * @see #inImageRuntimeCode()
     * @see #inImageCode()
     */
    public static boolean inImageBuildtimeCode() {
        if (imageInfoClass == null) {
            return false;
        } else {
            try {
                return (boolean) imageInfoClass.getDeclaredMethod("inImageBuildtimeCode").invoke(null);
            } catch(Throwable ite) {
                return false;
            }
        }
    }

    /**
     * Checks if code is executing in any GraalVM native image context.
     * 
     * <p>This method returns {@code true} when running either:</p>
     * <ul>
     *   <li>During native image build time</li>
     *   <li>At native image runtime</li>
     * </ul>
     * 
     * <p>Use this method when you need to detect any native image context,
     * regardless of whether it's build time or runtime. This is useful for
     * code paths that should behave differently in native images vs. JVM.</p>
     * 
     * @return {@code true} if executing in any native image context (build or runtime),
     *         {@code false} if running in a standard JVM
     * @see #inImageBuildtimeCode()
     * @see #inImageRuntimeCode()
     */
    public static boolean inImageCode() {
        if (imageInfoClass == null) {
            return false;
        } else {
            try {
                return (boolean) imageInfoClass.getDeclaredMethod("inImageCode").invoke(null);
            } catch(Throwable ite) {
                return false;
            }
        }
    }

    /**
     * Checks if code is executing at GraalVM native image runtime.
     * 
     * <p>This method returns {@code true} only when running in a compiled
     * native image executable, not during build time or in a standard JVM.
     * This is useful for:</p>
     * <ul>
     *   <li>Runtime-specific optimizations</li>
     *   <li>Native image feature detection</li>
     *   <li>Conditional resource loading</li>
     *   <li>Performance tuning specific to native images</li>
     * </ul>
     * 
     * @return {@code true} if executing at native image runtime,
     *         {@code false} if running in a JVM or during build time
     * @see #inImageBuildtimeCode()
     * @see #inImageCode()
     */
    public static boolean inImageRuntimeCode() {
        if (imageInfoClass == null) {
            return false;
        } else {
            try {
                return (boolean) imageInfoClass.getDeclaredMethod("inImageRuntimeCode").invoke(null);
            } catch(Throwable ite) {
                return false;
            }
        }
    }

    /**
     * Checks if the native image was built as a standalone executable.
     * 
     * <p>When {@code true}, the native image can be run directly as an
     * executable file. This is the most common type of native image output.</p>
     * 
     * <p>Note: This method only returns meaningful results when called
     * from within a native image context. It always returns {@code false}
     * when running in a standard JVM.</p>
     * 
     * @return {@code true} if the native image is a standalone executable,
     *         {@code false} if it's a shared library or running in a JVM
     * @see #isSharedLibrary()
     */
    public static boolean isExecutable() {
        if (imageInfoClass == null) {
            return false;
        } else {
            try {
                return (boolean) imageInfoClass.getDeclaredMethod("isExecutable").invoke(null);
            } catch(Throwable ite) {
                return false;
            }
        }
    }

    /**
     * Checks if the native image was built as a shared library.
     * 
     * <p>When {@code true}, the native image was built as a shared library
     * (.so, .dll, .dylib) that can be loaded and used by other applications
     * through JNI or other native interfaces.</p>
     * 
     * <p>Note: This method only returns meaningful results when called
     * from within a native image context. It always returns {@code false}
     * when running in a standard JVM.</p>
     * 
     * @return {@code true} if the native image is a shared library,
     *         {@code false} if it's an executable or running in a JVM
     * @see #isExecutable()
     */
    public static boolean isSharedLibrary() {
        if (imageInfoClass == null) {
            return false;
        } else {
            try {
                return (boolean) imageInfoClass.getDeclaredMethod("isSharedLibrary").invoke(null);
            } catch(Throwable ite) {
                return false;
            }
        }
    }
}
