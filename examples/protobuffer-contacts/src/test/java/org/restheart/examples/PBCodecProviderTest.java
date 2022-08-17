package org.restheart.examples;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.Ignore;
import org.junit.Test;
import org.restheart.utils.BsonUtils;
import io.github.gaplotech.PBBsonReader;
import io.github.gaplotech.PBBsonWriter;
import io.github.gaplotech.PBCodecProvider;

import static org.junit.Assert.assertEquals;

public class PBCodecProviderTest {
    private CodecRegistry registry = CodecRegistries.fromRegistries(CodecRegistries.fromProviders(new PBCodecProvider()), MongoClientSettings.getDefaultCodecRegistry());

    @Test
    @Ignore
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
    @Ignore
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
    @Ignore
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
    @Ignore
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

        assertEquals("name", request.getName());
        assertEquals("phone", request.getPhone());
        assertEquals("email", request.getEmail());
    }

    @Test
    @Ignore
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

            assertEquals("name", request.getName());
            assertEquals("phone", request.getPhone());
            assertEquals("email", request.getEmail());
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