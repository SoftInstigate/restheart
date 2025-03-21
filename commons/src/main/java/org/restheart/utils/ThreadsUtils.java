/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
