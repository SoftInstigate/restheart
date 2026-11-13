package org.restheart.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.configuration.ConfigurationException;

class MongoPermissionsAllowFilterTest {

    private static MongoPermissions fromJson(String json) {
        return MongoPermissions.from(BsonDocument.parse(json));
    }

    @Test
    void notSetTakesTheDefault() {
        var mp = fromJson("""
                {"mongo": {"allowBulkPatch": true}}""");

        assertNull(mp.getAllowFilter());
        assertFalse(mp.isAllowFilter(false));
        assertTrue(mp.isAllowFilter(true));
        assertFalse(mp.asBson().containsKey("allowFilter"));
    }

    @Test
    void withoutMongoBlockTakesTheDefault() {
        assertNull(fromJson("{}").getAllowFilter());
        assertNull(new MongoPermissions().getAllowFilter());
    }

    @Test
    void setWinsOverTheDefault() {
        var denied = fromJson("""
                {"mongo": {"allowFilter": false}}""");
        var allowed = fromJson("""
                {"mongo": {"allowFilter": true}}""");

        assertFalse(denied.isAllowFilter(true));
        assertTrue(allowed.isAllowFilter(false));
        assertEquals(Boolean.TRUE, allowed.asBson().getBoolean("allowFilter").getValue());
    }

    @Test
    void fromAMapOfTheConfiguration() throws Exception {
        var mp = MongoPermissions.from(Map.<String, Object>of("mongo", Map.<String, Object>of("allowFilter", true)));
        assertEquals(Boolean.TRUE, mp.getAllowFilter());
    }

    @Test
    void notABooleanIsRefused() {
        assertThrows(ConfigurationException.class, () -> fromJson("""
                {"mongo": {"allowFilter": "yes"}}"""));
    }

    @Test
    void allowAllAllowsFilter() {
        assertTrue(MongoPermissions.ALLOW_ALL_MONGO_PERMISSIONS.isAllowFilter(false));
    }
}
