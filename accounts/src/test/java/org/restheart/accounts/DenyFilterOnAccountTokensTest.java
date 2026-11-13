package org.restheart.accounts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.utils.BsonUtils;

class DenyFilterOnAccountTokensTest {

    private static boolean refersTo(String json) {
        return DenyFilterOnAccountTokens.refersTo(BsonUtils.parse(json), DenyFilterOnAccountTokens.INVITATION_TOKENS);
    }

    private static boolean refersToUserToken(String json) {
        return DenyFilterOnAccountTokens.refersTo(BsonUtils.parse(json), DenyFilterOnAccountTokens.USER_TOKENS);
    }

    @Test
    void aConditionOnTheTokenIsFound() {
        assertTrue(refersTo("""
                {"token": "abc"}"""));
        assertTrue(refersTo("""
                {"token.x": 1}"""));
    }

    @Test
    void aConditionInsideALogicalOperatorIsFound() {
        assertTrue(refersTo("""
                {"$or": [{"email": "a@example.com"}, {"token": "abc"}]}"""));
        assertTrue(refersTo("""
                {"$and": [{"$nor": [{"token": {"$exists": false}}]}]}"""));
    }

    @Test
    void theFieldInAnExpressionIsFound() {
        assertTrue(refersTo("""
                {"$expr": {"$eq": ["$token", "abc"]}}"""));
        assertTrue(refersTo("""
                {"$expr": {"$gt": [{"$strLenCP": "$token"}, 0]}}"""));
    }

    @Test
    void otherFieldsAreAllowed() {
        assertFalse(refersTo("""
                {"email": "a@example.com", "teamId": {"$oid": "65a1b2c3d4e5f60718293a4b"}}"""));
        assertFalse(refersTo("""
                {"tokens": 1, "mytoken": "x", "note": "token"}"""));
        assertFalse(DenyFilterOnAccountTokens.refersTo(new BsonDocument(), DenyFilterOnAccountTokens.INVITATION_TOKENS));
        assertFalse(DenyFilterOnAccountTokens.refersTo(null, DenyFilterOnAccountTokens.INVITATION_TOKENS));
    }

    @Test
    void theTokensOfTheUsersAreFound() {
        assertTrue(refersToUserToken("""
                {"emailVerificationToken": {"$exists": true}}"""));
        assertTrue(refersToUserToken("""
                {"$or": [{"_id": "a@example.com"}, {"passwordResetToken": "x"}]}"""));
        assertTrue(refersToUserToken("""
                {"$expr": {"$eq": ["$inviteToken", "x"]}}"""));
        assertFalse(refersToUserToken("""
                {"_id": "a@example.com", "token": "x"}"""));
    }
}
