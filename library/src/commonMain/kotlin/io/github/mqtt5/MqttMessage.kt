package io.github.mqtt5

import io.github.mqtt5.protocol.MqttProperties

/**
 * Represents an MQTT application message received from a subscription.
 */
data class MqttMessage(
    /** The topic the message was published to. */
    val topic: String,
    /** The message payload. */
    val payload: ByteArray,
    /** The QoS level of the message. */
    val qos: QoS,
    /** Whether this is a retained message. */
    val retain: Boolean = false,
    /** MQTT v5 properties associated with this message. */
    val properties: MqttProperties = MqttProperties(),
) {
    /** Decode payload as a UTF-8 string. */
    val payloadAsString: String get() = payload.decodeToString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MqttMessage) return false
        return topic == other.topic && payload.contentEquals(other.payload) &&
                qos == other.qos && retain == other.retain && properties == other.properties
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + qos.hashCode()
        result = 31 * result + retain.hashCode()
        return result
    }

    override fun toString(): String =
        "MqttMessage(topic='$topic', payloadSize=${payload.size}, qos=$qos, retain=$retain)"
}

/**
 * Options for an MQTT subscription.
 */
data class SubscriptionOptions(
    /** Maximum QoS level for messages delivered via this subscription. */
    val qos: QoS = QoS.AT_MOST_ONCE,
    /**
     * If true, messages published by this client's own connection are not forwarded
     * back to it on this subscription. Must not be set on shared subscriptions.
     */
    val noLocal: Boolean = false,
    /**
     * If true, application messages forwarded using this subscription keep the RETAIN flag
     * they were published with. If false, the RETAIN flag is set to 0.
     */
    val retainAsPublished: Boolean = false,
    /**
     * Controls retained message delivery when the subscription is established.
     * 0 = Send retained messages at subscribe time.
     * 1 = Send retained messages only if the subscription does not currently exist.
     * 2 = Do not send retained messages at subscribe time.
     */
    val retainHandling: Int = 0,
) {
    init {
        require(retainHandling in 0..2) { "retainHandling must be 0, 1, or 2" }
    }

    /** Encode subscription options into a single byte per MQTT v5 spec. */
    fun encode(): Int {
        return qos.value or
                (if (noLocal) 0x04 else 0) or
                (if (retainAsPublished) 0x08 else 0) or
                (retainHandling shl 4)
    }

    companion object {
        fun decode(byte: Int): SubscriptionOptions {
            return SubscriptionOptions(
                qos = QoS.fromValue(byte and 0x03),
                noLocal = (byte and 0x04) != 0,
                retainAsPublished = (byte and 0x08) != 0,
                retainHandling = (byte shr 4) and 0x03,
            )
        }
    }
}

/**
 * Represents a topic filter with subscription options for a SUBSCRIBE packet.
 */
data class Subscription(
    val topicFilter: String,
    val options: SubscriptionOptions = SubscriptionOptions(),
)
