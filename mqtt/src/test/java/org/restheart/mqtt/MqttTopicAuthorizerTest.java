/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
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

package org.restheart.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.utils.HttpStatus;

import io.undertow.security.idm.Account;

/**
 * Unit tests for MqttTopicAuthorizer topic matching logic, ACL configuration
 * validation, and the {@code handle} authorization decision (fail-closed on
 * missing authentication and on an unmatched ACL).
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttTopicAuthorizerTest {

    private MqttTopicAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new MqttTopicAuthorizer();
    }

    private void injectConfig(Map<String, Object> config) throws Exception {
        Field configField = MqttTopicAuthorizer.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(authorizer, config);
    }

    @SuppressWarnings("unchecked")
    private ServiceRequest<?> requestWithTopic(String topic, Account account) {
        ServiceRequest<Object> request = mock(ServiceRequest.class);
        Map<String, java.util.Deque<String>> queryParams = new HashMap<>();
        if (topic != null) {
            ArrayDeque<String> values = new ArrayDeque<>();
            values.add(topic);
            queryParams.put("topic", values);
        }
        doReturn(queryParams).when(request).getQueryParameters();
        when(request.getPath()).thenReturn("/mqtt-sse");
        when(request.getAuthenticatedAccount()).thenReturn(account);
        return request;
    }

    private Account accountWithRoles(String... roles) {
        Account account = mock(Account.class);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test-user");
        when(account.getPrincipal()).thenReturn(principal);
        when(account.getRoles()).thenReturn(Set.of(roles));
        return account;
    }

    // --- handle(): fail-closed authorization decisions ---

    @Test
    @DisplayName("handle() denies with 401 when there is no authenticated account")
    void testHandleDeniesUnauthenticatedWith401() throws Exception {
        injectConfig(Map.of("acl", Map.of("iot-reader", List.of("sensors/#"))));
        authorizer.init();

        ServiceRequest<?> request = requestWithTopic("sensors/temp", null);
        ServiceResponse<?> response = mock(ServiceResponse.class);

        authorizer.handle(request, response);

        verify(response).setInError(eq(HttpStatus.SC_UNAUTHORIZED), anyString());
    }

    @Test
    @DisplayName("handle() denies with 403 when the account's roles have no matching ACL entry")
    void testHandleDeniesUnmatchedAclWith403() throws Exception {
        injectConfig(Map.of("acl", Map.of("iot-reader", List.of("sensors/#"))));
        authorizer.init();

        Account account = accountWithRoles("guest");
        ServiceRequest<?> request = requestWithTopic("sensors/temp", account);
        ServiceResponse<?> response = mock(ServiceResponse.class);

        authorizer.handle(request, response);

        verify(response).setInError(eq(HttpStatus.SC_FORBIDDEN), anyString());
    }

    @Test
    @DisplayName("handle() allows when the account's role has a matching ACL entry")
    void testHandleAllowsMatchedAcl() throws Exception {
        injectConfig(Map.of("acl", Map.of("iot-reader", List.of("sensors/#"))));
        authorizer.init();

        Account account = accountWithRoles("iot-reader");
        ServiceRequest<?> request = requestWithTopic("sensors/temp", account);
        ServiceResponse<?> response = mock(ServiceResponse.class);

        authorizer.handle(request, response);

        verify(response, never()).setInError(org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    @Test
    @DisplayName("handle() with unconfigured (empty) acl denies every authenticated account with 403")
    void testHandleWithEmptyAclDeniesAll() throws Exception {
        injectConfig(new HashMap<>());
        authorizer.init();

        Account account = accountWithRoles("admin");
        ServiceRequest<?> request = requestWithTopic("sensors/temp", account);
        ServiceResponse<?> response = mock(ServiceResponse.class);

        authorizer.handle(request, response);

        verify(response).setInError(eq(HttpStatus.SC_FORBIDDEN), anyString());
    }

    // --- ACL configuration validation (@OnInit fail-fast) ---

    @Test
    @DisplayName("Well-formed acl config initializes without error")
    void testWellFormedAclConfigInitializes() throws Exception {
        injectConfig(Map.of("acl", Map.of(
            "iot-reader", List.of("sensors/#", "devices/+/status"),
            "admin", List.of("#"))));

        authorizer.init();

        Field aclField = MqttTopicAuthorizer.class.getDeclaredField("acl");
        aclField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> acl = (Map<String, List<String>>) aclField.get(authorizer);
        assertEquals(2, acl.size());
        assertEquals(List.of("sensors/#", "devices/+/status"), acl.get("iot-reader"));
    }

    @Test
    @DisplayName("Malformed acl: role value is a String instead of a list fails fast at init, naming 'acl'")
    void testMalformedAclRoleValueNotAListFailsFast() throws Exception {
        Map<String, Object> aclConfig = new HashMap<>();
        aclConfig.put("iot-reader", "sensors/#"); // should be a List, not a String
        injectConfig(Map.of("acl", aclConfig));

        var ex = assertThrows(IllegalArgumentException.class, () -> authorizer.init());
        assertTrue(ex.getMessage().contains("acl"), "message should name 'acl': " + ex.getMessage());
        assertTrue(ex.getMessage().contains("iot-reader"), "message should name the offending role: " + ex.getMessage());
    }

    @Test
    @DisplayName("Malformed acl: topic list contains a non-String element fails fast at init, naming 'acl'")
    void testMalformedAclTopicNotAStringFailsFast() throws Exception {
        Map<String, Object> aclConfig = new HashMap<>();
        aclConfig.put("iot-reader", List.of(42)); // non-string topic pattern
        injectConfig(Map.of("acl", aclConfig));

        var ex = assertThrows(IllegalArgumentException.class, () -> authorizer.init());
        assertTrue(ex.getMessage().contains("acl"), "message should name 'acl': " + ex.getMessage());
        assertTrue(ex.getMessage().contains("iot-reader"), "message should name the offending role: " + ex.getMessage());
    }

    @Test
    @DisplayName("Malformed acl: role key is not a String fails fast at init, naming 'acl'")
    void testMalformedAclRoleKeyNotAStringFailsFast() throws Exception {
        Map<Object, Object> aclConfig = new HashMap<>();
        aclConfig.put(42, List.of("sensors/#")); // non-string role key
        injectConfig(Map.of("acl", aclConfig));

        var ex = assertThrows(IllegalArgumentException.class, () -> authorizer.init());
        assertTrue(ex.getMessage().contains("acl"), "message should name 'acl': " + ex.getMessage());
    }

    // --- isTopicAllowed ---

    @Test
    @DisplayName("Empty allowed list denies all topics")
    void testEmptyAllowedList() {
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", List.of()));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", null));
    }

    @Test
    @DisplayName("Exact match allows topic")
    void testExactMatch() {
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", List.of("sensors/temp")));
    }

    @Test
    @DisplayName("Exact match denies non-matching topic")
    void testExactMatchDenied() {
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/humidity", List.of("sensors/temp")));
    }

    @Test
    @DisplayName("Multi-level wildcard (#) allows all topics")
    void testMultiLevelWildcardAll() {
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", List.of("#")));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("anything/at/all", List.of("#")));
    }

    @Test
    @DisplayName("Multi-level wildcard (prefix/#) allows matching topics")
    void testMultiLevelWildcardPrefix() {
        List<String> allowed = List.of("sensors/#");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", allowed));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/temp", allowed));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/a/b/c", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("traffic/flow", allowed));
    }

    @Test
    @DisplayName("Single-level wildcard (+) matches one level")
    void testSingleLevelWildcard() {
        List<String> allowed = List.of("sensors/+/temp");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/temp", allowed));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room2/temp", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/humidity", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/a/b/temp", allowed)); // too many levels
    }

    @Test
    @DisplayName("Multiple ACL entries: any match allows")
    void testMultipleAclEntries() {
        List<String> allowed = List.of("sensors/#", "traffic/+");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", allowed));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("traffic/flow", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("admin/config", allowed));
    }

    @Test
    @DisplayName("Denied topic returns false")
    void testDeniedTopic() {
        List<String> allowed = List.of("sensors/#");

        assertFalse(MqttTopicAuthorizer.isTopicAllowed("admin/config", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("internal/debug", allowed));
    }

    // --- topicMatchesPattern ---

    @Test
    @DisplayName("Pattern # matches everything")
    void testPatternHashMatchesAll() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("any/topic", "#"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("a", "#"));
    }

    @Test
    @DisplayName("Pattern sensors/# matches sensors prefix")
    void testPatternPrefixHash() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/temp", "sensors/#"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/room1/temp", "sensors/#"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("traffic/flow", "sensors/#"));
    }

    @Test
    @DisplayName("Pattern sensors/+/temp matches single level")
    void testPatternSingleLevel() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/room1/temp", "sensors/+/temp"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/room2/temp", "sensors/+/temp"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("sensors/room1/humidity", "sensors/+/temp"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("sensors/a/b/temp", "sensors/+/temp"));
    }

    @Test
    @DisplayName("Exact pattern match")
    void testPatternExactMatch() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/temp", "sensors/temp"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("sensors/humidity", "sensors/temp"));
    }

    @Test
    @DisplayName("Pattern with multiple wildcards")
    void testPatternMultipleWildcards() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/room1/temp", "sensors/+/+"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("a/b/c", "+/+/+"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("a/b", "+/+/+"));
    }
}
