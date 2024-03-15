/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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