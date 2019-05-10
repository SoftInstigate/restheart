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
public class HookMetadata {

    public final static String ROOT_KEY = "hooks";
    public final static String NAME_KEY = "name";
    public final static String CONF_ARGS_KEY = "args";
    public final static String ARGS_KEY = "args";

    public static BsonValue getProps(BsonDocument props) {
        return props == null
                ? null
                : props.containsKey(ROOT_KEY)
                ? props.get(ROOT_KEY)
                : null;
    }

    public static List<HookMetadata> getFromJson(BsonDocument props)
            throws InvalidMetadataException {
        BsonValue _scs = getProps(props);

        if (_scs == null || !_scs.isArray()) {
            throw new InvalidMetadataException(
                    (_scs == null ? "missing '" : "invalid '")
                    + ROOT_KEY
                    + "' element. it must be a json array");
        }

        BsonArray scs = _scs.asArray();

        List<HookMetadata> ret = new ArrayList<>();

        for (BsonValue o : scs) {
            if (o.isDocument()) {
                ret.add(getSingleFromJson(o.asDocument()));
            } else {
                throw new InvalidMetadataException("invalid '"
                        + ROOT_KEY
                        + "'. Array elements must be json objects");
            }
        }

        return ret;
    }

    private static HookMetadata getSingleFromJson(BsonDocument props)
            throws InvalidMetadataException {
        BsonValue _name = props.get(NAME_KEY);

        if (_name == null
                || !(_name.isString())) {
            throw new InvalidMetadataException(
                    (_name == null
                            ? "missing '"
                            : "invalid '")
                    + NAME_KEY
                    + "' element. it must be of type String");
        }

        String name = _name.asString().getValue();

        // args is optional
        return new HookMetadata(name, props.get(ARGS_KEY));
    }

    private final String name;
    private final BsonValue args;

    /**
     *
     * @param name
     * @param args
     */
    public HookMetadata(String name, BsonValue args) {
        this.name = name;
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
    public BsonValue getArgs() {
        return args;
    }

}
