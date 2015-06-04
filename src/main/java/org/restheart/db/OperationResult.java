/*
 * RESTHeart - the data REST API server
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
package org.restheart.db;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class OperationResult {
    private final int httpCode;
    private final Object etag;
    
    public OperationResult(int httpCode) {
        this.httpCode = httpCode;
        this.etag = null;
    }
    
    public OperationResult(int httpCode, Object etag) {
        this.httpCode = httpCode;
        this.etag = etag;
    }

    /**
     * @return the httpCode
     */
    public int getHttpCode() {
        return httpCode;
    }

    /**
     * @return the etag
     */
    public Object getEtag() {
        return etag;
    }
}