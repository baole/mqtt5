package io.github.mqtt5

import io.github.mqtt5.protocol.MqttDecoder
import io.github.mqtt5.protocol.MqttProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MqttPropertiesTest {

    @Test
    fun testEmptyProperties() {
        val props = MqttProperties()
        assertTrue(props.isEmpty)
        val encoded = props.encode()
        // Should be just a property length of 0 (1 byte VBI)
        assertEquals(1, encoded.size)
        assertEquals(0, encoded[0].toInt())

        val decoded = MqttProperties.decode(MqttDecoder(encoded))
        assertTrue(decoded.isEmpty)
    }

    @Test
    fun testSessionExpiryInterval() {
        val props = MqttProperties()
        props.sessionExpiryInterval = 3600

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(3600L, decoded.sessionExpiryInterval)
    }

    @Test
    fun testMaxSessionExpiry() {
        val props = MqttProperties()
        props.sessionExpiryInterval = 0xFFFFFFFFL // UINT_MAX = never expire

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(0xFFFFFFFFL, decoded.sessionExpiryInterval)
    }

    @Test
    fun testReceiveMaximum() {
        val props = MqttProperties()
        props.receiveMaximum = 100

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(100, decoded.receiveMaximum)
    }

    @Test
    fun testMaximumPacketSize() {
        val props = MqttProperties()
        props.maximumPacketSize = 1048576 // 1MB

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(1048576L, decoded.maximumPacketSize)
    }

    @Test
    fun testTopicAliasMaximum() {
        val props = MqttProperties()
        props.topicAliasMaximum = 50

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(50, decoded.topicAliasMaximum)
    }

    @Test
    fun testTopicAlias() {
        val props = MqttProperties()
        props.topicAlias = 5

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(5, decoded.topicAlias)
    }

    @Test
    fun testMaximumQos() {
        val props = MqttProperties()
        props.maximumQos = 1

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(1, decoded.maximumQos)
    }

    @Test
    fun testRetainAvailable() {
        val props = MqttProperties()
        props.retainAvailable = 0

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(0, decoded.retainAvailable)
    }

    @Test
    fun testUserProperties() {
        val props = MqttProperties()
        props.userProperties.add("key1" to "value1")
        props.userProperties.add("key2" to "value2")
        props.userProperties.add("key1" to "value3") // Same key allowed

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(3, decoded.userProperties.size)
        assertEquals("key1" to "value1", decoded.userProperties[0])
        assertEquals("key2" to "value2", decoded.userProperties[1])
        assertEquals("key1" to "value3", decoded.userProperties[2])
    }

    @Test
    fun testContentType() {
        val props = MqttProperties()
        props.contentType = "application/json"

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals("application/json", decoded.contentType)
    }

    @Test
    fun testResponseTopic() {
        val props = MqttProperties()
        props.responseTopic = "response/client123"

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals("response/client123", decoded.responseTopic)
    }

    @Test
    fun testCorrelationData() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val props = MqttProperties()
        props.correlationData = data

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(data.toList(), decoded.correlationData?.toList())
    }

    @Test
    fun testSubscriptionIdentifiers() {
        val props = MqttProperties()
        props.subscriptionIdentifiers.add(1)
        props.subscriptionIdentifiers.add(268435455)

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(2, decoded.subscriptionIdentifiers.size)
        assertEquals(1, decoded.subscriptionIdentifiers[0])
        assertEquals(268435455, decoded.subscriptionIdentifiers[1])
    }

    @Test
    fun testAuthenticationMethod() {
        val props = MqttProperties()
        props.authenticationMethod = "SCRAM-SHA-256"

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals("SCRAM-SHA-256", decoded.authenticationMethod)
    }

    @Test
    fun testAuthenticationData() {
        val data = "auth-data-here".encodeToByteArray()
        val props = MqttProperties()
        props.authenticationData = data

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals("auth-data-here", decoded.authenticationData?.decodeToString())
    }

    @Test
    fun testReasonString() {
        val props = MqttProperties()
        props.reasonString = "Connection rate exceeded"

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals("Connection rate exceeded", decoded.reasonString)
    }

    @Test
    fun testServerReference() {
        val props = MqttProperties()
        props.serverReference = "other-server.example.com:8883"

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals("other-server.example.com:8883", decoded.serverReference)
    }

    @Test
    fun testServerCapabilities() {
        val props = MqttProperties()
        props.wildcardSubscriptionAvailable = 1
        props.subscriptionIdentifierAvailable = 1
        props.sharedSubscriptionAvailable = 0
        props.serverKeepAlive = 120

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(1, decoded.wildcardSubscriptionAvailable)
        assertEquals(1, decoded.subscriptionIdentifierAvailable)
        assertEquals(0, decoded.sharedSubscriptionAvailable)
        assertEquals(120, decoded.serverKeepAlive)
    }

    @Test
    fun testAssignedClientIdentifier() {
        val props = MqttProperties()
        props.assignedClientIdentifier = "auto-generated-id-12345"

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals("auto-generated-id-12345", decoded.assignedClientIdentifier)
    }

    @Test
    fun testResponseInformation() {
        val props = MqttProperties()
        props.responseInformation = "response/topic/prefix"

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals("response/topic/prefix", decoded.responseInformation)
    }

    @Test
    fun testWillDelayInterval() {
        val props = MqttProperties()
        props.willDelayInterval = 300

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(300, decoded.willDelayInterval)
    }

    @Test
    fun testPayloadFormatIndicator() {
        val props = MqttProperties()
        props.payloadFormatIndicator = 1

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(1, decoded.payloadFormatIndicator)
    }

    @Test
    fun testMessageExpiryInterval() {
        val props = MqttProperties()
        props.messageExpiryInterval = 86400

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(86400, decoded.messageExpiryInterval)
    }

    @Test
    fun testMultipleProperties() {
        val props = MqttProperties()
        props.sessionExpiryInterval = 3600
        props.receiveMaximum = 200
        props.maximumPacketSize = 65536
        props.topicAliasMaximum = 25
        props.requestResponseInformation = 1
        props.requestProblemInformation = 0
        props.authenticationMethod = "PLAIN"
        props.authenticationData = "credentials".encodeToByteArray()
        props.userProperties.add("app-name" to "test-app")
        props.contentType = "text/plain"
        props.responseTopic = "reply/to"
        props.correlationData = "corr-123".encodeToByteArray()

        val encoded = props.encode()
        val decoded = MqttProperties.decode(MqttDecoder(encoded))

        assertEquals(3600L, decoded.sessionExpiryInterval)
        assertEquals(200, decoded.receiveMaximum)
        assertEquals(65536L, decoded.maximumPacketSize)
        assertEquals(25, decoded.topicAliasMaximum)
        assertEquals(1, decoded.requestResponseInformation)
        assertEquals(0, decoded.requestProblemInformation)
        assertEquals("PLAIN", decoded.authenticationMethod)
        assertEquals("credentials", decoded.authenticationData?.decodeToString())
        assertEquals(1, decoded.userProperties.size)
        assertEquals("text/plain", decoded.contentType)
        assertEquals("reply/to", decoded.responseTopic)
        assertEquals("corr-123", decoded.correlationData?.decodeToString())
    }
}
