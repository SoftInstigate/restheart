/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

public class ImageInfo {
    private static Class imageInfoClass = null;

    static {
        try {
            imageInfoClass = Class.forName("org.graalvm.nativeimage.ImageInfo");
        } catch(ClassNotFoundException cfe) {
            imageInfoClass = null;
        }
    }

    /**
     *
     * Returns true if (at the time of the call) code is executing in the
     * context of image building
     *
     * @return
     */
    @SuppressWarnings("unchecked")
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
     * Returns true if (at the time of the call) code is executing in the
     * context of image building or during image runtime, else false.
     *
     * @return
     */
    @SuppressWarnings("unchecked")
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
     * Returns true if (at the time of the call) code is executing at image
     * runtime.
     *
     * @return
     */
    @SuppressWarnings("unchecked")
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
     * Returns true if the image is built as an executable.
     *
     * @return
     */
    @SuppressWarnings("unchecked")
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
     * Returns true if the image is build as a shared library.
     */
    @SuppressWarnings("unchecked")
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
