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
import io.undertow.security.idm.PasswordCredential;
import java.security.Principal;
import java.util.Set;

/**
 *
 * @author uji
 */
public class SimpleAccount implements Account
{
    private Principal principal;
    private PasswordCredential credential;
    private Set<String> roles;
    
    public SimpleAccount(String name, char[] password, Set<String> roles)
    {
        if (name == null)
            throw new IllegalArgumentException("argument principal cannot be null");
        
        if (password == null)
            throw new IllegalArgumentException("argument password cannot be null");
        
        if (roles == null || roles.isEmpty())
            throw new IllegalArgumentException("argument roles cannot be null nor empty");
        
        
        this.principal = new SimplePrincipal(name);
        this.credential = new PasswordCredential(password);
        this.roles = roles;
    }

    @Override
    public Principal getPrincipal()
    {
        return principal;
    }
    
    public PasswordCredential getCredentials()
    {
        return credential;
    }

    @Override
    public Set<String> getRoles()
    {
        return roles;
    }
}