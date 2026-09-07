package org.restheart.mqtt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.plugins.RegisterPlugin;

/**
 * Asserts the {@code mqtt} module's two-tier dormancy scheme: the module must not bind any
 * network surface, nor connect to a broker, merely because its jar was dropped into
 * {@code plugins/}.
 * <p>
 * Tier 1 is the module switch: {@link MqttClientProvider} ({@code mqtt-client}) is the root of
 * the injection graph ({@code mqtt-client} &larr; {@code mqtt-router} &larr; {@code mqtt-sse} /
 * {@code mqtt-rest} / {@code mqtt-mongo-writer}), so disabling it by default cascades - silently,
 * at DEBUG, per {@code ProvidersChecker} - to every plugin that (transitively) requires it.
 * {@link MqttRouterProvider} ({@code mqtt-router}) exposes no HTTP surface and follows the client
 * through that cascade, so it is deliberately <em>not</em> gated here: gating it separately would
 * be redundant and would not change anything observable.
 * </p>
 * <p>
 * Tier 2 is each HTTP/writer surface opting back in individually - {@link MqttSseService}
 * ({@code mqtt-sse}), {@link MqttRestService} ({@code mqtt-rest}) and {@link MqttMongoWriter}
 * ({@code mqtt-mongo-writer}) - so that arming the module does not, as a side effect, expose an
 * endpoint or start writing to MongoDB: a consumer that only wants the injectable
 * {@code mqtt-router} (as {@code examples/mqtt-logger} does) must be able to enable
 * {@code mqtt-client} alone and get nothing more.
 * </p>
 * <p>
 * This class is collected in one place, rather than spread across each plugin's own test class,
 * because the property under test is a single cross-cutting policy over six classes (five gated
 * one way or the other, plus the interceptor that is deliberately left out of the scheme) - and
 * because the security-relevant assertion on {@link MqttTopicAuthorizer} needs to be easy to
 * isolate for a negative control: temporarily disabling that one plugin by default must turn red
 * exactly one, easily located assertion, not one assertion diffused among six test files.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttPluginGatingTest {

    @Test
    @DisplayName("Tier 1: mqtt-client is disabled by default, so dropping the jar in plugins/ "
        + "does not, by itself, open a connection to an MQTT broker")
    void testMqttClientDisabledByDefault() {
        RegisterPlugin annotation = MqttClientProvider.class.getAnnotation(RegisterPlugin.class);
        assertFalse(annotation.enabledByDefault(),
            "mqtt-client is the root of the module's injection graph; enabling it by default would "
                + "make every plugin that installs the mqtt jar start a broker connection loop "
                + "immediately, which is the defect this two-tier scheme fixes");
    }

    @Test
    @DisplayName("Tier 1: mqtt-router is gated with the client, rather than relying on the "
        + "injection graph to disable it")
    void testMqttRouterGatedWithTheClient() {
        RegisterPlugin annotation = MqttRouterProvider.class.getAnnotation(RegisterPlugin.class);
        assertFalse(annotation.enabledByDefault(),
            "mqtt-router must be enabledByDefault = false. It used to keep the annotation default "
                + "of true, on the reasoning that ProvidersChecker would follow mqtt-client through "
                + "the injection graph and disable this provider with it. That does not hold: a "
                + "disabled provider is never instantiated, so mqtt-client is absent from the "
                + "provider registry entirely and the injection lands on PluginsFactory's "
                + "'no provider found' branch - three ERROR lines on every startup. Since the "
                + "module ships with RESTHeart, every installation would print them");
    }

    @Test
    @DisplayName("Tier 2: mqtt-sse is disabled by default, so arming the module does not, by "
        + "itself, expose the /mqtt-sse endpoint")
    void testMqttSseDisabledByDefault() {
        RegisterPlugin annotation = MqttSseService.class.getAnnotation(RegisterPlugin.class);
        assertFalse(annotation.enabledByDefault(),
            "mqtt-sse must opt in separately from mqtt-client, so that a consumer of the "
                + "injectable mqtt-router alone (e.g. examples/mqtt-logger) does not get /mqtt-sse "
                + "exposed as an unrequested side effect of enabling mqtt-client");
    }

    @Test
    @DisplayName("Tier 2: mqtt-rest is disabled by default, so arming the module does not, by "
        + "itself, expose the /mqtt endpoint")
    void testMqttRestDisabledByDefault() {
        RegisterPlugin annotation = MqttRestService.class.getAnnotation(RegisterPlugin.class);
        assertFalse(annotation.enabledByDefault(),
            "mqtt-rest must opt in separately from mqtt-client, so that a consumer of the "
                + "injectable mqtt-router alone (e.g. examples/mqtt-logger) does not get /mqtt "
                + "exposed as an unrequested side effect of enabling mqtt-client");
    }

    @Test
    @DisplayName("Tier 2: mqtt-mongo-writer is disabled by default, so arming the module does "
        + "not, by itself, start persisting MQTT traffic to MongoDB")
    void testMqttMongoWriterDisabledByDefault() {
        RegisterPlugin annotation = MqttMongoWriter.class.getAnnotation(RegisterPlugin.class);
        assertFalse(annotation.enabledByDefault(),
            "mqtt-mongo-writer must opt in separately from mqtt-client, so that a consumer of the "
                + "injectable mqtt-router alone (e.g. examples/mqtt-logger) does not get every "
                + "message it publishes silently written to MongoDB as an unrequested side effect "
                + "of enabling mqtt-client");
    }

    @Test
    @DisplayName("mqtt-topic-authorizer is NOT disabled by default: leaving it enabled while "
        + "mqtt-sse/mqtt-rest are off is a security decision, not an inconsistency to \"fix\"")
    void testMqttTopicAuthorizerNotDisabledByDefaultIsASecurityDecision() {
        RegisterPlugin annotation = MqttTopicAuthorizer.class.getAnnotation(RegisterPlugin.class);
        assertTrue(annotation.enabledByDefault(),
            "mqtt-topic-authorizer must keep enabledByDefault = true even though every other mqtt "
                + "plugin defaults to false: its resolve() only matches /mqtt and /mqtt-sse, so "
                + "while those Tier 2 endpoints are off it is never invoked and costs nothing; but "
                + "the moment an operator opts one of them back in, this interceptor must already "
                + "be active and fail closed with no ACL configured, otherwise there would be a "
                + "window - however brief - where a re-enabled endpoint is reachable with topic "
                + "access completely unchecked. Do not disable this by default for consistency "
                + "with the other five plugins; that would reopen exactly that fail-open window");
    }
}
