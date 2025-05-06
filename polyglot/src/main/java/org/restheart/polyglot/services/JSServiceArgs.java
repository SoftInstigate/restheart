/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

package org.restheart.polyglot.services;

import java.util.Map;
import java.util.Optional;

import org.graalvm.polyglot.Source;
import org.restheart.configuration.Configuration;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;

import com.mongodb.client.MongoClient;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record JSServiceArgs(String name,
    String description,
    String uri,
    boolean secured,
    String modulesReplacements,
    MATCH_POLICY matchPolicy,
    Source handleSource,
    Configuration configuration,
    Optional<MongoClient> mclient,
    Map<String, String> contextOptions) {
}