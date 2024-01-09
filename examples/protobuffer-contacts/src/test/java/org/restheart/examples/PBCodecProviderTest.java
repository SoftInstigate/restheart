/*-
 * ========================LICENSE_START=================================
 * protobuffer-contacts
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.restheart.utils.BsonUtils;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.mongodb.MongoClientSettings;

import io.github.gaplotech.PBBsonReader;
import io.github.gaplotech.PBBsonWriter;
import io.github.gaplotech.PBCodecProvider;

@Disabled
public class PBCodecProviderTest {
    private CodecRegistry registry = CodecRegistries.fromRegistries(
            CodecRegistries.fromProviders(new PBCodecProvider()), MongoClientSettings.getDefaultCodecRegistry());

    @Test
    /**
     * converts a message to Bson using PBCodecProvider
     *
     * NOTE: this works but for some reason it makes `mvn clean verify` failing
     */
    public void testPBCodecProvider() {
        var doc = new Document().append("key", message());

        var bson = doc.toBsonDocument(BsonDocument.class, registry).get("key").asDocument();

        assertEquals(new BsonString("name"), bson.get("name"));
        assertEquals(new BsonString("email"), bson.get("email"));
        assertEquals(new BsonString("phone"), bson.get("phone"));
    }

    @Test
    /**
     * converts a message to Bson using PBBsonWriter
     */
    public void testPBBsonWriter() {
        var bson = new BsonDocument();

        var bsonWriter = new PBBsonWriter(
                true,
                true,
                new BsonDocumentWriter(bson));

        bsonWriter.write(message());

        assertEquals(new BsonString("name"), bson.get("name"));
        assertEquals(new BsonString("email"), bson.get("email"));
        assertEquals(new BsonString("phone"), bson.get("phone"));
    }

    @Test
    /**
     * converts a message to Bson using JsonFormat.printer()
     */
    public void testJsonFormatPrinter() throws InvalidProtocolBufferException {
        var json = JsonFormat.printer().print(message());
        var bson = BsonUtils.parse(json).asDocument();

        assertEquals(new BsonString("name"), bson.get("name"));
        assertEquals(new BsonString("email"), bson.get("email"));
        assertEquals(new BsonString("phone"), bson.get("phone"));
    }

    @Test
    /**
     * converts a Bson to ContactPostRequest using JsonFormat.parser()
     */
    public void testJsonFormatReader() throws InvalidProtocolBufferException {
        var bson = BsonUtils.parse("""
                {"name": "name", "email": "email", "phone": "phone"}
                """).asDocument();

        var json = BsonUtils.toJson(bson);

        var _request = ContactPostRequest.newBuilder();

        JsonFormat.parser().ignoringUnknownFields().merge(json, _request);

        var request = _request.build();

        assertEquals(request.getName(), "name");
        assertEquals(request.getPhone(), "phone");
        assertEquals(request.getEmail(), "email");
    }

    @Test
    /**
     * Convert a bson to ContactPostRequest using PBBsonReader
     *
     * WARNING: does not work!!
     */
    public void testPBBsonReader() {
        var bson = BsonUtils.parse("""
                {"name": "name", "email": "email", "phone": "phone"}
                """).asDocument();

        try (var bsonDocumentReader = new BsonDocumentReader(bson)) {
            var reader = new PBBsonReader(bsonDocumentReader);

            var request = reader.read(ContactPostRequest.class);

            assertEquals(request.getName(), "name");
            assertEquals(request.getPhone(), "phone");
            assertEquals(request.getEmail(), "email");
        }
    }

    private Message message() {
        return ContactPostRequest.newBuilder()
                .setName("name")
                .setEmail("email")
                .setPhone("phone")
                .build();
    }
}
