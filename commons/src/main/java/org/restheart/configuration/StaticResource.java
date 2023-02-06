/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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
package org.restheart.configuration;

import static org.restheart.configuration.Utils.getOrDefault;
import static org.restheart.configuration.Utils.asListOfMaps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record StaticResource(String what, String where, String welcomeFile, boolean embedded) {
    public static final String STATIC_RESOURCES_MOUNTS_KEY = "static-resources";
    public static final String STATIC_RESOURCES_MOUNT_WHAT_KEY = "what";
    public static final String STATIC_RESOURCES_MOUNT_WHERE_KEY = "where";
    public static final String STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY = "welcome-file";
    public static final String STATIC_RESOURCES_MOUNT_EMBEDDED_KEY = "embedded";

    public StaticResource(Map<String, Object> conf, boolean silent) {
        this(getOrDefault(conf, STATIC_RESOURCES_MOUNT_WHAT_KEY, null, silent),
            getOrDefault(conf, STATIC_RESOURCES_MOUNT_WHERE_KEY, null, silent),
            // following are optional paramenter, so get them always in silent mode
            getOrDefault(conf, STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY, "index.html", true),
            getOrDefault(conf, STATIC_RESOURCES_MOUNT_EMBEDDED_KEY, false, true));
    }

    public static List<StaticResource> build(Map<String, Object> conf, boolean silent) {
        var staticResources = asListOfMaps(conf, STATIC_RESOURCES_MOUNTS_KEY, null, silent);

        if (staticResources != null) {
            return staticResources.stream().map(p -> new StaticResource(p, silent)).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }
}