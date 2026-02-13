package io.github.mqtt5

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MqttClientTest {

    // ─────────────────── Config Defaults ───────────────────

    @Test
    fun testDefaultConfig() {
        val client = MqttClient()
        assertEquals("localhost", client.config.host)
        assertEquals(1883, client.config.port)
        assertEquals("", client.config.clientId)
        assertTrue(client.config.cleanStart)
        assertEquals(60.seconds, client.config.keepAlive)
        assertFalse(client.config.useTls)
        assertFalse(client.config.autoReconnect)
        assertEquals(1.seconds, client.config.reconnectDelay)
        assertEquals(60.seconds, client.config.maxReconnectDelay)
        assertEquals(0, client.config.maxReconnectAttempts)
        assertEquals(30.seconds, client.config.connectTimeout)
        assertNull(client.config.logger)
        assertNull(client.config.will)
        assertNull(client.config.username)
        assertNull(client.config.password)
    }

    @Test
    fun testCustomConfig() {
        val client = MqttClient {
            host = "broker.example.com"
            port = 8883
            clientId = "test-client"
            cleanStart = false
            keepAlive = 120.seconds
            useTls = true
            autoReconnect = true
            reconnectDelay = 2.seconds
            maxReconnectDelay = 30.seconds
            maxReconnectAttempts = 5
            connectTimeout = 10.seconds
        }

        assertEquals("broker.example.com", client.config.host)
        assertEquals(8883, client.config.port)
        assertEquals("test-client", client.config.clientId)
        assertFalse(client.config.cleanStart)
        assertEquals(120.seconds, client.config.keepAlive)
        assertTrue(client.config.useTls)
        assertTrue(client.config.autoReconnect)
        assertEquals(2.seconds, client.config.reconnectDelay)
        assertEquals(30.seconds, client.config.maxReconnectDelay)
        assertEquals(5, client.config.maxReconnectAttempts)
        assertEquals(10.seconds, client.config.connectTimeout)
    }

    // ─────────────────── Initial State ───────────────────

    @Test
    fun testInitialState() {
        val client = MqttClient()
        assertFalse(client.isConnected)
        assertFalse(client.isReconnecting)
    }

    // ─────────────────── Credentials Helper ───────────────────

    @Test
    fun testCredentials() {
        val client = MqttClient {
            credentials("user", "pass")
        }
        assertEquals("user", client.config.username)
        assertContentEquals("pass".encodeToByteArray(), client.config.password)
    }

    @Test
    fun testCredentialsWithoutPassword() {
        val client = MqttClient {
            credentials("user")
        }
        assertEquals("user", client.config.username)
        assertNull(client.config.password)
    }

    // ─────────────────── Will Configuration ───────────────────

    @Test
    fun testWillConfig() {
        val client = MqttClient {
            will("status/test", "offline".encodeToByteArray(), QoS.AT_LEAST_ONCE) {
                retain = true
                delayInterval = 30
                payloadFormatIndicator = 1
                contentType = "text/plain"
                responseTopic = "response/test"
                correlationData = "corr-123".encodeToByteArray()
                userProperties = listOf("key" to "value")
            }
        }

        val will = client.config.will
        assertNotNull(will)
        assertEquals("status/test", will.topic)
        assertContentEquals("offline".encodeToByteArray(), will.payload)
        assertEquals(QoS.AT_LEAST_ONCE, will.qos)
        assertTrue(will.retain)
        assertEquals(30L, will.delayInterval)
        assertEquals(1, will.payloadFormatIndicator)
        assertEquals("text/plain", will.contentType)
        assertEquals("response/test", will.responseTopic)
        assertContentEquals("corr-123".encodeToByteArray(), will.correlationData)
        assertEquals(1, will.userProperties.size)
    }

    @Test
    fun testWillBuildProperties() {
        val will = WillConfig(
            topic = "test",
            payload = "data".encodeToByteArray(),
            qos = QoS.EXACTLY_ONCE,
            retain = true,
        ).apply {
            delayInterval = 60
            payloadFormatIndicator = 1
            messageExpiryInterval = 3600
            contentType = "application/json"
            responseTopic = "response/topic"
            correlationData = "corr".encodeToByteArray()
            userProperties = listOf("a" to "b", "c" to "d")
        }

        val props = will.buildProperties()
        assertEquals(60, props.willDelayInterval)
        assertEquals(1, props.payloadFormatIndicator)
        assertEquals(3600, props.messageExpiryInterval)
        assertEquals("application/json", props.contentType)
        assertEquals("response/topic", props.responseTopic)
        assertContentEquals("corr".encodeToByteArray(), props.correlationData)
        assertEquals(2, props.userProperties.size)
    }

    // ─────────────────── Callbacks ───────────────────

    @Test
    fun testCallbacksAreNullByDefault() {
        val client = MqttClient()
        assertNull(client.onMessage)
        assertNull(client.onDisconnect)
        assertNull(client.onAuth)
        assertNull(client.onReconnecting)
        assertNull(client.onReconnected)
    }

    @Test
    fun testCallbacksCanBeSet() {
        val client = MqttClient()
        var messageReceived = false
        var disconnectReceived = false
        var reconnectingAttempt = -1
        var reconnectedCalled = false

        client.onMessage = { messageReceived = true }
        client.onDisconnect = { disconnectReceived = true }
        client.onReconnecting = { attempt -> reconnectingAttempt = attempt }
        client.onReconnected = { reconnectedCalled = true }

        assertNotNull(client.onMessage)
        assertNotNull(client.onDisconnect)
        assertNotNull(client.onReconnecting)
        assertNotNull(client.onReconnected)
    }

    // ─────────────────── Connect Timeout Config ───────────────────

    @Test
    fun testConnectTimeoutDefaults() {
        val client = MqttClient()
        assertEquals(30.seconds, client.config.connectTimeout)
    }

    @Test
    fun testCustomConnectTimeout() {
        val client = MqttClient {
            connectTimeout = 5.seconds
        }
        assertEquals(5.seconds, client.config.connectTimeout)
    }

    // ─────────────────── Auto-Reconnect Config ───────────────────

    @Test
    fun testAutoReconnectDefaults() {
        val client = MqttClient()
        assertFalse(client.config.autoReconnect)
        assertEquals(1.seconds, client.config.reconnectDelay)
        assertEquals(60.seconds, client.config.maxReconnectDelay)
        assertEquals(0, client.config.maxReconnectAttempts)
    }

    @Test
    fun testAutoReconnectConfig() {
        val client = MqttClient {
            autoReconnect = true
            reconnectDelay = 500.milliseconds
            maxReconnectDelay = 30.seconds
            maxReconnectAttempts = 10
        }

        assertTrue(client.config.autoReconnect)
        assertEquals(500.milliseconds, client.config.reconnectDelay)
        assertEquals(30.seconds, client.config.maxReconnectDelay)
        assertEquals(10, client.config.maxReconnectAttempts)
    }

    // ─────────────────── Session / MQTT properties ───────────────────

    @Test
    fun testSessionExpiryConfig() {
        val client = MqttClient {
            sessionExpiryInterval = 3600
        }
        assertEquals(3600L, client.config.sessionExpiryInterval)
    }

    @Test
    fun testReceiveMaximumConfig() {
        val client = MqttClient {
            receiveMaximum = 100
        }
        assertEquals(100, client.config.receiveMaximum)
    }

    @Test
    fun testMaximumPacketSizeConfig() {
        val client = MqttClient {
            maximumPacketSize = 1048576
        }
        assertEquals(1048576L, client.config.maximumPacketSize)
    }

    @Test
    fun testTopicAliasMaximumConfig() {
        val client = MqttClient {
            topicAliasMaximum = 16
        }
        assertEquals(16, client.config.topicAliasMaximum)
    }

    @Test
    fun testEnhancedAuthConfig() {
        val client = MqttClient {
            authenticationMethod = "SCRAM-SHA-256"
            authenticationData = "init-data".encodeToByteArray()
        }
        assertEquals("SCRAM-SHA-256", client.config.authenticationMethod)
        assertContentEquals("init-data".encodeToByteArray(), client.config.authenticationData)
    }
}
