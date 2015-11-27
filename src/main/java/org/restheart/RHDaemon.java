/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart;

/**
 * this just overrieds Kawaguchi'a Daemon to use the RESTHeart LOGGER
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
import com.sun.jna.StringArray;
import static com.sun.akuma.CLibrary.LIBC;
import com.sun.akuma.Daemon;
import com.sun.akuma.JavaVMArguments;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RHDaemon extends Daemon.WithoutChdir {

    private static final Logger LOGGER = LoggerFactory.getLogger(RHDaemon.class);

    /**
     * Returns true if the current process is already launched as a daemon via
     * {@link #daemonize()}.
     *
     * @return
     */
    @Override
    public boolean isDaemonized() {
        return System.getProperty(RHDaemon.class.getName()) != null;
    }

    /**
     * Relaunches the JVM with the given arguments into the daemon.
     *
     * @param args
     */
    @Override
    public void daemonize(JavaVMArguments args) {
        if (isDaemonized()) {
            throw new IllegalStateException("Already running as a daemon");
        }

        if (System.getProperty("com.sun.management.jmxremote.port") != null) {
            try {
                Method m = Class.forName("sun.management.Agent").getDeclaredMethod("stopRemoteManagementAgent");
                m.setAccessible(true);
                m.invoke(null);
            } catch (Exception x) {
                LOGGER.error("could not simulate jcmd $$ ManagementAgent.stop (JENKINS-14529)", x);
            }
        }

        // let the child process now that it's a daemon
        args.setSystemProperty(RHDaemon.class.getName(), "daemonized");

        // prepare for a fork
        String exe = getCurrentExecutable();
        StringArray sa = new StringArray(args.toArray(new String[args.size()]));

        LOGGER.info("Forking...");

        int i = LIBC.fork();
        
        if (i < 0) {
            LOGGER.error("Fork failed");
            System.exit(-1);
        }
        if (i == 0) {
            // with fork, we lose all the other critical threads, to exec to Java again

            LOGGER.info("Executing child process...");
            
            LIBC.execv(exe, sa);
            LOGGER.error("exec failed");
            System.exit(-1);
        } 
        // parent exits
    }
}
