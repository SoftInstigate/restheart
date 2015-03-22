/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.hal.metadata;

import com.mongodb.DBObject;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SchemaCheckerMetadata {
    public static final String SC_ELEMENT_NAME = "schemaCheck";
    public static final String SC_CHECKER_ELEMENT_NAME = "name";
    public static final String SC_ARGS_ELEMENT_NAME = "args";

    private final String name;
    private final DBObject args;

    /**
     *
     * @param checker
     * @param args
     */
    public SchemaCheckerMetadata(String checker, DBObject args) {
        this.name = checker;
        this.args = args;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the args
     */
    public DBObject getArgs() {
        return args;
    }

    public static SchemaCheckerMetadata getFromJson(DBObject props) throws InvalidMetadataException {
        String name;
        DBObject args = null;

        Object _sc = props.get(SC_ELEMENT_NAME);

        if (_sc == null || !(_sc instanceof DBObject)) {
            throw new InvalidMetadataException((_sc == null ? "missing '" : "invalid '") + SC_ELEMENT_NAME + "' element. it must be a json object");
        }

        DBObject sc = (DBObject) _sc;

        Object _name = sc.get(SC_CHECKER_ELEMENT_NAME);

        if (_name == null || !(_name instanceof String)) {
            throw new InvalidMetadataException((_name == null ? "missing '" : "invalid '") + SC_CHECKER_ELEMENT_NAME + "' element. it must be of type $code");
        }

        name = (String) _name;

        Object _args = sc.get(SC_ARGS_ELEMENT_NAME);

        // args is optional
        if (_args != null && !(_args instanceof DBObject)) {
            throw new InvalidMetadataException("invalid '" + SC_ARGS_ELEMENT_NAME + "' element. it must be a json object");
        }

        args = (DBObject) _args;
        
        return new SchemaCheckerMetadata(name, args);

    }
}