/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.authorizers;

import static org.restheart.plugins.ConfigurablePlugin.argValue;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.restheart.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.security.BaseAclPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;

/**
 * ACL Permission that specifies the conditions that are necessary to perform
 * the request
 *
 * The request is authorized if AclPermission.resolve() to true
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class FileAclPermission extends BaseAclPermission {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileAclPermission.class);

    private FileAclPermission(String predicate, Set<String> roles, int priority, Map<String, Object> raw) {
        super(predicate, roles, priority, raw);
    }

    /**
     * build acl permission from file definition
     *
     * @param seq
     * @param args
     * @throws ConfigurationException
     */
    static FileAclPermission build(Map<String, Object> args) throws ConfigurationException {
        var roles = new LinkedHashSet<String>();

        if (args.containsKey("role") && args.containsKey("roles")) {
            throw new ConfigurationException(
                    "Wrong permission: it specifies both 'role' and 'roles'; it requires just one or the other.");
        } else if (args.containsKey("role")) {
            roles.add(argValue(args, "role"));
        } else if (args.containsKey("roles")) {
            roles.addAll(argValue(args, "roles"));
        } else {
            throw new ConfigurationException("Wrong permission: does not specify 'role' or 'roles'.");
        }

        if (!args.containsKey("predicate")) {
            throw new ConfigurationException("Wrong permission: missing 'predicate'");
        }

        String predicate = argValue(args, "predicate");

        if (predicate == null) {
            throw new ConfigurationException("Wrong permission: 'predicate' cannot be null");
        }

        try {
            // check predicate
            PredicateParser.parse(predicate, FileAclPermission.class.getClassLoader());
        } catch (Throwable t) {
            throw new ConfigurationException("Wrong permission: invalid predicate: " + predicate, t);
        }

        int priority;

        if (args.containsKey("priority")) {
            priority = argValue(args, "priority");
        } else {
            LOGGER.warn("Predicate {} {} doesn't have priority; setting it to very low priority", roles, predicate);
            priority = Integer.MAX_VALUE; // very low priority
        }

        return new FileAclPermission(predicate, roles, priority, args);
    }

    public boolean allow(final HttpServerExchange exchange) {
        if (getPredicate() == null) {
            return false;
        } else {
            return AclPermissionsVarsInterpolator.interpolatePredicate(Request.of(exchange), getPredicate()).resolve(exchange);
        }
    }
}
