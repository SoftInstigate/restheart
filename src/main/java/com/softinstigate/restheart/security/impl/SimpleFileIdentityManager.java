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

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

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
public class SimpleFileIdentityManager implements IdentityManager {

    private static final Logger logger = LoggerFactory.getLogger(SimpleFileIdentityManager.class);

    private final Map<String, SimpleAccount> accounts;

    /**
     *
     * @param arguments
     */
    public SimpleFileIdentityManager(Map<String, Object> arguments) {
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

        this.accounts = new HashMap<>();

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
        Object _users = conf.get("users");

        if (_users == null || !(_users instanceof List)) {
            throw new IllegalArgumentException("wrong configuration file format. missing mandatory users section");
        }

        List<Map<String, Object>> users = (List<Map<String, Object>>) _users;

        users.stream().forEach(u -> {
            Object _userid = u.get("userid");
            Object _password = u.get("password");
            Object _roles = u.get("roles");

            if (_userid == null || !(_userid instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the userid");
            }

            if (_password == null || !(_password instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the password");
            }

            if (_roles == null) {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the roles");
            }

            if (!(_roles instanceof List)) {
                throw new IllegalArgumentException("wrong configuration file format. a user entry roles argument is not an array");
            }

            String userid = (String) _userid;
            char[] password = ((String) _password).toCharArray();

            if (((List) _roles).stream().anyMatch(i -> !(i instanceof String))) {
                throw new IllegalArgumentException("wrong configuration file format. a roles entry is wrong. they all must be strings");
            }

            Set<String> roles = new HashSet<>((List) _roles);

            SimpleAccount a = new SimpleAccount(userid, password, roles);

            this.accounts.put(userid, a);
        }
        );
    }

    @Override
    public Account verify(Account account) {
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
        Account account = accounts.get(id);
        if (account != null && verifyCredential(account, credential)) {
            return account;
        }

        return null;
    }

    @Override
    public Account verify(Credential credential) {
        // Auto-generated method stub
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential) {
        if (credential instanceof PasswordCredential && account instanceof SimpleAccount) {
            char[] password = ((PasswordCredential) credential).getPassword();
            char[] expectedPassword = accounts.get(account.getPrincipal().getName()).getCredentials().getPassword();

            return Arrays.equals(password, expectedPassword);
        }
        return false;
    }
}
