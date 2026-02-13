package io.github.mqtt5

import io.github.mqtt5.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PacketTest {

    // ─────────────────── CONNECT ───────────────────

    @Test
    fun testConnectPacketEncodeAndDecode() {
        val packet = ConnectPacket(
            cleanStart = true,
            keepAliveSeconds = 60,
            clientId = "test-client",
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<ConnectPacket>(decoded)
        assertEquals("test-client", decoded.clientId)
        assertEquals(true, decoded.cleanStart)
        assertEquals(60, decoded.keepAliveSeconds)
    }

    @Test
    fun testConnectPacketWithCredentials() {
        val packet = ConnectPacket(
            cleanStart = true,
            keepAliveSeconds = 30,
            clientId = "auth-client",
            username = "user",
            password = "pass".encodeToByteArray(),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<ConnectPacket>(decoded)
        assertEquals("auth-client", decoded.clientId)
        assertEquals("user", decoded.username)
        assertEquals("pass", decoded.password?.decodeToString())
    }

    @Test
    fun testConnectPacketWithWill() {
        val willProps = MqttProperties().apply {
            willDelayInterval = 30
            payloadFormatIndicator = 1
        }

        val packet = ConnectPacket(
            cleanStart = false,
            keepAliveSeconds = 120,
            clientId = "will-client",
            willProperties = willProps,
            willTopic = "status/will-client",
            willPayload = "offline".encodeToByteArray(),
            willQos = QoS.AT_LEAST_ONCE,
            willRetain = true,
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<ConnectPacket>(decoded)
        assertEquals("will-client", decoded.clientId)
        assertEquals(false, decoded.cleanStart)
        assertEquals("status/will-client", decoded.willTopic)
        assertEquals("offline", decoded.willPayload?.decodeToString())
        assertEquals(QoS.AT_LEAST_ONCE, decoded.willQos)
        assertEquals(true, decoded.willRetain)
        assertEquals(30, decoded.willProperties?.willDelayInterval)
        assertEquals(1, decoded.willProperties?.payloadFormatIndicator)
    }

    @Test
    fun testConnectPacketWithProperties() {
        val props = MqttProperties().apply {
            sessionExpiryInterval = 3600
            receiveMaximum = 100
            maximumPacketSize = 65536
            topicAliasMaximum = 10
            requestResponseInformation = 1
            userProperties.add("key1" to "value1")
            userProperties.add("key2" to "value2")
        }

        val packet = ConnectPacket(
            cleanStart = true,
            keepAliveSeconds = 60,
            properties = props,
            clientId = "props-client",
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<ConnectPacket>(decoded)
        assertEquals(3600L, decoded.properties.sessionExpiryInterval)
        assertEquals(100, decoded.properties.receiveMaximum)
        assertEquals(65536L, decoded.properties.maximumPacketSize)
        assertEquals(10, decoded.properties.topicAliasMaximum)
        assertEquals(1, decoded.properties.requestResponseInformation)
        assertEquals(2, decoded.properties.userProperties.size)
        assertEquals("key1" to "value1", decoded.properties.userProperties[0])
    }

    // ─────────────────── PUBLISH ───────────────────

    @Test
    fun testPublishQos0() {
        val packet = PublishPacket(
            qos = QoS.AT_MOST_ONCE,
            topicName = "sensor/temp",
            payload = "22.5".encodeToByteArray(),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<PublishPacket>(decoded)
        assertEquals("sensor/temp", decoded.topicName)
        assertEquals("22.5", decoded.payload.decodeToString())
        assertEquals(QoS.AT_MOST_ONCE, decoded.qos)
        assertEquals(null, decoded.packetId)
    }

    @Test
    fun testPublishQos1() {
        val packet = PublishPacket(
            qos = QoS.AT_LEAST_ONCE,
            topicName = "sensor/humidity",
            packetId = 42,
            payload = "65".encodeToByteArray(),
            retain = true,
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<PublishPacket>(decoded)
        assertEquals("sensor/humidity", decoded.topicName)
        assertEquals(42, decoded.packetId)
        assertEquals(QoS.AT_LEAST_ONCE, decoded.qos)
        assertEquals(true, decoded.retain)
        assertEquals("65", decoded.payload.decodeToString())
    }

    @Test
    fun testPublishQos2WithProperties() {
        val props = MqttProperties().apply {
            messageExpiryInterval = 600
            contentType = "application/json"
            responseTopic = "response/123"
            correlationData = "req-001".encodeToByteArray()
            userProperties.add("trace-id" to "abc-123")
        }

        val packet = PublishPacket(
            dup = true,
            qos = QoS.EXACTLY_ONCE,
            topicName = "order/new",
            packetId = 100,
            properties = props,
            payload = """{"item":"widget","qty":5}""".encodeToByteArray(),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<PublishPacket>(decoded)
        assertEquals(true, decoded.dup)
        assertEquals(QoS.EXACTLY_ONCE, decoded.qos)
        assertEquals("order/new", decoded.topicName)
        assertEquals(100, decoded.packetId)
        assertEquals(600, decoded.properties.messageExpiryInterval)
        assertEquals("application/json", decoded.properties.contentType)
        assertEquals("response/123", decoded.properties.responseTopic)
        assertEquals("req-001", decoded.properties.correlationData?.decodeToString())
        assertEquals(1, decoded.properties.userProperties.size)
    }

    @Test
    fun testPublishEmptyPayload() {
        val packet = PublishPacket(
            qos = QoS.AT_MOST_ONCE,
            topicName = "test/empty",
            payload = ByteArray(0),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<PublishPacket>(decoded)
        assertEquals(0, decoded.payload.size)
    }

    // ─────────────────── PUBACK ───────────────────

    @Test
    fun testPubackSuccess() {
        val packet = PubackPacket(packetId = 42)
        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<PubackPacket>(decoded)
        assertEquals(42, decoded.packetId)
        assertEquals(ReasonCode.SUCCESS, decoded.reasonCode)
    }

    @Test
    fun testPubackWithReasonCode() {
        val packet = PubackPacket(
            packetId = 100,
            reasonCode = ReasonCode.NO_MATCHING_SUBSCRIBERS,
        )
        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<PubackPacket>(decoded)
        assertEquals(100, decoded.packetId)
        assertEquals(ReasonCode.NO_MATCHING_SUBSCRIBERS, decoded.reasonCode)
    }

    // ─────────────────── PUBREC / PUBREL / PUBCOMP ───────────────────

    @Test
    fun testQos2Flow() {
        val pubrec = PubrecPacket(packetId = 55)
        val pubrecBytes = PacketEncoder.encode(pubrec)
        val decodedPubrec = PacketDecoder.decode(pubrecBytes)
        assertIs<PubrecPacket>(decodedPubrec)
        assertEquals(55, decodedPubrec.packetId)

        val pubrel = PubrelPacket(packetId = 55)
        val pubrelBytes = PacketEncoder.encode(pubrel)
        val decodedPubrel = PacketDecoder.decode(pubrelBytes)
        assertIs<PubrelPacket>(decodedPubrel)
        assertEquals(55, decodedPubrel.packetId)

        val pubcomp = PubcompPacket(packetId = 55)
        val pubcompBytes = PacketEncoder.encode(pubcomp)
        val decodedPubcomp = PacketDecoder.decode(pubcompBytes)
        assertIs<PubcompPacket>(decodedPubcomp)
        assertEquals(55, decodedPubcomp.packetId)
    }

    // ─────────────────── SUBSCRIBE ───────────────────

    @Test
    fun testSubscribe() {
        val packet = SubscribePacket(
            packetId = 10,
            subscriptions = listOf(
                "sensor/#" to SubscriptionOptions(qos = QoS.AT_LEAST_ONCE),
                "cmd/+" to SubscriptionOptions(
                    qos = QoS.EXACTLY_ONCE,
                    noLocal = true,
                    retainAsPublished = true,
                    retainHandling = 1,
                ),
            ),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<SubscribePacket>(decoded)
        assertEquals(10, decoded.packetId)
        assertEquals(2, decoded.subscriptions.size)

        assertEquals("sensor/#", decoded.subscriptions[0].first)
        assertEquals(QoS.AT_LEAST_ONCE, decoded.subscriptions[0].second.qos)

        assertEquals("cmd/+", decoded.subscriptions[1].first)
        assertEquals(QoS.EXACTLY_ONCE, decoded.subscriptions[1].second.qos)
        assertEquals(true, decoded.subscriptions[1].second.noLocal)
        assertEquals(true, decoded.subscriptions[1].second.retainAsPublished)
        assertEquals(1, decoded.subscriptions[1].second.retainHandling)
    }

    @Test
    fun testSubscribeWithSubscriptionIdentifier() {
        val props = MqttProperties().apply {
            subscriptionIdentifiers.add(42)
        }

        val packet = SubscribePacket(
            packetId = 5,
            properties = props,
            subscriptions = listOf(
                "test/topic" to SubscriptionOptions(qos = QoS.AT_MOST_ONCE),
            ),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<SubscribePacket>(decoded)
        assertEquals(1, decoded.properties.subscriptionIdentifiers.size)
        assertEquals(42, decoded.properties.subscriptionIdentifiers[0])
    }

    // ─────────────────── SUBACK ───────────────────

    @Test
    fun testSuback() {
        val packet = SubackPacket(
            packetId = 10,
            reasonCodes = listOf(
                ReasonCode.GRANTED_QOS_1,
                ReasonCode.GRANTED_QOS_2,
            ),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<SubackPacket>(decoded)
        assertEquals(10, decoded.packetId)
        assertEquals(2, decoded.reasonCodes.size)
        assertEquals(ReasonCode.GRANTED_QOS_1, decoded.reasonCodes[0])
        assertEquals(ReasonCode.GRANTED_QOS_2, decoded.reasonCodes[1])
    }

    // ─────────────────── UNSUBSCRIBE ───────────────────

    @Test
    fun testUnsubscribe() {
        val packet = UnsubscribePacket(
            packetId = 20,
            topicFilters = listOf("sensor/#", "cmd/+"),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<UnsubscribePacket>(decoded)
        assertEquals(20, decoded.packetId)
        assertEquals(2, decoded.topicFilters.size)
        assertEquals("sensor/#", decoded.topicFilters[0])
        assertEquals("cmd/+", decoded.topicFilters[1])
    }

    // ─────────────────── UNSUBACK ───────────────────

    @Test
    fun testUnsuback() {
        val packet = UnsubackPacket(
            packetId = 20,
            reasonCodes = listOf(ReasonCode.SUCCESS, ReasonCode.NO_SUBSCRIPTION_EXISTED),
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<UnsubackPacket>(decoded)
        assertEquals(20, decoded.packetId)
        assertEquals(ReasonCode.SUCCESS, decoded.reasonCodes[0])
        assertEquals(ReasonCode.NO_SUBSCRIPTION_EXISTED, decoded.reasonCodes[1])
    }

    // ─────────────────── PINGREQ / PINGRESP ───────────────────

    @Test
    fun testPingreq() {
        val bytes = PacketEncoder.encode(PingreqPacket)
        assertEquals(2, bytes.size)
        assertEquals(0xC0.toByte(), bytes[0])
        assertEquals(0x00.toByte(), bytes[1])

        val decoded = PacketDecoder.decode(bytes)
        assertIs<PingreqPacket>(decoded)
    }

    @Test
    fun testPingresp() {
        val bytes = PacketEncoder.encode(PingrespPacket)
        assertEquals(2, bytes.size)
        assertEquals(0xD0.toByte(), bytes[0])
        assertEquals(0x00.toByte(), bytes[1])

        val decoded = PacketDecoder.decode(bytes)
        assertIs<PingrespPacket>(decoded)
    }

    // ─────────────────── DISCONNECT ───────────────────

    @Test
    fun testDisconnectNormal() {
        val packet = DisconnectPacket()
        val bytes = PacketEncoder.encode(packet)
        assertEquals(2, bytes.size) // Optimized: no reason code/properties

        val decoded = PacketDecoder.decode(bytes)
        assertIs<DisconnectPacket>(decoded)
        assertEquals(ReasonCode.NORMAL_DISCONNECTION, decoded.reasonCode)
    }

    @Test
    fun testDisconnectWithReasonAndProperties() {
        val props = MqttProperties().apply {
            sessionExpiryInterval = 0
            reasonString = "Server shutting down"
        }
        val packet = DisconnectPacket(
            reasonCode = ReasonCode.SERVER_SHUTTING_DOWN,
            properties = props,
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<DisconnectPacket>(decoded)
        assertEquals(ReasonCode.SERVER_SHUTTING_DOWN, decoded.reasonCode)
        assertEquals(0L, decoded.properties.sessionExpiryInterval)
        assertEquals("Server shutting down", decoded.properties.reasonString)
    }

    // ─────────────────── AUTH ───────────────────

    @Test
    fun testAuthContinue() {
        val props = MqttProperties().apply {
            authenticationMethod = "SCRAM-SHA-1"
            authenticationData = "client-first-data".encodeToByteArray()
        }
        val packet = AuthPacket(
            reasonCode = ReasonCode.CONTINUE_AUTHENTICATION,
            properties = props,
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)

        assertIs<AuthPacket>(decoded)
        assertEquals(ReasonCode.CONTINUE_AUTHENTICATION, decoded.reasonCode)
        assertEquals("SCRAM-SHA-1", decoded.properties.authenticationMethod)
        assertEquals("client-first-data", decoded.properties.authenticationData?.decodeToString())
    }

    @Test
    fun testAuthSuccess() {
        val packet = AuthPacket(reasonCode = ReasonCode.SUCCESS)
        val bytes = PacketEncoder.encode(packet)

        val decoded = PacketDecoder.decode(bytes)
        assertIs<AuthPacket>(decoded)
        assertEquals(ReasonCode.SUCCESS, decoded.reasonCode)
    }

    // ─────────────────── Fixed Header Flags ───────────────────

    @Test
    fun testPublishFixedHeaderFlags() {
        // SUBSCRIBE has reserved flags 0010
        val subBytes = PacketEncoder.encode(SubscribePacket(packetId = 1, subscriptions = listOf("t" to SubscriptionOptions())))
        assertEquals(0x82, subBytes[0].toInt() and 0xFF) // type=8, flags=0010

        // UNSUBSCRIBE has reserved flags 0010
        val unsubBytes = PacketEncoder.encode(UnsubscribePacket(packetId = 1, topicFilters = listOf("t")))
        assertEquals(0xA2, unsubBytes[0].toInt() and 0xFF) // type=10, flags=0010

        // PUBREL has reserved flags 0010
        val pubrelBytes = PacketEncoder.encode(PubrelPacket(packetId = 1))
        assertEquals(0x62, pubrelBytes[0].toInt() and 0xFF) // type=6, flags=0010
    }

    @Test
    fun testPublishFlags() {
        val packet = PublishPacket(
            dup = true,
            qos = QoS.EXACTLY_ONCE,
            retain = true,
            topicName = "t",
            packetId = 1,
            payload = ByteArray(0),
        )
        val bytes = PacketEncoder.encode(packet)
        val byte1 = bytes[0].toInt() and 0xFF
        assertEquals(3, byte1 shr 4)            // type = PUBLISH = 3
        assertTrue((byte1 and 0x08) != 0)       // DUP = 1
        assertEquals(2, (byte1 shr 1) and 0x03) // QoS = 2
        assertTrue((byte1 and 0x01) != 0)       // RETAIN = 1
    }
}
