/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
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
package org.restheart.security.impl;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 *
 * @author Jason Brown
 *
 * I don't know how well this will work in other environments, I built it specifically for mine.
 * The principal name isn't consistent across different users within our DC, so I added support for
 * multiple suffixes to be added to the username.  In addition, we have 3 domain controllers.  I
 * added support to list them so I don't have to wait for changes to propogate across all DCs before
 * the new user can log in.  Simply add the "adim" section to security.yml and switch then change
 * restheart.yml to use org.restheart.security.impl.ADIdentityManager instead of DBIdentityManager
 * or SimpleFileIdentityManager.  This still uses the file based access manager, so you'll need to
 * choose which AD roles you want to map to Admin and/or other users.  The code will use the each
 * DC listed, in order (so list your most stable or closest DC first).  It will use each suffix in
 * the principalNameSuffixes list in order as well, so list your most common one first.  It will
 * try all suffixes at one DC, before moving on to try all suffixes at the next DC.
 * <code>
## Config for AD Identity Manager
adim:
    - domainControllers: ldap://eastdc.example.com
      principalNameSuffixes: corp.example.com,example.com

 * </code>
 */
public final class ADIdentityManager extends AbstractSimpleSecurityManager implements IdentityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ADIdentityManager.class);

    private String[] ldapURLs = null;
    private String[] principalNameSuffixes = null;
    /**
     *
     * @param arguments
     * @throws FileNotFoundException
     */
    public ADIdentityManager(Map<String, Object> arguments) throws FileNotFoundException {
        init(arguments, "adim");
    }

    @Override
    Consumer<? super Map<String, Object>> consumeConfiguration() {
        return ci -> {
            LOGGER.info(ci.keySet().toString());
            Object _dc = ci.get("domainControllers");
            if (_dc == null || !(_dc instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. missing domainControllers property, should be a comma separated list of DCs to authenticate against.  E.g. 'ldap://eastdc.example.com,ldap://westdc.example.com'");
            }
            this.ldapURLs = ((String) _dc).split(",");

            Object _pns = ci.get("principalNameSuffixes");
            if (_pns == null || !(_pns instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. missing principalNameSuffixes property, should be a comma separated list of suffixes to add to end of username.  E.g. 'example.com,corp.example.com,example.net'");
            }
            this.principalNameSuffixes = ((String) _pns).split(",");
        };
    }

    @Override
    public Account verify(Account account) {
        return account;
    }

    @Override
    public Account verify(Credential credential) {
        return null;
    }

    @Override
    public Account verify(String username, Credential credential) {
        if (username == null || credential == null || !(credential instanceof PasswordCredential)){
            return null;
        }
        PasswordCredential pwc = (PasswordCredential) credential;

        try {
            Set<String> roles = getRoles(username, new String(pwc.getPassword()));
            Account acct = new SimpleAccount(username, pwc.getPassword(), roles);
            return acct;
        } catch (NamingException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    private Set<String> getRoles(String username, String password) throws NamingException {
        if (password == null || password.trim().length() == 0 || username == null || username.trim().length() == 0){
            return null;
        }

        for (String ldapURL : ldapURLs) {
            for (String pns : principalNameSuffixes) {
                ldapURL = ldapURL.trim();
                pns = pns.trim();
                String principalName = username + "@" + pns;

                Hashtable props = new Hashtable();
                props.put(Context.SECURITY_PRINCIPAL, principalName);
                props.put(Context.SECURITY_CREDENTIALS, password);


                props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                props.put(Context.PROVIDER_URL, ldapURL);
                try {
                    LdapContext ldc = new InitialLdapContext(props, null);
                    LOGGER.info("Connected to " + ldapURL + " as user " + principalName);
                    return getRoles(ldc, principalName, pns);
                } catch (javax.naming.CommunicationException e) {
                    LOGGER.warn("Failed to connect to " + ldapURL);
                } catch (NamingException e) {
                    LOGGER.warn("Failed to authenticate " + principalName);
                }
            }
        }
        LOGGER.error("Failed to connect to any specified DC with any user/suffix combination");
        throw new NamingException("Failed to connect to any specified DC with any user/suffix combination");
    }

    private Set<String> getRoles(LdapContext ldc, String principalName, String principalNameSuffix){
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setReturningAttributes(new String[]{"memberOf"});
        String searchString = "(&(objectClass=user)(userPrincipalName=" + principalName + "))";
        try{
            Set<String> roles = new HashSet<>();
            NamingEnumeration<SearchResult> results = ldc.search(toDCList(principalNameSuffix), searchString, sc);
            if (results.hasMore()){
                SearchResult res = results.next();
                System.out.println("res.getName = " + res.getName());
                Attributes attrs = res.getAttributes();
                if (attrs != null){
                    NamingEnumeration attrEnum = attrs.getAll();
                    while (attrEnum.hasMore()){
                        Attribute attr = (Attribute)attrEnum.next();
                        System.out.println("Attribute : " + attr.getID());
                        NamingEnumeration sa = attr.getAll();
                        while (sa.hasMore()){
                            String[] parts = ((String)sa.next()).split(",");
                            String[] kv = parts[0].split("=");
                            roles.add(kv[1]);
                        }
                    }
                }
            }
            return roles;
        }
        catch(NamingException e){
            LOGGER.error("Failed to lookup groups for user " + principalName);
        }
        return null;
    }

    private static String toDCList(String domainName)
    {
        StringBuilder sb = new StringBuilder();
        for (String p : domainName.split("\\."))
        {
            if (p.length() > 0) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append("DC=").append(p);
            }
        }
        return sb.toString();
    }
}
