/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

import java.lang.ref.Cleaner;

/**
 * Utility class providing access to a shared Cleaner instance for resource management.
 * This class implements a singleton pattern to provide a single Cleaner that uses
 * virtual threads for efficient cleanup operations.
 *
 * <p>The Cleaner is useful for automatic cleanup of resources when objects become
 * unreachable, providing a safer alternative to finalization.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CleanerUtils {
    /** Singleton instance of CleanerUtils. */
    private static CleanerUtils instance = null;
    
    /** The Cleaner instance that uses virtual threads for cleanup operations. */
    private final Cleaner cleaner;

    /**
     * Private constructor to prevent direct instantiation.
     * Creates a Cleaner instance that uses virtual threads for efficient cleanup.
     */
    private CleanerUtils() {
        this.cleaner = Cleaner.create(Thread.ofVirtual().factory());
    }

    /**
     * Returns the singleton instance of CleanerUtils.
     * Creates the instance if it doesn't exist yet.
     *
     * @return the singleton CleanerUtils instance
     */
    public static CleanerUtils get() {
        if (instance == null) {
            instance = new CleanerUtils();
        }
        return instance;
    }

    /**
     * Returns the Cleaner instance for registering cleanup actions.
     * The returned Cleaner uses virtual threads for efficient cleanup operations.
     *
     * @return the Cleaner instance
     */
    public Cleaner cleaner() {
        return cleaner;
    }
}
