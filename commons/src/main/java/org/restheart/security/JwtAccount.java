/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
package org.restheart.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Jwt Account
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JwtAccount extends BaseAccount implements WithProperties<String> {
    /**
     *
     */
    private static final long serialVersionUID = -2405615782892727187L;
    final private String properties;

    /**
     *
     * @param name
     * @param roles
     * @param properties
     */
    public JwtAccount(final String name, final Set<String> roles, String properties) {
        super(name, roles);
        this.properties = properties;
    }

    @Override
    public String toString() {
        return super.toString().concat(" jwt=").concat(properties);
    }

    /**
     * @return the jwt payload as string
     */
    @Override
    public String properties() {
        return properties;
    }

    private static Gson GSON = new GsonBuilder().serializeNulls().create();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, ? super Object> propertiesAsMap() {
        return GSON.fromJson(properties, HashMap.class);
    }
}
