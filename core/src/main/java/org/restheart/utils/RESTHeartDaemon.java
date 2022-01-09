/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

/**
 * utility class to help daemonizing process
 *
 * See<a href="http://akuma.kohsuke.org">Akuma</a>
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
import com.sun.akuma.Daemon;
import com.sun.akuma.JavaVMArguments;

import org.graalvm.nativeimage.ImageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RESTHeartDaemon extends Daemon {

    private static final Logger LOGGER = LoggerFactory.getLogger(RESTHeartDaemon.class);

    /**
     * Returns true if the current process is already launched as a daemon via
     * {@link #daemonize()}.
     *
     * @return
     */
    @Override
    public boolean isDaemonized() {
        return System.getProperty(RESTHeartDaemon.class.getName()) != null;
    }

    /**
     * Relaunches the JVM as a daemon.
     *
     */
    @Override
    public void daemonize() {
        if (isDaemonized()) {
            throw new IllegalStateException("Already running as a daemon");
        }

        try {
            LOGGER.info("Forking...");

            var args = JavaVMArguments.current();
            args.setSystemProperty(RESTHeartDaemon.class.getName(), "daemonized");

            String _args[] = args.toArray(new String[args.size()]);

            if (isExecutable()) {
                _args[0] = FileUtils.getFileAbsolutePath(_args[0]).toString();
            } else {
                _args[0] = getCurrentExecutable();
            }

            // create child process
            var p = new ProcessBuilder().command(_args).start();

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
            // this happen when not running GraalVM. ImageInfo would not be available.
            return false;
        }
    }
}
