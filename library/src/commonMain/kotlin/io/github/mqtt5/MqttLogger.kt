package io.github.mqtt5

/**
 * Log levels for the MQTT client logger.
 */
enum class MqttLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Simple callback-based logger for the MQTT client.
 *
 * ## Usage
 * ```kotlin
 * val client = MqttClient {
 *     host = "broker.example.com"
 *     logger = MqttLogger { level, tag, message ->
 *         println("[$level] $tag: $message")
 *     }
 * }
 * ```
 */
class MqttLogger(
    /** Minimum log level. Messages below this level are discarded. */
    var minLevel: MqttLogLevel = MqttLogLevel.INFO,
    /** Log output callback. */
    private val output: (level: MqttLogLevel, tag: String, message: String) -> Unit,
) {
    internal fun debug(tag: String, message: () -> String) {
        if (minLevel <= MqttLogLevel.DEBUG) output(MqttLogLevel.DEBUG, tag, message())
    }

    internal fun info(tag: String, message: () -> String) {
        if (minLevel <= MqttLogLevel.INFO) output(MqttLogLevel.INFO, tag, message())
    }

    internal fun warn(tag: String, message: () -> String) {
        if (minLevel <= MqttLogLevel.WARN) output(MqttLogLevel.WARN, tag, message())
    }

    internal fun error(tag: String, message: () -> String) {
        output(MqttLogLevel.ERROR, tag, message())
    }
}
