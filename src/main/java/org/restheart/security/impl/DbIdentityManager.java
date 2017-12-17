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

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * This class implements the basic DbIdentityManager.
 * The accountIdTrasformer method is an identity, so it works like before.
 *
 * @author mturatti
 */
public class DbIdentityManager extends AbstractDbIdentityManager {

    public DbIdentityManager(Map<String, Object> arguments) throws FileNotFoundException, UnsupportedEncodingException {
        super(arguments);
    }

    @Override
    protected String accountIdTrasformer(String id) {
        return id;
    }

}
