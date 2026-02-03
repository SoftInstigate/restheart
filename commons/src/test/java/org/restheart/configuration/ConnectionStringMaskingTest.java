/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.configuration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MongoDB connection string password masking functionality.
 *
 * This test class ensures that passwords in MongoDB connection strings are properly
 * masked when logged, while usernames and other connection details remain visible.
 *
 * Tests the {@link Configuration#maskPasswordInConnectionString(String)} method.
 *
 * @author SoftInstigate
 */
public class ConnectionStringMaskingTest {

    private static final String MASK = "**********";

    @Test
    public void testBasicMongoDbConnectionString() {
        String original = "mongodb://repoUser:myPassword@localhost:27017/mydb";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb://repoUser:" + MASK + "@localhost:27017/mydb";

        assertEquals(expected, masked, "Password should be masked, username should remain visible");
        assertTrue(masked.contains("repoUser"), "Username should be visible");
        assertFalse(masked.contains("myPassword"), "Password should not be visible");
    }

    @Test
    public void testMongoDbSrvConnectionString() {
        String original = "mongodb+srv://repoUser:myPassword@caas-mongo.customer-qa-caas.svc.cluster.local/?authSource=admin&replicaSet=rs0";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb+srv://repoUser:" + MASK + "@caas-mongo.customer-qa-caas.svc.cluster.local/?authSource=admin&replicaSet=rs0";

        assertEquals(expected, masked, "Password should be masked in mongodb+srv connection string");
        assertTrue(masked.contains("repoUser"), "Username should be visible");
        assertFalse(masked.contains("myPassword"), "Password should not be visible");
        assertTrue(masked.contains("authSource=admin"), "Query parameters should remain intact");
    }

    @Test
    public void testConnectionStringWithPasswordMatchingAuthSource() {
        // This tests the case where password value appears elsewhere in the connection string
        String original = "mongodb://repoUser:admin@caas-mongo/?authSource=admin&replicaSet=rs0";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb://repoUser:" + MASK + "@caas-mongo/?authSource=admin&replicaSet=rs0";

        assertEquals(expected, masked, "Only password portion should be masked, not authSource parameter");
        assertTrue(masked.contains("repoUser"), "Username should be visible");
        assertFalse(masked.contains("repoUser:admin@"), "Password 'admin' should be masked");
        assertTrue(masked.contains("authSource=admin"), "authSource parameter should remain intact");
    }

    @Test
    public void testConnectionStringWithSpecialCharactersInPassword() {
        // Note: Passwords with @ must be URL-encoded in valid MongoDB connection strings
        // @ becomes %40, ! becomes %21
        String original = "mongodb://user123:p%40ss%21word123@host.com:27017/db";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb://user123:" + MASK + "@host.com:27017/db";

        assertEquals(expected, masked, "Password with special characters should be masked");
        assertTrue(masked.contains("user123"), "Username should be visible");
        assertFalse(masked.contains("p%40ss%21word123"), "Password with special chars should not be visible");
    }

    @Test
    public void testConnectionStringWithQueryParameters() {
        String original = "mongodb://myUser:secret123@host:27017/db?readPreference=secondaryPreferred&serverSelectionTimeoutMS=5000";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb://myUser:" + MASK + "@host:27017/db?readPreference=secondaryPreferred&serverSelectionTimeoutMS=5000";

        assertEquals(expected, masked, "Password should be masked while preserving query parameters");
        assertTrue(masked.contains("myUser"), "Username should be visible");
        assertFalse(masked.contains("secret123"), "Password should not be visible");
        assertTrue(masked.contains("readPreference=secondaryPreferred"), "Query parameters should remain intact");
    }

    @Test
    public void testConnectionStringWithComplexPassword() {
        // Password containing URL-encoded characters
        String original = "mongodb://admin:p%40ssw0rd%21@localhost/db";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb://admin:" + MASK + "@localhost/db";

        assertEquals(expected, masked, "Complex password with URL encoding should be masked");
        assertTrue(masked.contains("admin"), "Username should be visible");
        assertFalse(masked.contains("p%40ssw0rd%21"), "Encoded password should not be visible");
    }

    @Test
    public void testRealWorldExampleFromIssue() {
        // This is the exact example from the bug report where username was being masked instead of password
        String original = "mongodb://repoUser:admin@caas-mongo/?authSource=admin&serverSelectionTimeoutMS=3000&replicaSet=rs0";
        String masked = Configuration.maskPasswordInConnectionString(original);

        // The bug was showing: mongodb://**********:admin@caas-mongo/...
        // The fix should show: mongodb://repoUser:**********@caas-mongo/...
        assertTrue(masked.contains("repoUser:"), "Username 'repoUser' should be visible followed by colon");
        assertTrue(masked.contains(":" + MASK + "@"), "Password should be masked between colon and @ symbol");
        assertFalse(masked.contains(":admin@"), "Password 'admin' should not appear between colon and @ symbol");
        assertTrue(masked.contains("authSource=admin"), "authSource parameter should remain intact");
    }

    @Test
    public void testConnectionStringWithNumericPassword() {
        String original = "mongodb://dbuser:123456@prod-mongo:27017/mydb";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb://dbuser:" + MASK + "@prod-mongo:27017/mydb";

        assertEquals(expected, masked, "Numeric password should be masked");
        assertTrue(masked.contains("dbuser"), "Username should be visible");
        assertFalse(masked.contains(":123456@"), "Numeric password should not be visible");
    }

    @Test
    public void testConnectionStringWithLongPassword() {
        // Use a long password without @ symbol (or use URL-encoded form)
        String longPassword = "veryLongAndComplexPassword123WithSpecialChars";
        String original = "mongodb://user:" + longPassword + "@server.example.com/database";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb://user:" + MASK + "@server.example.com/database";

        assertEquals(expected, masked, "Long password should be masked");
        assertTrue(masked.contains("user"), "Username should be visible");
        assertFalse(masked.contains(longPassword), "Long password should not be visible");
    }

    @Test
    public void testConnectionStringPreservesStructure() {
        String original = "mongodb+srv://user:pass@cluster0.mongodb.net/test?retryWrites=true&w=majority";
        String masked = Configuration.maskPasswordInConnectionString(original);

        // Verify the structure is maintained
        assertTrue(masked.startsWith("mongodb+srv://"), "Protocol should be preserved");
        assertTrue(masked.contains("user:"), "Username with colon should be preserved");
        assertTrue(masked.contains(":" + MASK + "@"), "Masked password should be between colon and @");
        assertTrue(masked.contains("@cluster0.mongodb.net"), "Host should be preserved");
        assertTrue(masked.contains("/test?retryWrites=true&w=majority"), "Database and parameters should be preserved");
    }

    @Test
    public void testMultipleHosts() {
        String original = "mongodb://admin:secretPass@host1:27017,host2:27017,host3:27017/db?replicaSet=rs0";
        String masked = Configuration.maskPasswordInConnectionString(original);
        String expected = "mongodb://admin:" + MASK + "@host1:27017,host2:27017,host3:27017/db?replicaSet=rs0";

        assertEquals(expected, masked, "Password should be masked in connection string with multiple hosts");
        assertTrue(masked.contains("admin"), "Username should be visible");
        assertFalse(masked.contains("secretPass"), "Password should not be visible");
        assertTrue(masked.contains("host1:27017,host2:27017,host3:27017"), "All hosts should be preserved");
    }
}
