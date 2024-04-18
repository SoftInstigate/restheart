/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
package org.restheart.mongodb.security;

import java.util.List;
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;

/**
 * Forbids all requests to Mongo API that use an blacklisted operator in the filter query paramter
 */
@RegisterPlugin(name = "filterOperatorsBlacklist",
    description = "forbids requests containing filter qparameter using operator in blacklist",
    enabledByDefault = false)
public class FilterOperatorsBlacklist implements Initializer {
    @Inject("acl-registry")
    private ACLRegistry registry;

    @Inject("config")
    private Map<String, Object> config;

    private List<String> blacklist;

    @OnInit
    public void onInit() {
        this.blacklist = arg(config, "blacklist");

        if (!blacklist.stream().allMatch(o -> o.startsWith("$"))) {
            throw new IllegalArgumentException("All entries of blacklist must start with $");
        }
    }

    @Override
    public void init() {
        this.registry.registerVeto(exchange -> {
            var request = Request.of(exchange);

            if (request instanceof MongoRequest mreq) {
                return contains(mreq.getFiltersDocument(), blacklist);
            } else {
                return false; // don't veto
            }
        });
    }

    /**
     *
     * @param doc
     * @param blacklist
     * @return true if doc contains any operator in blacklist
     */
    private boolean contains(BsonDocument doc, List<String> blacklist) {
        if (doc  == null) {
            return true;
        }

        var found = doc.keySet().stream().anyMatch(k -> blacklist.contains(k));

        if (found) {
            return true;
        } else {
            var foundInSubDocs = doc.keySet().stream()
                .filter(key -> doc.get(key).isDocument())
                .map(key -> doc.get(key).asDocument())
                .anyMatch(sdoc -> contains(sdoc, blacklist));

            if (foundInSubDocs) {
                return true;
            } else {
                return doc.keySet().stream()
                    .filter(key -> doc.get(key).isArray())
                    .map(key -> doc.get(key).asArray())
                    .anyMatch(array -> contains(array, blacklist));
            }
        }
    }

    private boolean contains(BsonArray array, List<String> blacklist) {
        if (array == null) {
            return false;
        }

        var foundInDocs = array.stream()
            .filter(el -> el.isDocument())
            .map(el -> el.asDocument())
            .anyMatch(doc -> contains(doc, blacklist));

        if (foundInDocs) {
            return true;
        } else {
            return array.stream()
                .filter(el -> el.isArray())
                .map(el -> el.asArray())
                .anyMatch(subArray -> contains(subArray, blacklist));
        }
    }
}
