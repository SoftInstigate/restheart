/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.restheart.utils.HttpStatus;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoQueryException;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;

/**
 * Tests the mapping of the MongoDB 9.0 error codes. The messages are the ones
 * MongoDB 9.0.4 returns.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class ResponseHelperTest {
    private static final String GEO_MSG = "Could not extract geo keys at path 'loc' for document with _id ObjectId('6ae32144ed9d9a5de4bf2f42'): Longitude/latitude is out of bounds, lng: 500 lat: 500";

    private static BsonDocument response(int code, String codeName, String errmsg) {
        return new BsonDocument("ok", new BsonInt32(0))
                .append("errmsg", new BsonString(errmsg))
                .append("code", new BsonInt32(code))
                .append("codeName", new BsonString(codeName));
    }

    @ParameterizedTest
    @ValueSource(ints = { 510, 511, 16755, 16756, 12116300, 146, 292 })
    public void testClientErrorsAreBadRequest(int code) {
        assertEquals(HttpStatus.SC_BAD_REQUEST, ResponseHelper.getHttpStatusFromErrorCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = { 509, 433, 449, 450, 462, 473, 489, 512, 521 })
    public void testOverloadErrorsAreServiceUnavailable(int code) {
        assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, ResponseHelper.getHttpStatusFromErrorCode(code));
    }

    @Test
    public void testGeoWriteErrorKeepsServerMessage() {
        var mwe = new MongoWriteException(new WriteError(510, GEO_MSG, new BsonDocument()), new ServerAddress());
        assertEquals(GEO_MSG, ResponseHelper.getMessageFromMongoException(mwe));
    }

    @Test
    public void testEmptyGroupFieldKeepsServerMessage() {
        var mqe = new MongoQueryException(response(12116300, "Location12116300", "The field name '' cannot be an empty string"), new ServerAddress());
        assertEquals("The field name '' cannot be an empty string", ResponseHelper.getMessageFromMongoException(mqe));
    }

    @Test
    public void testMemoryLimitMessage() {
        var mce = new MongoCommandException(response(292, "QueryExceededMemoryLimitNoDiskUseAllowed",
                "Executor error during aggregate command on namespace: probe.big :: caused by :: Exceeded memory limit for group, but didn't allow external spilling; pass allowDiskUse:true to opt in"),
                new ServerAddress());
        assertEquals(ResponseHelper.getMessageFromErrorCode(292), ResponseHelper.getMessageFromMongoException(mce));
    }

    @Test
    public void testOverloadMessage() {
        var mce = new MongoCommandException(response(462, "IngressRequestRateLimitExceeded", "rate limit exceeded"), new ServerAddress());
        assertEquals("MongoDB is overloaded, retry later", ResponseHelper.getMessageFromMongoException(mce));
    }
}
