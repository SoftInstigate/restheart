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

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class SimpleFileIdentityManager implements IdentityManager
{
    private static final Logger logger = LoggerFactory.getLogger(SimpleFileIdentityManager.class);

    private final Map<String, SimpleAccount> accounts;

    public SimpleFileIdentityManager(Map<String, Object> arguments)
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
        
        this.accounts = new HashMap<>();

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
        Object _users = conf.get("users");

        if (_users == null || !(_users instanceof List))
        {
            logger.error("wrong configuration file format.");
            throw new IllegalArgumentException("wrong configuration file format. missing mandatory users section");
        }

        List<Map<String, Object>> users = (List<Map<String, Object>>) _users;

        users.stream().forEach(u ->
        {
            Object _userid = u.get("userid");
            Object _password = u.get("password");
            Object _roles = u.get("roles");

            if (_userid == null || !(_userid instanceof String))
            {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the userid");
            }

            if (_password == null || !(_password instanceof String))
            {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the password");
            }

            if (_roles == null)
            {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the roles");
            }
            
            if ( !(_roles instanceof List))
            {
                throw new IllegalArgumentException("wrong configuration file format. a user entry roles argument is not an array");
            }

            String userid = (String) _userid;
            char[] password = ((String) _password).toCharArray();
            
            if (((List)_roles).stream().anyMatch(i -> !(i instanceof String)))
                throw new IllegalArgumentException("wrong configuration file format. a roles entry is wrong. they all must be strings");
                    
            Set<String> roles = new HashSet<>((List) _roles);

            SimpleAccount a = new SimpleAccount(userid, password, roles);

            this.accounts.put(userid, a);
        }
        );
    }

    @Override
    public Account verify(Account account)
    {
        return account;
    }

    @Override
    public Account verify(String id, Credential credential)
    {
        Account account = accounts.get(id);
        if (account != null && verifyCredential(account, credential))
        {
            return account;
        }

        return null;
    }

    @Override
    public Account verify(Credential credential)
    {
        // Auto-generated method stub
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential)
    {
        if (credential instanceof PasswordCredential && account instanceof SimpleAccount)
        {
            char[] password = ((PasswordCredential) credential).getPassword();
            char[] expectedPassword = accounts.get(account.getPrincipal().getName()).getCredentials().getPassword();

            return Arrays.equals(password, expectedPassword);
        }
        return false;
    }
}