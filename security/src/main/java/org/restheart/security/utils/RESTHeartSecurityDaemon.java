/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.utils;

/**
 * utility class to help daemonizing process
 *
 * @see <a href="http://akuma.kohsuke.org">Akuma</a>
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
import com.sun.akuma.Daemon;
import com.sun.akuma.JavaVMArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RESTHeartSecurityDaemon extends Daemon {

    private static final Logger LOGGER = LoggerFactory.getLogger(RESTHeartSecurityDaemon.class);

    /**
     * Returns true if the current process is already launched as a daemon via
     * {@link #daemonize()}.
     *
     * @return
     */
    @Override
    public boolean isDaemonized() {
        return System.getProperty(RESTHeartSecurityDaemon.class.getName()) != null;
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
            args.setSystemProperty(RESTHeartSecurityDaemon.class.getName(), "daemonized");

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
