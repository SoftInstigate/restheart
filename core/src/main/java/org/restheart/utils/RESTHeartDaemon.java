/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.utils;

import org.restheart.graal.ImageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * utility class to help daemonizing process
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RESTHeartDaemon {

    private static final Logger LOGGER = LoggerFactory.getLogger(RESTHeartDaemon.class);

    /**
     * Returns true if the current process is already launched as a daemon via
     * {@link #daemonize()}.
     *
     * @return
     */
    public boolean isDaemonized() {
        return System.getProperty(RESTHeartDaemon.class.getName()) != null;
    }

    /**
     * Relaunches the JVM as a daemon.
     *
     */
    public void daemonize() {
        if (isDaemonized()) {
            throw new IllegalStateException("Already running as a daemon");
        }

        try {
            LOGGER.info("Forking...");

            var processInfo = ProcessHandle.current().info();
            var __args = processInfo.arguments();

            var args = new LinkedList<>(Arrays.asList(__args.orElseGet(() -> new String[0])));

            LOGGER.info("args: {}", (Object) args);

            if (processInfo.command().isEmpty()) {
                throw new IllegalStateException("Command not available");
            }

            var command = processInfo.command().get();

            // add system property to identify daemon process
            args.addFirst("-D".concat(RESTHeartDaemon.class.getName()).concat("=daemonized"));

            // create child process
            if (isExecutable()) {
                args.addFirst(FileUtils.getFileAbsolutePath(command).toString());
            } else {
                args.addFirst(command);
            }

            LOGGER.info("newArgs: {}", args);

            var p = new ProcessBuilder()
                .command(args.toArray(new String[0]))
                .directory(new File(System.getProperty("user.dir")))
                .inheritIO()
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

            // disconnect std io
            p.getInputStream().close();
            p.getOutputStream().close();
            p.getErrorStream().close();

            LOGGER.info("Forked process: {}", p.pid());
            // parent exists
            System.exit(0);
        } catch (Throwable t) {
            LOGGER.error("Fork failed", t);
            System.exit(-4);
        }
    }

    private boolean isExecutable() {
        try {
            return ImageInfo.isExecutable();
        } catch(Throwable cnfe) {
            // this happens when not running GraalVM. ImageInfo would not be available.
            return false;
        }
    }
}
