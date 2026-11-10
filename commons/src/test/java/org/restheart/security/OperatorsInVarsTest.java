package org.restheart.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.IllegalQueryVariableException;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.utils.VarsInterpolator;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;

/**
 * A server-resolved variable whose value may come from outside the configuration is data: with
 * operators it matches no document in a permission filter, and refuses an aggregation.
 */
public class OperatorsInVarsTest {

    private static MongoRequest withBody(String json) {
        var request = mock(MongoRequest.class);
        when(request.getContent()).thenReturn(BsonDocument.parse(json));
        return request;
    }

    @Test
    public void theResolversThatAllowOperators() {
        assertTrue(AclVarsInterpolator.operatorsAllowed("@mongoPermissions.readFilter"));
        assertTrue(AclVarsInterpolator.operatorsAllowed("@filter"));
        assertFalse(AclVarsInterpolator.operatorsAllowed("@user.team"));
        assertFalse(AclVarsInterpolator.operatorsAllowed("@request.body.owner"));
        assertFalse(AclVarsInterpolator.operatorsAllowed("@qparams.x"));
    }

    @Test
    public void operatorsAtAnyDepth() {
        assertTrue(AclVarsInterpolator.containsOperators(BsonDocument.parse("""
                {"$ne": null}""")));
        assertTrue(AclVarsInterpolator.containsOperators(BsonDocument.parse("""
                {"a": [{"b": {"$gt": 1}}]}""")));
        assertFalse(AclVarsInterpolator.containsOperators(BsonDocument.parse("""
                {"a": "$b", "c": [1, 2]}""")));
    }

    @Test
    public void inAPermissionFilterAValueWithOperatorsMatchesNoDocument() {
        var request = withBody("""
                {"owner": {"$ne": null}}""");
        var filter = BsonDocument.parse("""
                {"owner": "@request.body.owner"}""");

        var interpolated = AclVarsInterpolator.interpolateFilter(request, filter).asDocument();

        assertTrue(interpolated.get("owner").isString(), "replaced by a value that matches no document");
        assertNotEquals("@request.body.owner", interpolated.getString("owner").getValue());
    }

    @Test
    public void inAPermissionFilterAPlainValueIsBound() {
        var request = withBody("""
                {"owner": "alice"}""");
        var filter = BsonDocument.parse("""
                {"owner": "@request.body.owner"}""");

        var interpolated = AclVarsInterpolator.interpolateFilter(request, filter).asDocument();

        assertEquals("alice", interpolated.getString("owner").getValue());
    }

    @Test
    public void inAnAggregationAValueWithOperatorsIsRefused() {
        var request = withBody("""
                {"owner": {"$ne": null}}""");
        var stage = BsonDocument.parse("""
                {"$match": {"owner": {"$var": "@request.body.owner"}}}""");

        assertThrows(IllegalQueryVariableException.class,
                () -> VarsInterpolator.interpolate(VAR_OPERATOR.$var, stage, new BsonDocument(), request));
    }
}
