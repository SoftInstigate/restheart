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
package org.restheart.idm;

import java.util.Set;


/**
 * Jwt Account
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JwtAccount extends BaseAccount {
    /**
     *
     */
    private static final long serialVersionUID = -2405615782892727187L;
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
