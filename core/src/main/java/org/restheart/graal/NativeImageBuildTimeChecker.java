/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

/**
 * this is used by PluginsScanner to determine if scanning is occurring at build
 * or run time. When running with java, Bootstrapper calls
 * NativeImageBuildTimeChecker.atRunting() making the variable bt=false. When
 * building with native-image, the class is initialized at build time (due to
 * native-image.properties that sets PluginScanner to be initialized at build
 * time and tranistively NativeImageBuildTimeChecker, since PluginScanner
 * references it) making bt=false
 */
public class NativeImageBuildTimeChecker {
    private static boolean bt = true;

    public static boolean isBuildTime() {
        return bt;
    }

    public static void atRuntime() {
        bt = false;
    }
}
