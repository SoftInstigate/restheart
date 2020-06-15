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
package org.restheart.idm;

import java.util.Set;


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
