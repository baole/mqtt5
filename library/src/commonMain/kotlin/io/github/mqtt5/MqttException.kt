package io.github.mqtt5

/**
 * Base exception for all MQTT-related errors.
 */
open class MqttException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when an MQTT protocol error is detected (malformed packets, etc.).
 */
class MqttProtocolException(message: String, cause: Throwable? = null) : MqttException(message, cause)

/**
 * Exception thrown when a connection-level error occurs.
 */
class MqttConnectionException(message: String, cause: Throwable? = null) : MqttException(message, cause)

/**
 * Exception thrown when the server rejects a connection with a reason code.
 */
class MqttConnectException(
    val reasonCode: ReasonCode,
    message: String = "Connection refused: ${reasonCode.name} (0x${reasonCode.value.toString(16)})"
) : MqttException(message)

/**
 * Exception thrown when a publish operation fails.
 */
class MqttPublishException(
    val reasonCode: ReasonCode,
    message: String = "Publish failed: ${reasonCode.name}"
) : MqttException(message)

/**
 * Exception thrown when a subscribe/unsubscribe operation fails.
 */
class MqttSubscribeException(
    val reasonCodes: List<ReasonCode>,
    message: String = "Subscribe failed: ${reasonCodes.map { it.name }}"
) : MqttException(message)
