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

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Relationship {

    private static final Logger LOGGER = LoggerFactory.getLogger(Relationship.class);

    public enum TYPE {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    };

    public enum ROLE {
        OWNING,
        INVERSE
    };

    public static final String RELATIONSHIPS_ELEMENT_NAME = "rels";
    public static final String REL_ELEMENT_NAME = "rel";
    public static final String TYPE_ELEMENT_NAME = "type";
    public static final String ROLE_ELEMENT_NAME = "role";
    public static final String TARGET_DB_ELEMENT_NAME = "target-db";
    public static final String TARGET_COLLECTION_ELEMENT_NAME = "target-coll";
    public static final String REF_ELEMENT_NAME = "ref-field";

    private final String rel;
    private final TYPE type;
    private final ROLE role;
    private final String targetDb;
    private final String targetCollection;
    private final String referenceField;

    /**
     *
     * @param rel
     * @param type
     * @param role
     * @param targetDb
     * @param targetCollection
     * @param referenceField
     */
    public Relationship(String rel, TYPE type, ROLE role, String targetDb, String targetCollection, String referenceField) {
        this.rel = rel;
        this.type = type;
        this.role = role;
        this.targetDb = targetDb;
        this.targetCollection = targetCollection;
        this.referenceField = referenceField;
    }

    /**
     *
     * @param rel
     * @param type
     * @param role
     * @param targetDb
     * @param targetCollection
     * @param referenceField
     * @throws InvalidMetadataException
     */
    public Relationship(String rel, String type, String role, String targetDb, String targetCollection, String referenceField) throws InvalidMetadataException {
        this.rel = rel;

        try {
            this.type = TYPE.valueOf(type);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("invalid type value: " + type + ". valid values are " + Arrays.toString(TYPE.values()), iae);
        }

        try {
            this.role = ROLE.valueOf(role);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("invalid role value " + role + ". valid values are " + Arrays.toString(ROLE.values()), iae);
        }

        this.targetDb = targetDb;
        this.targetCollection = targetCollection;
        this.referenceField = referenceField;
    }

    /**
     *
     * @param collProps
     * @return
     * @throws InvalidMetadataException
     */
    public static List<Relationship> getFromJson(DBObject collProps) throws InvalidMetadataException {
        if (collProps == null) {
            return null;
        }

        ArrayList<Relationship> ret = new ArrayList<>();

        Object _rels = collProps.get(RELATIONSHIPS_ELEMENT_NAME);

        if (_rels == null) {
            return ret;
        }

        if (!(_rels instanceof BasicDBList)) {
            throw new InvalidMetadataException("element 'relationships' is not an array list." + _rels);
        }

        BasicDBList rels = (BasicDBList) _rels;

        for (Object _rel : rels.toArray()) {
            if (!(_rel instanceof DBObject)) {
                throw new InvalidMetadataException("element 'relationships' is not valid." + _rel);
            }

            DBObject rel = (DBObject) _rel;
            ret.add(getRelFromJson(rel));
        }

        return ret;
    }

    private static Relationship getRelFromJson(DBObject content) throws InvalidMetadataException {
        Object _rel = content.get(REL_ELEMENT_NAME);
        Object _type = content.get(TYPE_ELEMENT_NAME);
        Object _role = content.get(ROLE_ELEMENT_NAME);
        Object _targetDb = content.get(TARGET_DB_ELEMENT_NAME);
        Object _targetCollection = content.get(TARGET_COLLECTION_ELEMENT_NAME);
        Object _referenceField = content.get(REF_ELEMENT_NAME);

        if (_rel == null || !(_rel instanceof String)) {
            throw new InvalidMetadataException((_rel == null ? "missing " : "invalid ") + REL_ELEMENT_NAME + " element.");
        }

        if (_type == null || !(_type instanceof String)) {
            throw new InvalidMetadataException((_type == null ? "missing " : "invalid ") + TYPE_ELEMENT_NAME + " element.");
        }

        if (_role == null || !(_role instanceof String)) {
            throw new InvalidMetadataException((_role == null ? "missing " : "invalid ") + ROLE_ELEMENT_NAME + " element.");
        }

        if (_targetDb != null && !(_targetDb instanceof String)) {
            throw new InvalidMetadataException("invalid " + TARGET_DB_ELEMENT_NAME + " field.");
        }

        if (_targetCollection == null || !(_targetCollection instanceof String)) {
            throw new InvalidMetadataException((_targetCollection == null ? "missing " : "invalid ") + TARGET_COLLECTION_ELEMENT_NAME + " element.");
        }

        if (_referenceField == null || !(_referenceField instanceof String)) {
            throw new InvalidMetadataException((_referenceField == null ? "missing " : "invalid ") + REF_ELEMENT_NAME + " element.");
        }

        String rel = (String) _rel;
        String type = (String) _type;
        String role = (String) _role;
        String targetDb = (String) _targetDb;
        String targetCollection = (String) _targetCollection;
        String referenceField = (String) _referenceField;

        return new Relationship(rel, type, role, targetDb, targetCollection, referenceField);
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param data
     * @return
     * @throws IllegalArgumentException
     * @throws org.restheart.hal.UnsupportedDocumentIdException
     */
    public String getRelationshipLink(RequestContext context, String dbName, String collName, DBObject data) throws IllegalArgumentException, UnsupportedDocumentIdException {
        Object _referenceValue = getReferenceFieldValue(referenceField, data);

        String db = (targetDb == null ? dbName : targetDb);

        // check _referenceValue
        if (role == ROLE.OWNING) {
            if (_referenceValue == null) {
                return null; // the reference field is missing or it value is null => do not generate a link
            }

            if (type == TYPE.ONE_TO_ONE || type == TYPE.MANY_TO_ONE) {
                Object id = _referenceValue;

                // can be a BasicDBList if ref-field is a json path expression
                if (id instanceof BasicDBList && ((BasicDBList) id).size() == 1) {
                    id = ((BasicDBList) id).get(0);
                }

                return URLUtils.getUriWithDocId(context, db, targetCollection, id);
            } else {
                if (!(_referenceValue instanceof BasicDBList)) {
                    throw new IllegalArgumentException("in resource " + dbName + "/" + collName + "/" + data.get("_id")
                            + " the " + type.name() + " relationship ref-field " + this.referenceField + " should be an array, but is " + _referenceValue);
                }

                Object[] ids = ((BasicDBList) _referenceValue).toArray();
                return URLUtils.getUriWithFilterMany(context, db, targetCollection, ids);
            }
        } else {
            // INVERSE
            Object id = data.get("_id");

            if (type == TYPE.ONE_TO_ONE || type == TYPE.ONE_TO_MANY) {
                return URLUtils.getUriWithFilterOne(context, db, targetCollection, referenceField, id);
            } else if (type == TYPE.MANY_TO_ONE || type == TYPE.MANY_TO_MANY) {
                return URLUtils.getUriWithFilterManyInverse(context, db, targetCollection, referenceField, id);
            }
        }

        LOGGER.debug("returned null link. this = {}, data = {}", this, data);
        return null;
    }

    /**
     *
     * @returns the reference field value, either it is an object or, in case
     * referenceField is a json path, a BasicDBObject
     *
     *
     */
    private Object getReferenceFieldValue(String referenceField, DBObject data) throws IllegalArgumentException {
        if (referenceField.startsWith("$.")) {
            // it is a json path expression

            List<Optional<Object>> objs;

            try {
                objs = JsonUtils.getPropsFromPath(data, referenceField);
            } catch (IllegalArgumentException ex) {
                return null;
            }

            if (objs == null) {
                return null;
            }
            
            BasicDBList ret = new BasicDBList();

            objs.stream().forEach((Optional<Object> obj) -> {
                if (obj != null && obj.isPresent()) {
                    ret.add(obj.get());
                } else {
                    LOGGER.debug("cound not get the value of the reference field " + referenceField + " from " + data.toString() + "\nThe json path expression resolved to " + objs.toString());
                    throw new IllegalArgumentException("xxxx ref-field json path expression resolved to " + objs.toString());
                }
            });

            return ret;
        } else {
            return data.get(referenceField);
        }
    }

    /**
     * @return the rel
     */
    public String getRel() {
        return rel;
    }

    /**
     * @return the type
     */
    public TYPE getType() {
        return type;
    }

    /**
     * @return the role
     */
    public ROLE getRole() {
        return role;
    }

    /**
     * @return the targetDb
     */
    public String getTargetDb() {
        return targetDb;
    }

    /**
     * @return the targetCollection
     */
    public String getTargetCollection() {
        return targetCollection;
    }

    /**
     * @return the referenceField
     */
    public String getReferenceField() {
        return referenceField;
    }
}
