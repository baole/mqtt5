import io.github.mqtt5.MqttClient
import io.github.mqtt5.MqttMessage
import io.github.mqtt5.QoS
import io.github.mqtt5.protocol.MqttProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlin.time.Duration.Companion.seconds

/**
 * Sample application demonstrating the KMQTT5 library usage.
 *
 * Prerequisites: An MQTT v5 broker running on localhost:1883
 * (e.g., Mosquitto: `mosquitto -c mosquitto.conf`)
 *
 * You can use a public test broker like:
 * - broker.hivemq.com:1883
 * - test.mosquitto.org:1883
 */
fun main() = runBlocking {
    println("=== KMQTT5 Sample Application ===\n")

    // 1. Create the client with configuration
    val client = MqttClient {
        host = "test.mosquitto.org"
        port = 1883
        clientId = "kmqtt5-sample-${System.currentTimeMillis()}"
        cleanStart = true
        keepAlive = 30.seconds

        // Optional: set credentials
        // credentials("username", "password")

        // Optional: configure a Will Message
        will("status/kmqtt5-sample", "offline".encodeToByteArray(), QoS.AT_LEAST_ONCE) {
            delayInterval = 5
            payloadFormatIndicator = 1  // UTF-8
            contentType = "text/plain"
        }
    }

    // 2. Set up callbacks
    client.onMessage = { message: MqttMessage ->
        println("[RECEIVED] Topic: ${message.topic}")
        println("           QoS: ${message.qos}")
        println("           Retain: ${message.retain}")
        println("           Payload: ${message.payloadAsString}")

        // Print user properties if any
        if (message.properties.userProperties.isNotEmpty()) {
            println("           Properties: ${message.properties.userProperties}")
        }
        println()
    }

    client.onDisconnect = { cause ->
        println("[DISCONNECTED] ${cause?.message ?: "Normal disconnect"}")
    }

    try {
        // 3. Connect
        println("[CONNECTING] to ${client.config.host}:${client.config.port}...")
        client.connect()
        println("[CONNECTED] Client ID: ${client.clientId}\n")

        // 4. Subscribe to topics
        println("[SUBSCRIBING] to 'kmqtt5/test/#' with QoS 1...")
        val subResult = client.subscribe("kmqtt5/test/#", QoS.AT_LEAST_ONCE)
        println("[SUBSCRIBED] Result: $subResult\n")

        // 5. Publish messages at different QoS levels
        println("[PUBLISHING] QoS 0 message...")
        client.publish(
            topic = "kmqtt5/test/qos0",
            payload = "Hello from QoS 0!",
            qos = QoS.AT_MOST_ONCE,
        )

        delay(500)

        println("[PUBLISHING] QoS 1 message...")
        client.publish(
            topic = "kmqtt5/test/qos1",
            payload = "Hello from QoS 1!",
            qos = QoS.AT_LEAST_ONCE,
        )

        delay(500)

        println("[PUBLISHING] QoS 2 message with properties...")
        val publishProps = MqttProperties().apply {
            contentType = "text/plain"
            messageExpiryInterval = 60
            userProperties.add("sender" to "kmqtt5-sample")
            userProperties.add("timestamp" to System.currentTimeMillis().toString())
        }
        client.publish(
            topic = "kmqtt5/test/qos2",
            payload = "Hello from QoS 2 with properties!",
            qos = QoS.EXACTLY_ONCE,
            properties = publishProps,
        )

        // 6. Wait for messages
        println("\n[WAITING] for messages (5 seconds)...\n")
        delay(5000)

        // 7. Publish a retained message
        println("[PUBLISHING] Retained message...")
        client.publish(
            topic = "kmqtt5/test/retained",
            payload = "This is a retained message",
            qos = QoS.AT_LEAST_ONCE,
            retain = true,
        )

        delay(1000)

        // 8. Unsubscribe
        println("[UNSUBSCRIBING] from 'kmqtt5/test/#'...")
        val unsubResult = client.unsubscribe("kmqtt5/test/#")
        println("[UNSUBSCRIBED] Result: $unsubResult\n")

        // 9. Clear the retained message
        client.publish(
            topic = "kmqtt5/test/retained",
            payload = ByteArray(0),
            qos = QoS.AT_LEAST_ONCE,
            retain = true,
        )

    } catch (e: Exception) {
        println("[ERROR] ${e.message}")
        e.printStackTrace()
    } finally {
        // 10. Disconnect gracefully
        println("[DISCONNECTING]...")
        client.disconnect()
        println("[DONE]")
    }
}
