/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.representation.InvalidMetadataException;
import org.restheart.representation.UnsupportedDocumentIdException;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Relationship {

    private static final Logger LOGGER = LoggerFactory.getLogger(Relationship.class);

    /**
     *
     */
    public static final String RELATIONSHIPS_ELEMENT_NAME = "rels";

    /**
     *
     */
    public static final String REL_ELEMENT_NAME = "rel";

    /**
     *
     */
    public static final String TYPE_ELEMENT_NAME = "type";

    /**
     *
     */
    public static final String ROLE_ELEMENT_NAME = "role";

    /**
     *
     */
    public static final String TARGET_DB_ELEMENT_NAME = "target-db";

    /**
     *
     */
    public static final String TARGET_COLLECTION_ELEMENT_NAME = "target-coll";

    /**
     *
     */
    public static final String REF_ELEMENT_NAME = "ref-field";

    /**
     *
     * @param collProps
     * @return
     * @throws InvalidMetadataException
     */
    public static List<Relationship> getFromJson(BsonDocument collProps)
            throws InvalidMetadataException {
        if (collProps == null) {
            return null;
        }

        ArrayList<Relationship> ret = new ArrayList<>();

        BsonValue _rels = collProps.get(RELATIONSHIPS_ELEMENT_NAME);

        if (_rels == null) {
            return ret;
        }

        if (!_rels.isArray()) {
            throw new InvalidMetadataException(
                    "element '"
                    + RELATIONSHIPS_ELEMENT_NAME
                    + "' is not an array list."
                    + _rels);
        }

        BsonArray rels = _rels.asArray();

        for (BsonValue _rel : rels.getValues()) {
            if (!_rel.isDocument()) {
                throw new InvalidMetadataException(
                        "element '"
                        + RELATIONSHIPS_ELEMENT_NAME
                        + "' is not valid."
                        + _rel);
            }

            BsonDocument rel = _rel.asDocument();
            ret.add(getRelFromJson(rel));
        }

        return ret;
    }

    private static Relationship getRelFromJson(BsonDocument content)
            throws InvalidMetadataException {
        BsonValue _rel = content.get(REL_ELEMENT_NAME);
        BsonValue _type = content.get(TYPE_ELEMENT_NAME);
        BsonValue _role = content.get(ROLE_ELEMENT_NAME);
        BsonValue _targetDb = content.get(TARGET_DB_ELEMENT_NAME);
        BsonValue _targetCollection = content.get(TARGET_COLLECTION_ELEMENT_NAME);
        BsonValue _referenceField = content.get(REF_ELEMENT_NAME);

        if (_rel == null || !_rel.isString()) {
            throw new InvalidMetadataException(
                    (_rel == null ? "missing " : "invalid ")
                    + REL_ELEMENT_NAME
                    + " element.");
        }

        if (_type == null || !_type.isString()) {
            throw new InvalidMetadataException(
                    (_type == null ? "missing " : "invalid ")
                    + TYPE_ELEMENT_NAME
                    + " element.");
        }

        if (_role == null || !_role.isString()) {
            throw new InvalidMetadataException(
                    (_role == null ? "missing " : "invalid ")
                    + ROLE_ELEMENT_NAME
                    + " element.");
        }

        if (_targetDb != null && !_targetDb.isString()) {
            throw new InvalidMetadataException(
                    "invalid "
                    + TARGET_DB_ELEMENT_NAME
                    + " field.");
        }

        if (_targetCollection == null || !_targetCollection.isString()) {
            throw new InvalidMetadataException(
                    (_targetCollection == null ? "missing " : "invalid ")
                    + TARGET_COLLECTION_ELEMENT_NAME
                    + " element.");
        }

        if (_referenceField == null || !_referenceField.isString()) {
            throw new InvalidMetadataException(
                    (_referenceField == null ? "missing " : "invalid ")
                    + REF_ELEMENT_NAME
                    + " element.");
        }

        String rel = _rel.asString().getValue();
        String type = _type.asString().getValue();
        String role = _role.asString().getValue();
        String targetDb = _targetDb == null
                ? null
                : _targetDb.asString().getValue();

        String targetCollection = _targetCollection.asString().getValue();
        String referenceField = _referenceField.asString().getValue();

        return new Relationship(
                rel,
                type,
                role,
                targetDb,
                targetCollection,
                referenceField);
    }

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
    public Relationship(
            String rel,
            TYPE type,
            ROLE role,
            String targetDb,
            String targetCollection,
            String referenceField) {
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
    public Relationship(
            String rel,
            String type,
            String role,
            String targetDb,
            String targetCollection,
            String referenceField)
            throws InvalidMetadataException {
        this.rel = rel;

        try {
            this.type = TYPE.valueOf(type);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException(
                    "invalid type value: "
                    + type
                    + ". valid values are "
                    + Arrays.toString(TYPE.values()),
                    iae);
        }

        try {
            this.role = ROLE.valueOf(role);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException(
                    "invalid role value "
                    + role
                    + ". valid values are "
                    + Arrays.toString(ROLE.values()),
                    iae);
        }

        this.targetDb = targetDb;
        this.targetCollection = targetCollection;
        this.referenceField = referenceField;
    }

    /**
     *
     * @param request
     * @param dbName
     * @param collName
     * @param data
     * @return
     * @throws IllegalArgumentException
     * @throws org.restheart.representation.UnsupportedDocumentIdException
     */
    public String getRelationshipLink(
            MongoRequest request,
            String dbName,
            String collName,
            BsonDocument data)
            throws IllegalArgumentException, UnsupportedDocumentIdException {
        BsonValue _referenceValue
                = getReferenceFieldValue(referenceField, data);

        String db = (targetDb == null ? dbName : targetDb);

        // check _referenceValue
        if (role == ROLE.OWNING) {
            if (_referenceValue == null) {
                return null; // the reference field is missing or it value is null => do not generate a link
            }

            if (type == TYPE.ONE_TO_ONE || type == TYPE.MANY_TO_ONE) {
                BsonValue id = _referenceValue;

                // can be an array if ref-field is a json path expression
                if (id.isArray() && id.asArray().size() == 1) {
                    id = id.asArray().get(0);
                }

                return URLUtils.getUriWithDocId(request, db, targetCollection, id);
            } else {
                if (!_referenceValue.isArray()) {
                    throw new IllegalArgumentException(
                            "in resource "
                            + dbName
                            + "/"
                            + collName
                            + "/"
                            + data.get("_id")
                            + " the "
                            + type.name()
                            + " relationship ref-field "
                            + this.referenceField
                            + " should be an array, but it is "
                            + _referenceValue);
                }

                List<BsonValue> bsonVals = _referenceValue.asArray().getValues();
                BsonValue[] ids = bsonVals.toArray(new BsonValue[bsonVals.size()]);

                return URLUtils.getUriWithFilterMany(request, db, targetCollection, ids);
            }
        } else {
            // INVERSE
            BsonValue id = data.get("_id");

            if (type == TYPE.ONE_TO_ONE || type == TYPE.ONE_TO_MANY) {
                return URLUtils.getUriWithFilterOne(
                        request,
                        db,
                        targetCollection,
                        referenceField,
                        id);
            } else if (type == TYPE.MANY_TO_ONE || type == TYPE.MANY_TO_MANY) {
                return URLUtils.getUriWithFilterManyInverse(
                        request,
                        db,
                        targetCollection,
                        referenceField,
                        id);
            }
        }

        LOGGER.debug("returned null link. this = {}, data = {}", this, data);
        return null;
    }

    /**
     *
     * @returns the reference field value, either it is an object or, in case
     * referenceField is a json path, a BsonDocument
     *
     *
     */
    private BsonValue getReferenceFieldValue(
            String referenceField,
            BsonDocument data) {
        if (referenceField.startsWith("$.")) {
            // it is a json path expression

            List<Optional<BsonValue>> objs;

            try {
                objs = JsonUtils.getPropsFromPath(data, referenceField);
            } catch (IllegalArgumentException ex) {
                return null;
            }

            if (objs == null) {
                return null;
            }

            BsonArray ret = new BsonArray();

            objs.stream().forEach((Optional<BsonValue> obj) -> {
                if (obj != null && obj.isPresent()) {
                    ret.add(obj.get());
                } else {
                    LOGGER.trace(
                            "the reference field {} resolved to {} from {}",
                            referenceField,
                            objs,
                            data);
                }
            });

            if (ret.isEmpty()) {
                return null;
            } else {
                return ret;
            }
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

    /**
     *
     */
    public enum TYPE {

        /**
         *
         */
        ONE_TO_ONE,

        /**
         *
         */
        ONE_TO_MANY,

        /**
         *
         */
        MANY_TO_ONE,

        /**
         *
         */
        MANY_TO_MANY
    }

    /**
     *
     */
    public enum ROLE {

        /**
         *
         */
        OWNING,

        /**
         *
         */
        INVERSE
    }
}
