/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadsUtils {
    private static final ExecutorService VIRTUAL_THREADS_EXECUTOR = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("RH-VIRTUAL-WORKER-", 0L).factory());

    public static ExecutorService virtualThreadsExecutor() {
        return VIRTUAL_THREADS_EXECUTOR;
    }
}