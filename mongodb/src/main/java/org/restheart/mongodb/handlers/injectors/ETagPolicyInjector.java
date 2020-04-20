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
package org.restheart.mongodb.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.ExchangeKeys;
import static org.restheart.exchange.ExchangeKeys.ETAG_CHECK_POLICY.OPTIONAL;
import static org.restheart.exchange.ExchangeKeys.ETAG_CHECK_POLICY.REQUIRED;
import static org.restheart.exchange.ExchangeKeys.ETAG_DOC_POLICY_METADATA_KEY;
import static org.restheart.exchange.ExchangeKeys.ETAG_POLICY_METADATA_KEY;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * injects the ETag Policy
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ETagPolicyInjector extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ETagPolicyInjector.class);

    /**
     * Creates a new instance of ETagPolicyInjector
     *
     */
    public ETagPolicyInjector() {
        super(null);
    }

    /**
     * Creates a new instance of ETagPolicyInjector
     *
     * @param next
     */
    public ETagPolicyInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);

        request.setETagCheckRequired(isETagCheckRequired(request));

        next(exchange);
    }

    /**
     *
     * @param request
     * @return
     */
    public boolean isETagCheckRequired(MongoRequest request) {
        var collectionProps = request.getCollectionProps();
        var dbProps = request.getDbProps();

        // if client specifies the If-Match header, than check it
        if (request.getETag() != null) {
            return true;
        }

        // if client requires the check via qparam return true
        if (request.isForceEtagCheck()) {
            return true;
        }

        // for documents consider db and coll etagDocPolicy metadata
        if (request.isDocument() || request.isFile()) {
            // check the coll metadata
            BsonValue _policy = collectionProps != null
                    ? collectionProps.get(ETAG_DOC_POLICY_METADATA_KEY)
                    : null;

            LOGGER.trace(
                    "collection etag policy (from coll properties) {}",
                    _policy);

            if (_policy == null) {
                // check the db metadata
                _policy = dbProps != null ? dbProps.get(ETAG_DOC_POLICY_METADATA_KEY)
                        : null;
                LOGGER.trace(
                        "collection etag policy (from db properties) {}",
                        _policy);
            }

            ExchangeKeys.ETAG_CHECK_POLICY policy = null;

            if (_policy != null && _policy.isString()) {
                try {
                    policy = ExchangeKeys.ETAG_CHECK_POLICY
                            .valueOf(_policy.asString().getValue()
                                    .toUpperCase());
                } catch (IllegalArgumentException iae) {
                    policy = null;
                }
            }

            if (null != policy) {
                if (request.isDelete()) {
                    return policy != OPTIONAL;
                } else {
                    return policy == REQUIRED;
                }
            }
        }

        // for db consider db etagPolicy metadata
        if (request.isDb() && dbProps != null) {
            // check the coll  metadata
            BsonValue _policy = dbProps.get(ETAG_POLICY_METADATA_KEY);

            LOGGER.trace("db etag policy (from db properties) {}", _policy);

            ExchangeKeys.ETAG_CHECK_POLICY policy = null;

            if (_policy != null && _policy.isString()) {
                try {
                    policy = ExchangeKeys.ETAG_CHECK_POLICY.valueOf(
                            _policy.asString().getValue()
                                    .toUpperCase());
                } catch (IllegalArgumentException iae) {
                    policy = null;
                }
            }

            if (null != policy) {
                if (request.isDelete()) {
                    return policy != OPTIONAL;
                } else {
                    return policy == REQUIRED;
                }
            }
        }

        // for collection consider coll and db etagPolicy metadata
        if (request.isCollection() && collectionProps != null) {
            // check the coll  metadata
            BsonValue _policy = collectionProps.get(ETAG_POLICY_METADATA_KEY);

            LOGGER.trace(
                    "coll etag policy (from coll properties) {}",
                    _policy);

            if (_policy == null) {
                // check the db metadata
                _policy = dbProps != null ? dbProps.get(ETAG_POLICY_METADATA_KEY)
                        : null;

                LOGGER.trace(
                        "coll etag policy (from db properties) {}",
                        _policy);
            }

            ExchangeKeys.ETAG_CHECK_POLICY policy = null;

            if (_policy != null && _policy.isString()) {
                try {
                    policy = ExchangeKeys.ETAG_CHECK_POLICY.valueOf(
                            _policy.asString().getValue()
                                    .toUpperCase());
                } catch (IllegalArgumentException iae) {
                    policy = null;
                }
            }

            if (null != policy) {
                if (request.isDelete()) {
                    return policy != OPTIONAL;
                } else {
                    return policy == REQUIRED;
                }
            }
        }

        // apply the default policy from configuration
        var dbP = MongoServiceConfiguration.get()
                .getDbEtagCheckPolicy();

        var collP = MongoServiceConfiguration.get()
                .getCollEtagCheckPolicy();

        var docP = MongoServiceConfiguration.get()
                .getDocEtagCheckPolicy();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("default etag db check (from conf) {}", dbP);
            LOGGER.trace("default etag coll check (from conf) {}", collP);
            LOGGER.trace("default etag doc check (from conf) {}", docP);
        }

        ExchangeKeys.ETAG_CHECK_POLICY policy = null;

        if (null != request.getType()) {
            switch (request.getType()) {
                case DB:
                    policy = dbP;
                    break;
                case COLLECTION:
                case FILES_BUCKET:
                case SCHEMA_STORE:
                    policy = collP;
                    break;
                default:
                    policy = docP;
            }
        }

        if (null != policy) {
            if (request.isDelete()) {
                return policy != OPTIONAL;
            } else {
                return policy == REQUIRED;
            }
        }

        return false;
    }
}
