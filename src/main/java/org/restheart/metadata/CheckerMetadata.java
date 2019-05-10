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
package org.restheart.metadata;

import java.util.ArrayList;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.metadata.InvalidMetadataException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CheckerMetadata {

    public final static String ROOT_KEY = "checkers";
    public final static String NAME_KEY = "name";
    public final static String ARGS_KEY = "args";
    public final static String SKIP_NOT_SUPPORTED = "skipNotSupported";

    public static BsonValue getProps(BsonDocument props) {
        return props.get(ROOT_KEY);
    }

    public static List<CheckerMetadata> getFromJson(BsonDocument props)
            throws InvalidMetadataException {
        BsonValue _scs = getProps(props);

        if (_scs == null || !_scs.isArray()) {
            throw new InvalidMetadataException(
                    (_scs == null ? "missing '" : "invalid '")
                    + ROOT_KEY
                    + "' element. it must be a json array");
        }

        BsonArray scs = _scs.asArray();

        List<CheckerMetadata> ret = new ArrayList<>();

        for (BsonValue o : scs.getValues()) {
            if (o.isDocument()) {
                ret.add(getSingleFromJson(o.asDocument()));
            } else {
                throw new InvalidMetadataException(
                        "invalid '"
                        + ROOT_KEY
                        + "'. Array elements must be json objects");
            }
        }

        return ret;
    }

    private static CheckerMetadata getSingleFromJson(BsonDocument props)
            throws InvalidMetadataException {
        BsonValue _name = props.get(NAME_KEY);

        if (_name == null || !_name.isString()) {
            throw new InvalidMetadataException(
                    (_name == null ? "missing '" : "invalid '")
                    + NAME_KEY
                    + "' element. it must be of type String");
        }

        String name = _name.asString().getValue();

        BsonValue _args = props.get(ARGS_KEY);

        // args is optional
        if (_args != null
                && !_args.isNull()
                && !(_args.isArray()
                || _args.isDocument())) {
            throw new InvalidMetadataException(
                    "invalid '"
                    + ARGS_KEY
                    + "' element. it must be a json object or array");
        }

        BsonValue _skipNotSupported = props.get(SKIP_NOT_SUPPORTED);

        Boolean skipNotSupported;

        // failNotSupported is optional
        if (_skipNotSupported == null) {
            skipNotSupported = false;
        } else if (!(_skipNotSupported.isBoolean())) {
            throw new InvalidMetadataException(
                    "invalid '"
                    + SKIP_NOT_SUPPORTED
                    + "' element. it must be boolean");
        } else {
            skipNotSupported = _skipNotSupported.asBoolean().getValue();
        }

        return new CheckerMetadata(name, _args, skipNotSupported);
    }

    private final String name;
    private final BsonValue args;
    private final boolean skipNotSupported;

    /**
     *
     * @param checker
     * @param args
     */
    public CheckerMetadata(String checker, BsonArray args) {
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
    public CheckerMetadata(
            String checker,
            BsonValue args,
            boolean skipNotSupported) {
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
    public BsonValue getArgs() {
        return args;
    }

    /**
     * @return true if the checker must skip the requests that it does not
     * support
     */
    public boolean skipNotSupported() {
        return skipNotSupported;
    }
}
