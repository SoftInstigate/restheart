package org.restheart.examples;

import com.mongodb.MongoClientSettings;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.junit.Test;

import io.github.gaplotech.PBCodecProvider;

public class PBCodecProviderTest {
    @Test
    public void testPBCodec() {
        var registry = CodecRegistries.fromRegistries(
            CodecRegistries.fromProviders(new PBCodecProvider()),
            MongoClientSettings.getDefaultCodecRegistry());

        var contact = ContactPostRequest.newBuilder()
            .setName("name")
            .setEmail("email")
            .setPhone("phone")
            .build();

        var doc = new Document().append("key", contact);

        var bson = doc.toBsonDocument(BsonDocument.class, registry).get("key").asDocument();

        System.out.println(bson.toJson());
    }
}