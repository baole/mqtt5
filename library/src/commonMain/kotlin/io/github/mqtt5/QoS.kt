package io.github.mqtt5

/**
 * MQTT Quality of Service levels.
 *
 * Defines the delivery guarantee for application messages.
 */
enum class QoS(val value: Int) {
    /** At most once delivery. Message loss can occur. */
    AT_MOST_ONCE(0),

    /** At least once delivery. Messages are assured to arrive but duplicates can occur. */
    AT_LEAST_ONCE(1),

    /** Exactly once delivery. Messages are assured to arrive exactly once. */
    EXACTLY_ONCE(2);

    companion object {
        fun fromValue(value: Int): QoS = entries.first { it.value == value }
        fun fromValueOrNull(value: Int): QoS? = entries.firstOrNull { it.value == value }
    }
}
