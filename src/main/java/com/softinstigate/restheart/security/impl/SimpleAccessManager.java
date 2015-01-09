/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.softinstigate.restheart.security.impl;

import com.softinstigate.restheart.security.AccessManager;
import com.softinstigate.restheart.handlers.RequestContext;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Andrea Di Cesare
 */
public class SimpleAccessManager implements AccessManager {

    private static final Logger logger = LoggerFactory.getLogger(SimpleAccessManager.class);

    private HashMap<String, Set<Predicate>> acl;

    /**
     *
     * @param arguments
     */
    public SimpleAccessManager(Map<String, Object> arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("missing required arguments conf-file");
        }

        Object _confFilePath = arguments.getOrDefault("conf-file", "security.yml");

        if (_confFilePath == null || !(_confFilePath instanceof String)) {
            throw new IllegalArgumentException("missing required arguments conf-file");
        }

        String confFilePath = (String) _confFilePath;

        if (!confFilePath.startsWith("/")) {
            // this is to allow specifying the configuration file path relative to the jar (also working when running from classes)
            URL location = this.getClass().getProtectionDomain().getCodeSource().getLocation();
            File locationFile = new File(location.getPath());
            confFilePath = locationFile.getParent() + File.separator + confFilePath;
        }

        this.acl = new HashMap<>();

        FileInputStream fis = null;

        try {
            fis = new FileInputStream(new File(confFilePath));
            init((Map<String, Object>) new Yaml().load(fis));
        } catch (FileNotFoundException fnef) {
            throw new IllegalArgumentException("configuration file not found.", fnef);
        } catch (Throwable t) {
            throw new IllegalArgumentException("wrong configuration file format.", t);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ex) {
                    logger.warn("error closing the configuration file {}", confFilePath);
                }
            }
        }
    }

    private void init(Map<String, Object> conf) {
        Object _users = conf.get("permissions");

        if (_users == null || !(_users instanceof List)) {
            throw new IllegalArgumentException("wrong configuration file format. missing mandatory permissions section.");
        }

        List<Map<String, Object>> users = (List<Map<String, Object>>) _users;

        users.stream().forEach(u -> {
            Object _role = u.get("role");
            Object _predicate = u.get("predicate");

            if (_role == null || !(_role instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry is missing the role");
            }

            String role = (String) _role;

            if (_predicate == null || !(_predicate instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry is missing the predicate");
            }

            Predicate predicate = null;

            try {
                predicate = PredicateParser.parse((String) _predicate, this.getClass().getClassLoader());
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong configuration file format. wrong predictate" + (String) _predicate, t);
            }

            Set<Predicate> perms = getAcl().get(role);

            if (perms == null) {
                perms = new HashSet<>();
                getAcl().put(role, perms);
            }

            perms.add(predicate);
        }
        );
    }

    /**
     *
     * @param exchange
     * @param context
     * @return
     */
    @Override
    public boolean isAllowed(HttpServerExchange exchange, RequestContext context) {
        Account account = exchange.getSecurityContext().getAuthenticatedAccount();

        if (account == null && getAcl().get("$unauthenticated") != null) {
            // not authenticated, let's get the permission set given to the $unauthenticated group
            return getAcl() == null ? false : getAcl().get("$unauthenticated").stream().anyMatch(p -> p.resolve(exchange));
        } else if (account != null && account.getRoles() != null) {
            return account.getRoles().stream().anyMatch(r -> getAcl() == null ? false : getAcl().get(r).stream().anyMatch(p -> p.resolve(exchange)));
        } else {
            return false;
        }
    }

    /**
     * @return the acl
     */
    @Override
    public HashMap<String, Set<Predicate>> getAcl() {
        return acl;
    }
}
