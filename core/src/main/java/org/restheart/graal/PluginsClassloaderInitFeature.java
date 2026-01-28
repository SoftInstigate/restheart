/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
/** WIP
 * Automate reflect configuratio currently done via GenerateGraalvmReflectConfig
 *
 */

package org.restheart.graal;

import org.graalvm.nativeimage.hosted.Feature;
import org.restheart.plugins.PluginsClassloader;
import org.restheart.plugins.PluginsScanner;

/**
 * Initializes PluginsClassloader with the native image classpath and triggers PluginsScanner
 *
 */
public class PluginsClassloaderInitFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // System.out.println("***** afterRegistration " + access.getApplicationClassPath().toString());
        // initialize PluginsClassloader with the native image classpath
        PluginsClassloader.init(access.getApplicationClassPath());
        // initialize PluginScanner class
        PluginsScanner.initAtBuildTime();
    }

    @Override
    public String getDescription() {
        return "Initializes PluginsClassloader with the native image classpath and triggers PluginsScanner";
    }
}
