/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
import org.restheart.polyglot.JSPlugin;

import com.mongodb.client.MongoClient;


/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class JSService extends JSPlugin {
    private final String uri;
    private final boolean secured;
    private final MATCH_POLICY matchPolicy;

    public JSService(String name,
        String pluginClass,
        String description,
        String uri,
        boolean secured,
        MATCH_POLICY matchPolicy,
        Source handleSource,
        String modulesReplacements,
        Configuration config,
        Optional<MongoClient> mclient,
        Map<String, String> contextOptions) {
        super(name, description, handleSource, modulesReplacements, config, mclient, contextOptions);
        this.uri = uri;
        this.secured = secured;
        this.matchPolicy = matchPolicy;
    }


    public JSService(JSServiceArgs args) {
        super(args.name(), args.description(), args.handleSource(), args.modulesReplacements(), args.configuration(), args.mclient(), args.contextOptions());
        this.uri = args.uri();
        this.secured = args.secured();
        this.matchPolicy = args.matchPolicy();
    }

    public String uri() {
        return uri;
    }


    public boolean secured() {
        return secured;
    }

    public MATCH_POLICY matchPolicy() {
        return matchPolicy;
    }
}