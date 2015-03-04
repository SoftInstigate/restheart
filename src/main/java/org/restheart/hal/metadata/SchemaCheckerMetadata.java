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
import javax.script.ScriptException;
import org.bson.types.Code;
import static org.restheart.hal.metadata.RepresentationTransformer.RTL_CODE_ELEMENT_NAME;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SchemaCheckerMetadata extends ScriptMetadata {
    public static final String SCHEMA_ELEMENT_NAME = "schemaCheck";

    private final Code code;

    /**
     *
     * @param code
     */
    public SchemaCheckerMetadata(Code code) {
        super(code.getCode());
        this.code = code;
    }

    /**
     * @return the code
     */
    public Code getCode() {
        return code;
    }

    public static SchemaCheckerMetadata getFromJson(DBObject props, boolean checkCode) throws InvalidMetadataException {
        Code code;

        if (checkCode) {
            code = checkCodeFromJson((DBObject) props);
        } else {
            Object _code = props.get(SCHEMA_ELEMENT_NAME);

            if (_code == null || !(_code instanceof Code)) {
                throw new InvalidMetadataException((_code == null ? "missing '" : "invalid '") + RTL_CODE_ELEMENT_NAME + "' element. it must be of type $code");
            }

            code = (Code) _code;
        }

        return new SchemaCheckerMetadata(code);
    }

    private static Code checkCodeFromJson(DBObject props) throws InvalidMetadataException {
        Object _code = props.get(SCHEMA_ELEMENT_NAME);

        if (_code == null || !(_code instanceof Code)) {
            throw new InvalidMetadataException((_code == null ? "missing '" : "invalid '") + RTL_CODE_ELEMENT_NAME + "' element. it must be of type $code");
        }

        Code code = (Code) _code;

        // check code
        try {
            evaluate(code.getCode(), getTestBindings());
        } catch (ScriptException se) {
            throw new InvalidMetadataException("invalid javascript code", se);
        }

        return code;
    }
}
