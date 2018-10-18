/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.utils;

/**
 * utility class to help daemonizing process
 *
 * @see <a href="http://akuma.kohsuke.org">Akuma</a>
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
import com.sun.akuma.Daemon;
import static com.sun.akuma.Daemon.getCurrentExecutable;
import com.sun.akuma.JavaVMArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class uIAMDaemon extends Daemon {

    private static final Logger LOGGER = LoggerFactory.getLogger(uIAMDaemon.class);

    /**
     * Returns true if the current process is already launched as a daemon via
     * {@link #daemonize()}.
     *
     * @return
     */
    @Override
    public boolean isDaemonized() {
        return System.getProperty(uIAMDaemon.class.getName()) != null;
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

            JavaVMArguments args = JavaVMArguments.current();
            args.setSystemProperty(uIAMDaemon.class.getName(), "daemonized");

            String _args[] = args.toArray(new String[args.size()]);

            _args[0] = getCurrentExecutable();

            // create child process
            new ProcessBuilder()
                    .command(_args)
                    .start();

            // parent exists
            System.exit(0);
        } catch (Throwable t) {
            LOGGER.error("Fork failed", t);
            System.exit(-4);
        }
    }
}
