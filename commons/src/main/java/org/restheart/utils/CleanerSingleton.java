/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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

public class CleanerSingleton {
    public static Cleaner get(){
        return CleanerHolder.INSTANCE;
    }

    private static class CleanerHolder {
        private static final Cleaner INSTANCE;

        // make sure the Singleton is a Singleton even in a multi-classloader environment
        // credits to https://stackoverflow.com/users/145989/ondra-Žižka
        // https://stackoverflow.com/a/47445573/4481670
        static {
            // There should be just one system class loader object in the whole JVM.
            synchronized(ClassLoader.getSystemClassLoader()) {
                var sysProps = System.getProperties();
                // The key is a String, because the .class object would be different across classloaders.
                var singleton = (Cleaner) sysProps.get(CleanerHolder.class.getName());

                // Some other class loader loaded MongoClientSingleton earlier.
                if (singleton != null) {
                    INSTANCE = singleton;
                } else {
                    // Otherwise this classloader is the first one, let's create a singleton.
                    // Make sure not to do any locking within this.
                    INSTANCE = Cleaner.create();
                    System.getProperties().put(CleanerHolder.class.getName(), INSTANCE);
                }
            }
        }
    }
}
