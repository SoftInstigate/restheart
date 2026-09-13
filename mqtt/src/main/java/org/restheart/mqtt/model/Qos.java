package org.restheart.mqtt.model;

/**
 * MQTT Quality of Service level, owned by this module.
 * <p>
 * {@link MqttMessageRouter}'s plugin-facing API — {@code subscribe(String, Qos, Consumer)} —
 * must be expressed entirely in types this module owns, so that a user plugin consuming the
 * router does not take a compile-time dependency on the HiveMQ client purely to name a QoS
 * level. This is the same class of problem as restheart#726 (a third-party implementation type
 * leaking into an API other modules compile against, there {@code accounts} depending on
 * concrete classes inside {@code restheart-security.jar}) and restheart#724.
 * <p>
 * The constant names deliberately match {@code com.hivemq.client.mqtt.datatypes.MqttQos}, so
 * this reads as a move of the concept into this module rather than a redesign. The single
 * conversion to HiveMQ's {@code MqttQos} lives in {@code MqttMessageRouter}, at the boundary
 * where the router actually talks to the HiveMQ client.
 * <p>
 * To obtain a {@code Qos} from a raw wire value (e.g. {@link MqttMessage#getQos()}, which stays
 * an {@code int} because it is the value that came off the wire), use {@link #fromCode(int)}.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public enum Qos {

    /** Fire-and-forget: the message is delivered at most once, with no acknowledgement. */
    AT_MOST_ONCE(0),

    /** The message is delivered at least once, possibly more than once. */
    AT_LEAST_ONCE(1),

    /** The message is delivered exactly once, via a four-part handshake. */
    EXACTLY_ONCE(2);

    private final int code;

    Qos(int code) {
        this.code = code;
    }

    /**
     * @return the numeric QoS code (0, 1 or 2) as used on the wire
     */
    public int code() {
        return code;
    }

    /**
     * Resolves a raw numeric QoS code to its {@code Qos} constant.
     * <p>
     * Returns {@code null}, rather than throwing or returning an {@link java.util.Optional},
     * for any code outside 0..2: every caller of this method in this module already validates
     * user input (a query parameter or a configuration value) by testing the result against
     * {@code null}, and matching that contract keeps those call sites unchanged in shape.
     *
     * @param code the raw QoS code
     * @return the matching {@code Qos} constant, or {@code null} if {@code code} is not 0, 1 or 2
     */
    public static Qos fromCode(int code) {
        return switch (code) {
            case 0 -> AT_MOST_ONCE;
            case 1 -> AT_LEAST_ONCE;
            case 2 -> EXACTLY_ONCE;
            default -> null;
        };
    }
}
