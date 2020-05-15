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
package com.restheart.platform.jwt;

import java.util.Set;
import org.restheart.plugins.security.BaseAccount;


/**
 * Jwt Account
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JwtAccount extends BaseAccount {
    final private String jwtPayload;

    /**
     *
     * @param name
     * @param roles
     * @param jwtPayload
     */
    public JwtAccount(final String name, final Set<String> roles, String jwtPayload) {
        super(name, roles);
        this.jwtPayload = jwtPayload;
    }

    /**
     *
     * @return the jwtPayload
     */
    public String getJwtPayload() {
        return jwtPayload;
    }
    
    @Override
    public String toString() {
        return super.toString()
                .concat(" jwt=")
                .concat(jwtPayload);
    }
}
