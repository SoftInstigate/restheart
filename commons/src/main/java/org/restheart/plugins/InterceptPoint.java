/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.plugins;

/**
 * Defines the intercept point of an Interceptor
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public enum InterceptPoint {
    /**
     * intercept the request before authentication occurs
     */
    REQUEST_BEFORE_AUTH,
    
    /**
     * intercept the request after authentication occurs 
     */
    REQUEST_AFTER_AUTH,
        
    /**
     * intercept the response and executes blocking the response
     */
    RESPONSE,
    
    /**
     * intercept the response and executes asynchronously with the response
     */
    RESPONSE_ASYNC
}
