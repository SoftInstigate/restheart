/*
 * uIAM - the IAM for microservices
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
package com.restheart.security.plugins.mechanisms;

import java.util.Set;
import org.restheart.security.plugins.authenticators.BaseAccount;


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
