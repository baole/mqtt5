package io.github.mqtt5

/**
 * Represents the lifecycle state of an [MqttClient] connection.
 *
 * Observe state changes via [MqttClient.connectionState]:
 * ```kotlin
 * client.connectionState.collect { state ->
 *     when (state) {
 *         ConnectionState.CONNECTED -> showOnline()
 *         ConnectionState.DISCONNECTED -> showOffline()
 *         ConnectionState.CONNECTING -> showConnecting()
 *         ConnectionState.RECONNECTING -> showReconnecting()
 *         ConnectionState.DISCONNECTING -> showDisconnecting()
 *     }
 * }
 * ```
 */
enum class ConnectionState {
    /** No active connection. Initial state, or after disconnect/reconnect failure. */
    DISCONNECTED,

    /** TCP/TLS handshake and MQTT CONNECT in progress. */
    CONNECTING,

    /** Successfully connected and ready to publish/subscribe. */
    CONNECTED,

    /** Graceful disconnect in progress (sending DISCONNECT packet). */
    DISCONNECTING,

    /** Connection was lost and auto-reconnect is attempting to restore it. */
    RECONNECTING,
}
