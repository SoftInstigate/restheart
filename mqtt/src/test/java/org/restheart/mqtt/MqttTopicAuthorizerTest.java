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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
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

    /**
     * Injects the full RESTHeart configuration the authorizer reads {@code mqtt-sse}'s
     * {@code default-topic} from. Tests that do not call this leave it null, which makes the
     * authorizer fall back to {@link MqttSseService#DEFAULT_TOPIC} - the same thing that happens
     * on a server where mqtt-sse is left on its own defaults.
     */
    private void injectRhConfig(Map<String, Object> confMap) throws Exception {
        var configuration = mock(org.restheart.configuration.Configuration.class);
        when(configuration.toMap()).thenReturn(confMap);
        Field field = MqttTopicAuthorizer.class.getDeclaredField("rhConfig");
        field.setAccessible(true);
        field.set(authorizer, configuration);
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

    @SuppressWarnings("unchecked")
    private ServiceRequest<?> requestWithPath(String path) {
        ServiceRequest<Object> request = mock(ServiceRequest.class);
        when(request.getPath()).thenReturn(path);
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

    // --- patternCovers ---

    @Test
    @DisplayName("Pattern # covers everything")
    void testPatternHashMatchesAll() {
        assertTrue(MqttTopicAuthorizer.patternCovers("#", "any/topic"));
        assertTrue(MqttTopicAuthorizer.patternCovers("#", "a"));
    }

    @Test
    @DisplayName("Pattern sensors/# covers sensors prefix")
    void testPatternPrefixHash() {
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors/temp"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors/room1/temp"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/#", "traffic/flow"));
    }

    @Test
    @DisplayName("Pattern sensors/+/temp covers single level")
    void testPatternSingleLevel() {
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/+/temp", "sensors/room1/temp"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/+/temp", "sensors/room2/temp"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/+/temp", "sensors/room1/humidity"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/+/temp", "sensors/a/b/temp"));
    }

    @Test
    @DisplayName("Exact pattern match")
    void testPatternExactMatch() {
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/temp", "sensors/temp"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/temp", "sensors/humidity"));
    }

    @Test
    @DisplayName("Pattern with multiple wildcards")
    void testPatternMultipleWildcards() {
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/+/+", "sensors/room1/temp"));
        assertTrue(MqttTopicAuthorizer.patternCovers("+/+/+", "a/b/c"));
        assertFalse(MqttTopicAuthorizer.patternCovers("+/+/+", "a/b"));
    }

    // --- patternCovers: privilege-escalation regression (the defect this class fixes) ---

    @Test
    @DisplayName("SECURITY: ACL sensors/+ does NOT cover a request for the broader filter sensors/#")
    void testAclSingleLevelDoesNotCoverBroaderMultiLevelRequest() {
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/+", "sensors/#"),
            "granting sensors/+ must not let a client escalate to sensors/#, which reaches "
                + "topics like sensors/a/b that sensors/+ never grants");
    }

    // --- patternCovers: '#' alone covers every request ---

    @Test
    @DisplayName("ACL # covers a request of # itself and of +/x")
    void testAclHashCoversWildcardRequests() {
        assertTrue(MqttTopicAuthorizer.patternCovers("#", "#"));
        assertTrue(MqttTopicAuthorizer.patternCovers("#", "+/x"));
    }

    // --- patternCovers: sensors/# covers broader and narrower requests alike ---

    @Test
    @DisplayName("ACL sensors/# covers sensors/#, sensors/+, sensors/temp, sensors/a/b, and sensors itself")
    void testAclMultiLevelCoversEverythingUnderPrefix() {
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors/#"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors/+"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors/temp"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors/a/b"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors"));
    }

    // --- patternCovers: sensors/+ covers only same-depth requests, never broader ones ---

    @Test
    @DisplayName("ACL sensors/+ covers sensors/temp and sensors/+, but not sensors/# nor sensors/a/b")
    void testAclSingleLevelCoversOnlySameDepth() {
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/+", "sensors/temp"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/+", "sensors/+"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/+", "sensors/#"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/+", "sensors/a/b"));
    }

    // --- patternCovers: a literal ACL level covers only the identical literal request ---

    @Test
    @DisplayName("ACL sensors/temp covers only sensors/temp, not sensors/+ nor sensors/#")
    void testAclLiteralCoversOnlyIdenticalLiteral() {
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/temp", "sensors/temp"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/temp", "sensors/+"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/temp", "sensors/#"));
    }

    // --- patternCovers: a '+' nested between literal levels behaves the same way ---

    @Test
    @DisplayName("ACL sensors/+/temp covers sensors/a/temp and sensors/+/temp, but not sensors/#")
    void testAclNestedSingleLevelWildcard() {
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/+/temp", "sensors/a/temp"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/+/temp", "sensors/+/temp"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/+/temp", "sensors/#"));
    }

    // --- patternCovers: requested filter deeper or shallower than the ACL pattern ---

    @Test
    @DisplayName("A request deeper than the ACL pattern is denied unless the pattern ends in #")
    void testRequestDeeperThanPattern() {
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/temp", "sensors/temp/extra"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/+", "sensors/a/b"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors/a/b/c/d"));
    }

    @Test
    @DisplayName("A request shallower than the ACL pattern is denied unless the pattern's remaining level is #")
    void testRequestShallowerThanPattern() {
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/temp", "sensors"));
        assertFalse(MqttTopicAuthorizer.patternCovers("sensors/+", "sensors"));
        assertTrue(MqttTopicAuthorizer.patternCovers("sensors/#", "sensors"));
    }

    // --- @RegisterPlugin wiring: what puts this interceptor into the set that
    // core's SSE executor runs at REQUEST_AFTER_AUTH. Neither fact below is
    // exercised by handle()/resolve() unit tests, so a change to either one
    // (e.g. moving the intercept point, or dropping the interface) would leave
    // /mqtt-sse silently unauthorized again while every other test stays green. ---

    @Test
    @DisplayName("@RegisterPlugin.interceptPoint() is REQUEST_AFTER_AUTH, "
        + "which is the only point core's SSE pipeline runs WildcardInterceptors at")
    void testRegisteredAtRequestAfterAuth() {
        RegisterPlugin annotation = MqttTopicAuthorizer.class.getAnnotation(RegisterPlugin.class);
        assertEquals(InterceptPoint.REQUEST_AFTER_AUTH, annotation.interceptPoint(),
            "MqttTopicAuthorizer must be registered at REQUEST_AFTER_AUTH: that is the intercept "
                + "point PlugSseServiceWiringTest proves core's assembled SSE pipeline actually runs "
                + "WildcardInterceptors at. Registering it anywhere else means the pipeline never "
                + "invokes it and /mqtt-sse subscriptions go unauthorized, even though this file's "
                + "other tests (which call handle()/resolve() directly) would stay green.");
    }

    @Test
    @DisplayName("MqttTopicAuthorizer implements WildcardInterceptor, "
        + "the interceptor kind core's SSE pipeline collects and runs")
    void testImplementsWildcardInterceptor() {
        assertTrue(WildcardInterceptor.class.isAssignableFrom(MqttTopicAuthorizer.class),
            "MqttTopicAuthorizer must implement WildcardInterceptor: that is the plugin type "
                + "PlugSseServiceWiringTest proves core resolves and runs for the SSE handshake. "
                + "If this class implemented a different interceptor interface it would keep "
                + "compiling and its own unit tests would keep passing, but core's SSE executor "
                + "would never select it and /mqtt-sse would again be reachable unauthorized.");
    }

    // --- resolve(): the paths this interceptor actually protects ---

    @Test
    @DisplayName("resolve() is true for /mqtt-sse, the SSE endpoint this whole effort protects")
    void testResolveTrueForMqttSse() {
        ServiceRequest<?> request = requestWithPath("/mqtt-sse");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertTrue(authorizer.resolve(request, response));
    }

    @Test
    @DisplayName("resolve() is true for /mqtt, the plain MQTT endpoint")
    void testResolveTrueForMqtt() {
        ServiceRequest<?> request = requestWithPath("/mqtt");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertTrue(authorizer.resolve(request, response));
    }

    @Test
    @DisplayName("resolve() is true for /mqtt/, the plain MQTT endpoint with a trailing slash")
    void testResolveTrueForMqttTrailingSlash() {
        ServiceRequest<?> request = requestWithPath("/mqtt/");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertTrue(authorizer.resolve(request, response));
    }

    @Test
    @DisplayName("resolve() is true for a sub-path of /mqtt, because MqttRestService is mounted "
        + "as a URI prefix and every sub-path genuinely reaches it and must still be authorized")
    void testResolveTrueForMqttSubPath() {
        ServiceRequest<?> request = requestWithPath("/mqtt/sub/path");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertTrue(authorizer.resolve(request, response),
            "/mqtt is registered with MATCH_POLICY.PREFIX, so /mqtt/sub/path reaches "
                + "MqttRestService just like /mqtt does; an exact-match resolve() would let this "
                + "request bypass the ACL entirely");
    }

    @Test
    @DisplayName("resolve() is true for a sub-path of /mqtt-sse, because the SSE pipeline is "
        + "mounted as a URI prefix and every sub-path genuinely reaches it")
    void testResolveTrueForMqttSseSubPath() {
        ServiceRequest<?> request = requestWithPath("/mqtt-sse/sub");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertTrue(authorizer.resolve(request, response),
            "/mqtt-sse is registered with MATCH_POLICY.PREFIX, so /mqtt-sse/sub reaches the SSE "
                + "pipeline just like /mqtt-sse does; an exact-match resolve() would let this "
                + "request bypass the ACL entirely");
    }

    @Test
    @DisplayName("resolve() is false for /mqttx, which merely shares a character prefix with /mqtt")
    void testResolveFalseForMqttxLookalike() {
        ServiceRequest<?> request = requestWithPath("/mqttx");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertFalse(authorizer.resolve(request, response));
    }

    @Test
    @DisplayName("resolve() is false for /mqtt-foo, which merely shares a character prefix with /mqtt")
    void testResolveFalseForMqttFooLookalike() {
        ServiceRequest<?> request = requestWithPath("/mqtt-foo");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertFalse(authorizer.resolve(request, response));
    }

    @Test
    @DisplayName("resolve() is false for /mqttsse, which merely shares a character prefix with /mqtt")
    void testResolveFalseForMqttsseLookalike() {
        ServiceRequest<?> request = requestWithPath("/mqttsse");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertFalse(authorizer.resolve(request, response));
    }

    @Test
    @DisplayName("resolve() is false for an unrelated path")
    void testResolveFalseForUnrelatedPath() {
        ServiceRequest<?> request = requestWithPath("/some-other-service");
        ServiceResponse<?> response = mock(ServiceResponse.class);

        assertFalse(authorizer.resolve(request, response));
    }

    // --- A request that names no topic still subscribes to one ---
    //
    // These cover the hole that let /mqtt-sse be reached unchecked: the authorizer used to return
    // without deciding anything when the request carried no "topic" parameter, on the assumption
    // that the service would reject it. MqttSseService does not - it substitutes default-topic and
    // subscribes. Nothing in this class exercised a request without a topic, which is why it went
    // unnoticed.

    @Test
    @DisplayName("no ?topic= on /mqtt-sse is denied when the default topic is not granted")
    void testMissingTopicOnSseIsDeniedWhenDefaultNotGranted() throws Exception {
        injectConfig(Map.of("acl", Map.of("admin", List.of("alarms/#"))));
        authorizer.init();

        ServiceRequest<?> request = requestWithTopic(null, accountWithRoles("admin"));
        ServiceResponse<?> response = mock(ServiceResponse.class);

        authorizer.handle(request, response);

        // sensors/# is what the connection would actually be subscribed to, and this ACL grants
        // only alarms/#, so it must be refused - not waved through for naming nothing.
        verify(response).setInError(eq(HttpStatus.SC_FORBIDDEN), contains("sensors/#"));
    }

    @Test
    @DisplayName("no ?topic= on /mqtt-sse is allowed when the default topic IS granted")
    void testMissingTopicOnSseIsAllowedWhenDefaultGranted() throws Exception {
        injectConfig(Map.of("acl", Map.of("admin", List.of("sensors/#"))));
        authorizer.init();

        ServiceRequest<?> request = requestWithTopic(null, accountWithRoles("admin"));
        ServiceResponse<?> response = mock(ServiceResponse.class);

        authorizer.handle(request, response);

        // default-topic must keep working: closing the hole must not quietly remove the feature.
        verify(response, never()).setInError(anyInt(), anyString());
    }

    @Test
    @DisplayName("the configured mqtt-sse default-topic is the one authorized, not the built-in")
    void testMissingTopicUsesConfiguredDefaultTopic() throws Exception {
        injectConfig(Map.of("acl", Map.of("admin", List.of("sensors/#"))));
        injectRhConfig(Map.of("mqtt-sse", Map.of("default-topic", "alarms/#")));
        authorizer.init();

        ServiceRequest<?> request = requestWithTopic(null, accountWithRoles("admin"));
        ServiceResponse<?> response = mock(ServiceResponse.class);

        authorizer.handle(request, response);

        // The grant is on sensors/#, but this server's mqtt-sse subscribes unnamed connections to
        // alarms/# - so that is what has to be checked. Reading the built-in default here instead
        // would authorize a filter the service never uses.
        verify(response).setInError(eq(HttpStatus.SC_FORBIDDEN), contains("alarms/#"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("no ?topic= on /mqtt is left alone: that service answers 400 and never subscribes")
    void testMissingTopicOnRestIsNotDecided() throws Exception {
        injectConfig(Map.of("acl", Map.of("admin", List.of("alarms/#"))));
        authorizer.init();

        // Built before the stubbing below, not inside it: accountWithRoles builds a mock of its
        // own, and Mockito reads a nested mock() inside an in-progress when(...) as unfinished
        // stubbing.
        var account = accountWithRoles("admin");

        ServiceRequest<Object> request = mock(ServiceRequest.class);
        doReturn(new HashMap<String, java.util.Deque<String>>()).when(request).getQueryParameters();
        when(request.getPath()).thenReturn("/mqtt");
        when(request.getAuthenticatedAccount()).thenReturn(account);
        ServiceResponse<?> response = mock(ServiceResponse.class);

        authorizer.handle(request, response);

        // MqttRestService answers 400 for a missing topic and reads only a cache, so there is no
        // filter to authorize and nothing that could be subscribed unchecked. Turning this into a
        // 403 would replace an accurate error with a misleading one.
        verify(response, never()).setInError(anyInt(), anyString());
    }
}
