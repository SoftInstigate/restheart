/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.utils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class OSChecker {

    private static final String OS = System.getProperty("os.name").toLowerCase();

    /**
     *
     * @return
     */
    public static boolean isWindows() {
        return OS.contains("win");
    }

    /**
     *
     * @return
     */
    public static boolean isMac() {
        return OS.contains("mac");
    }

    /**
     *
     * @return
     */
    public static boolean isUnix() {
        return OS.contains("nix") || OS.contains("nux") || OS.indexOf("aix") > 0;
    }

    /**
     *
     * @return
     */
    public static boolean isSolaris() {
        return OS.contains("sunos");
    }

    private OSChecker() {
    }
}
