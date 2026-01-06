package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.restheart.mongodb.utils.MongoMountResolver.isParametricMount;
import static org.restheart.mongodb.utils.MongoMountResolver.isValidTenantId;

/**
 * Tests for parametric mount support in
 * 
 * <p>
 * Phase 1.2 tests: Parametric mount detection, tenant ID validation, pattern matching,
 * and relative path extraction for parametric mounts using hostname parameters.
 * </p>
 *
 * @see MongoMountResolver
 */
public class MongoMountResolverParametricTest {

    /**
     * Tests for parametric mount detection.
     */
    @Nested
    class ParametricMountDetectionTests {

        @Test
        void detectsHostParameterInPattern() {
            assertTrue(isParametricMount("/{host[0]}/{*}"),
                    "Pattern with {host[0]} should be detected as parametric");
        }

        @Test
        void doesNotDetectStaticPattern() {
            assertFalse(isParametricMount("/mydb/{*}"),
                    "Static pattern should not be parametric");
        }

        @Test
        void doesNotDetectNullAsParametric() {
            assertFalse(isParametricMount(null),
                    "Null should not be parametric");
        }

        @Test
        void doesNotDetectEmptyAsParametric() {
            assertFalse(isParametricMount(""),
                    "Empty pattern should not be parametric");
        }
    }

    /**
     * Tests for tenant ID validation.
     */
    @Nested
    class TenantIdValidationTests {

        @Test
        void acceptsSimpleTenantId() {
            assertTrue(isValidTenantId("acme"),
                    "Simple alphanumeric should be valid");
        }

        @Test
        void acceptsAlphanumericTenantId() {
            assertTrue(isValidTenantId("acme123"),
                    "Alphanumeric should be valid");
        }

        @Test
        void acceptsTenantIdWithHyphens() {
            assertTrue(isValidTenantId("acme-prod"),
                    "Tenant ID with hyphens should be valid");
        }

        @Test
        void acceptsSingleCharTenantId() {
            assertTrue(isValidTenantId("a"),
                    "Single character should be valid");
        }

        @Test
        void rejectsTenantIdWithLeadingHyphen() {
            assertFalse(MongoMountResolver.isValidTenantId("-acme"),
                    "Leading hyphen should be invalid");
        }

        @Test
        void rejectsTenantIdWithTrailingHyphen() {
            assertFalse(isValidTenantId("acme-"),
                    "Trailing hyphen should be invalid");
        }

        @Test
        void rejectsTenantIdWithSpecialCharacters() {
            assertFalse(isValidTenantId("acme_prod"),
                    "Underscore should be invalid");
            assertFalse(isValidTenantId("acme@prod"),
                    "@ should be invalid");
        }

        @Test
        void rejectsTenantIdTooLong() {
            String longId = "a".repeat(64);
            assertFalse(isValidTenantId(longId),
                    "Tenant ID > 63 chars should be invalid");
        }

        @Test
        void acceptsTenantIdAtMaxLength() {
            String maxId = "a".repeat(63);
            assertTrue(isValidTenantId(maxId),
                    "Tenant ID with 63 chars should be valid");
        }

        @Test
        void rejectsNullTenantId() {
            assertFalse(isValidTenantId(null),
                    "Null should be invalid");
        }

        @Test
        void rejectsEmptyTenantId() {
            assertFalse(isValidTenantId(""),
                    "Empty string should be invalid");
        }
    }

}
