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
package org.restheart.hal.metadata;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestChecker {
    public final static String ROOT_KEY = "checkers";
    public final static String NAME_KEY = "name";
    public final static String ARGS_KEY = "args";
    public final static String SKIP_NOT_SUPPORTED = "skipNotSupported";

    private final String name;
    private final DBObject args;
    private final boolean skipNotSupported;

    /**
     *
     * @param checker
     * @param args
     */
    public RequestChecker(String checker, DBObject args) {
        this.name = checker;
        this.args = args;
        this.skipNotSupported = false;
    }

    /**
     *
     * @param checker
     * @param args
     * @param skipNotSupported false if the checker should fail if it does not
     * support the request
     */
    public RequestChecker(String checker, DBObject args, boolean skipNotSupported) {
        this.name = checker;
        this.args = args;
        this.skipNotSupported = skipNotSupported;
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

    public static Object getProps(DBObject props) {
        return props.get(ROOT_KEY);
    }

    public static List<RequestChecker> getFromJson(DBObject props) throws InvalidMetadataException {
        Object _scs = getProps(props);

        if (_scs == null || !(_scs instanceof BasicDBList)) {
            throw new InvalidMetadataException((_scs == null ? "missing '" : "invalid '") + ROOT_KEY + "' element. it must be a json array");
        }

        BasicDBList scs = (BasicDBList) _scs;

        List<RequestChecker> ret = new ArrayList<>();

        for (Object o : scs) {
            if (o instanceof DBObject) {
                ret.add(getSingleFromJson((DBObject) o));
            } else {
                throw new InvalidMetadataException("invalid '" + ROOT_KEY + "'. Array elements must be json objects");
            }
        }

        return ret;
    }

    private static RequestChecker getSingleFromJson(DBObject props) throws InvalidMetadataException {
        Object _name = props.get(NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new InvalidMetadataException((_name == null ? "missing '" : "invalid '") + NAME_KEY + "' element. it must be of type String");
        }

        String name = (String) _name;

        Object _args = props.get(ARGS_KEY);

        // args is optional
        if (_args != null && !(_args instanceof DBObject)) {
            throw new InvalidMetadataException("invalid '" + ARGS_KEY + "' element. it must be a json object");
        }

        DBObject args = (DBObject) _args;

        Object _skipNotSupported = props.get(SKIP_NOT_SUPPORTED);

        Boolean skipNotSupported;

        // failNotSupported is optional
        if (_skipNotSupported == null) {
            skipNotSupported = false;
        } else if (!(_skipNotSupported instanceof Boolean)) {
            throw new InvalidMetadataException("invalid '" + ARGS_KEY + "' element. it must be boolean");
        } else {
            skipNotSupported = (Boolean) _skipNotSupported;
        }

        return new RequestChecker(name, args, skipNotSupported);
    }

    /**
     * @return true if the checker must skip the requests that it does not
     * support
     */
    public boolean skipNotSupported() {
        return skipNotSupported;
    }
}
