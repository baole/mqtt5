package io.github.mqtt5

import io.github.mqtt5.protocol.MqttProperties
import io.ktor.network.tls.TLSConfigBuilder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for an MQTT v5 client connection.
 */
class MqttConfig {
    /** Broker hostname or IP address. */
    var host: String = "localhost"

    /** Broker port (1883 for TCP, 8883 for TLS). */
    var port: Int = 1883

    /** Client identifier. Empty string means the server assigns one. */
    var clientId: String = ""

    /** Whether to start a new session (true) or resume an existing one (false). */
    var cleanStart: Boolean = true

    /**
     * Keep alive interval. The client sends PINGREQ if no other packets are sent
     * within this interval. Set to 0 to disable keep-alive.
     */
    var keepAlive: Duration = 60.seconds

    /** Session expiry interval in seconds. 0 means session ends at disconnect.
     *  0xFFFFFFFF means the session does not expire. */
    var sessionExpiryInterval: Long = 0

    /** Maximum number of QoS 1 and QoS 2 messages the client is willing to process concurrently. */
    var receiveMaximum: Int = 65535

    /** Maximum packet size the client is willing to accept. 0 means no limit. */
    var maximumPacketSize: Long = 0

    /** Maximum topic alias value the client will accept from the server. 0 means no topic aliases. */
    var topicAliasMaximum: Int = 0

    /** Whether to request response information from the server. */
    var requestResponseInformation: Boolean = false

    /** Whether the server should send reason strings and user properties on failures. */
    var requestProblemInformation: Boolean = true

    /** User properties to send on the CONNECT packet. */
    var userProperties: List<Pair<String, String>> = emptyList()

    /** Authentication method for enhanced authentication. Null means basic auth only. */
    var authenticationMethod: String? = null

    /** Authentication data for enhanced authentication. */
    var authenticationData: ByteArray? = null

    /** Username for authentication. */
    var username: String? = null

    /** Password for authentication. */
    var password: ByteArray? = null

    /** Whether to use TLS. */
    var useTls: Boolean = false

    /**
     * Custom TLS configuration block applied to Ktor's [TLSConfigBuilder].
     * Use the [tls] helper to set this and enable TLS automatically.
     *
     * On JVM, the builder gives access to `trustManager` (for custom CA certificates)
     * and client certificate configuration for mutual TLS (mTLS).
     */
    var tlsConfig: (TLSConfigBuilder.() -> Unit)? = null

    /**
     * Enable TLS with optional custom configuration.
     *
     * The [block] is applied to Ktor's [TLSConfigBuilder], giving access to
     * platform-specific TLS settings. On JVM this includes custom trust managers
     * and client certificates for mutual TLS (mTLS), commonly required for
     * AWS IoT Core, Azure IoT Hub, and similar cloud IoT platforms.
     *
     * Calling this automatically sets [useTls] to `true`.
     *
     * ```kotlin
     * // Basic TLS (system trust store)
     * tls()
     *
     * // Custom CA / mTLS (JVM)
     * tls {
     *     trustManager = myCustomTrustManager
     * }
     * ```
     */
    fun tls(block: TLSConfigBuilder.() -> Unit = {}) {
        useTls = true
        tlsConfig = block
    }

    // --- Will Message Configuration ---

    /** Will message configuration, or null if no will message. */
    var will: WillConfig? = null

    /** Connection timeout. */
    var connectTimeout: Duration = 30.seconds

    /** Whether to automatically reconnect on connection loss. */
    var autoReconnect: Boolean = false

    /** Initial delay between reconnection attempts. Increases with exponential backoff. */
    var reconnectDelay: Duration = 1.seconds

    /** Maximum delay between reconnection attempts (caps exponential backoff). */
    var maxReconnectDelay: Duration = 60.seconds

    /** Maximum number of reconnection attempts. 0 means unlimited. */
    var maxReconnectAttempts: Int = 0

    /**
     * Maximum number of messages to buffer when disconnected and [autoReconnect] is enabled.
     * When the queue is full, the oldest message is dropped to make room.
     * Set to 0 for unlimited queue size.
     */
    var offlineQueueCapacity: Int = 100

    /** Optional logger for debugging and monitoring. */
    var logger: MqttLogger? = null

    /** Helper to set username/password. */
    fun credentials(username: String, password: String? = null) {
        this.username = username
        this.password = password?.encodeToByteArray()
    }

    /** Helper to configure a will message. */
    fun will(topic: String, payload: ByteArray, qos: QoS = QoS.AT_MOST_ONCE, retain: Boolean = false, block: WillConfig.() -> Unit = {}) {
        will = WillConfig(topic, payload, qos, retain).apply(block)
    }
}

/**
 * Configuration for the Will Message.
 */
class WillConfig(
    var topic: String,
    var payload: ByteArray,
    var qos: QoS = QoS.AT_MOST_ONCE,
    var retain: Boolean = false,
) {
    /** Will delay interval in seconds. */
    var delayInterval: Long = 0

    /** Payload format indicator. 0 = unspecified bytes, 1 = UTF-8 data. */
    var payloadFormatIndicator: Int? = null

    /** Message expiry interval in seconds. */
    var messageExpiryInterval: Long? = null

    /** Content type (e.g., MIME type). */
    var contentType: String? = null

    /** Response topic for request/response pattern. */
    var responseTopic: String? = null

    /** Correlation data for request/response pattern. */
    var correlationData: ByteArray? = null

    /** User properties for the will message. */
    var userProperties: List<Pair<String, String>> = emptyList()

    fun buildProperties(): MqttProperties {
        val props = MqttProperties()
        if (delayInterval != 0L) props.willDelayInterval = delayInterval.toInt()
        payloadFormatIndicator?.let { props.payloadFormatIndicator = it }
        messageExpiryInterval?.let { props.messageExpiryInterval = it.toInt() }
        contentType?.let { props.contentType = it }
        responseTopic?.let { props.responseTopic = it }
        correlationData?.let { props.correlationData = it }
        if (userProperties.isNotEmpty()) props.userProperties = userProperties.toMutableList()
        return props
    }
}
