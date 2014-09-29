/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.security.impl;

import com.softinstigate.restheart.security.AccessManager;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
 * @author uji
 */
public class SimpleAccessManager implements AccessManager
{
    private static final Logger logger = LoggerFactory.getLogger(SimpleAccessManager.class);

    private HashMap<String, Set<SimplePermission>> acl;
    
    public SimpleAccessManager(Map<String, Object> arguments)
    {
        if (arguments == null)
        {
            logger.error("missing required argument conf-file");
            throw new IllegalArgumentException("\"missing required arguments conf-file");
        }

        Object _confFilePath = arguments.getOrDefault("conf-file", "security.yml");

        if (_confFilePath == null || !(_confFilePath instanceof String))
        {
            logger.error("missing required argument conf-file");
            throw new IllegalArgumentException("\"missing required arguments conf-file");
        }

        String confFilePath = (String) _confFilePath;

        this.acl = new HashMap<>();

        try
        {
            init((Map<String, Object>) new Yaml().load(new FileInputStream(new File(confFilePath))));
        }
        catch (FileNotFoundException fnef)
        {
            logger.error("configuration file not found.", fnef);
            throw new IllegalArgumentException("configuration file not found.", fnef);
        }
        catch (Throwable t)
        {
            logger.error("wrong configuration file format.", t);
            throw new IllegalArgumentException("wrong configuration file format.", t);
        }
    }

    private void init(Map<String, Object> conf)
    {
        Object _users = conf.get("permissions");

        if (_users == null || !(_users instanceof List))
        {
            logger.error("wrong configuration file format. missing mandatory permissions section.");
            throw new IllegalArgumentException("wrong configuration file format. missing mandatory permissions section.");
        }

        List<Map<String, Object>> users = (List<Map<String, Object>>) _users;

        users.stream().forEach(u ->
        {
            Object _role = u.get("role");
            Object _accessLevel = u.get("access-level");
            Object _urls = u.get("urls");
            Object _contentCondition = u.get("content-condition");

            if (_role == null || !(_role instanceof String))
            {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry is missing the role");
            }

            if (_accessLevel == null || !(_accessLevel instanceof String))
            {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry is missing the access-level");
            }

            if (_urls == null)
            {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry is missing the urls");
            }

            if (!(_urls instanceof List))
            {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry urls argument is not an array");
            }
            
            if (_contentCondition != null && !(_contentCondition instanceof String))
            {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry has a not valid content-condition. it has to be a string");
            }

            String role = (String) _role;
            SimplePermission.ACCESS_LEVEL accessLevel = SimplePermission.ACCESS_LEVEL.valueOf(((String) _accessLevel));
            String contentCondition = (String) _contentCondition;
            
            if (((List) _urls).stream().anyMatch(i -> !(i instanceof String)))
            {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry is wrong. all urls must be regular expressions");
            }
            
            Set<String> urls = new HashSet<>((List)_urls);

            
            Set<SimplePermission> perms = acl.get(role);
            
            if (perms == null)
            {
                perms = new HashSet<>();
                acl.put(role, perms);
            }
            
            perms.add(new SimplePermission(role, accessLevel, urls, contentCondition));
        }
        );
    }

    @Override
    public boolean isAllowed(HttpServerExchange exchange, RequestContext context)
    {
        Account account = exchange.getSecurityContext().getAuthenticatedAccount();
        
        return account.getRoles().stream().anyMatch(r -> acl == null ? false : acl.get(r).stream().anyMatch(p -> p.doesAllow(exchange, context)));
    }
}