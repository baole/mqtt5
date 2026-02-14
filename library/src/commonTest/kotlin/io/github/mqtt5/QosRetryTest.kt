package io.github.mqtt5

import io.github.mqtt5.internal.*
import io.github.mqtt5.protocol.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class QosRetryTest {

    // ─────────────────── SessionState: saveInflightForRetry ───────────────────

    @Test
    fun testSaveInflightEmptyByDefault() {
        val state = SessionState()
        assertTrue(state.inflightForRetry.isEmpty())
    }

    @Test
    fun testSaveInflightQos1Messages() = runTest {
        val state = SessionState()
        val packet1 = PublishPacket(qos = QoS.AT_LEAST_ONCE, topicName = "topic/1", packetId = 1, payload = "msg1".encodeToByteArray())
        val packet2 = PublishPacket(qos = QoS.AT_LEAST_ONCE, topicName = "topic/2", packetId = 2, payload = "msg2".encodeToByteArray())

        state.addPendingPuback(1, PendingPublish(packet1))
        state.addPendingPuback(2, PendingPublish(packet2))

        state.saveInflightForRetry()

        assertEquals(2, state.inflightForRetry.size)
        val ids = state.inflightForRetry.map { it.packetId }.toSet()
        assertTrue(1 in ids)
        assertTrue(2 in ids)
        // All should have pubrecReceived = false (QoS 1 messages)
        state.inflightForRetry.forEach { assertFalse(it.pubrecReceived) }
    }

    @Test
    fun testSaveInflightQos2Messages() = runTest {
        val state = SessionState()
        val packet = PublishPacket(qos = QoS.EXACTLY_ONCE, topicName = "topic/qos2", packetId = 10, payload = "data".encodeToByteArray())

        // QoS 2 not yet received PUBREC
        val statePrePubrec = Qos2OutboundState(packet)
        state.addPendingQos2Outbound(10, statePrePubrec)

        // QoS 2 already received PUBREC
        val packet2 = PublishPacket(qos = QoS.EXACTLY_ONCE, topicName = "topic/qos2b", packetId = 11, payload = "data2".encodeToByteArray())
        val statePostPubrec = Qos2OutboundState(packet2)
        statePostPubrec.pubrecReceived = true
        state.addPendingQos2Outbound(11, statePostPubrec)

        state.saveInflightForRetry()

        assertEquals(2, state.inflightForRetry.size)

        val prePubrec = state.inflightForRetry.find { it.packetId == 10 }
        assertNotNull(prePubrec)
        assertFalse(prePubrec.pubrecReceived)

        val postPubrec = state.inflightForRetry.find { it.packetId == 11 }
        assertNotNull(postPubrec)
        assertTrue(postPubrec.pubrecReceived)
    }

    @Test
    fun testSaveInflightMixedQosMessages() = runTest {
        val state = SessionState()

        // QoS 1
        val qos1Packet = PublishPacket(qos = QoS.AT_LEAST_ONCE, topicName = "topic/q1", packetId = 1)
        state.addPendingPuback(1, PendingPublish(qos1Packet))

        // QoS 2
        val qos2Packet = PublishPacket(qos = QoS.EXACTLY_ONCE, topicName = "topic/q2", packetId = 2)
        state.addPendingQos2Outbound(2, Qos2OutboundState(qos2Packet))

        state.saveInflightForRetry()

        assertEquals(2, state.inflightForRetry.size)
    }

    @Test
    fun testClearInflightRetry() = runTest {
        val state = SessionState()
        val packet = PublishPacket(qos = QoS.AT_LEAST_ONCE, topicName = "topic", packetId = 1)
        state.addPendingPuback(1, PendingPublish(packet))
        state.saveInflightForRetry()

        assertEquals(1, state.inflightForRetry.size)

        state.clearInflightRetry()
        assertTrue(state.inflightForRetry.isEmpty())
    }

    // ─────────────────── SessionState: sessionPresent ───────────────────

    @Test
    fun testSessionPresentDefaultFalse() {
        val state = SessionState()
        assertFalse(state.sessionPresent)
    }

    @Test
    fun testSessionPresentUpdatedFromConnack() {
        val state = SessionState()

        // Session present = true
        val connackWithSession = ConnackPacket(sessionPresent = true)
        state.updateFromConnack(connackWithSession)
        assertTrue(state.sessionPresent)

        // Session present = false
        val connackWithoutSession = ConnackPacket(sessionPresent = false)
        state.updateFromConnack(connackWithoutSession)
        assertFalse(state.sessionPresent)
    }

    // ─────────────────── SessionState: clearForReconnect ───────────────────

    @Test
    fun testClearForReconnectCleanStartClearsEverything() = runTest {
        val state = SessionState()
        val packet = PublishPacket(qos = QoS.AT_LEAST_ONCE, topicName = "t", packetId = 1)
        state.addPendingPuback(1, PendingPublish(packet))
        state.addPendingQos2Outbound(2, Qos2OutboundState(
            PublishPacket(qos = QoS.EXACTLY_ONCE, topicName = "t2", packetId = 2)
        ))
        state.subscriptions["topic/#"] = QoS.AT_LEAST_ONCE

        state.clearForReconnect(cleanStart = true)

        assertTrue(state.pendingPuback.isEmpty())
        assertTrue(state.pendingQos2Outbound.isEmpty())
        assertTrue(state.subscriptions.isEmpty())
    }

    @Test
    fun testClearForReconnectResumeSessionPreservesSubscriptions() = runTest {
        val state = SessionState()
        state.subscriptions["topic/#"] = QoS.AT_LEAST_ONCE
        state.subscriptions["cmd/+"] = QoS.EXACTLY_ONCE

        state.clearForReconnect(cleanStart = false)

        // Subscriptions preserved
        assertEquals(2, state.subscriptions.size)
        assertEquals(QoS.AT_LEAST_ONCE, state.subscriptions["topic/#"])
        assertEquals(QoS.EXACTLY_ONCE, state.subscriptions["cmd/+"])
    }

    // ─────────────────── PacketIdManager: reserve ───────────────────

    @Test
    fun testPacketIdReserve() = runTest {
        val manager = PacketIdManager()

        // Reserve a specific ID
        assertTrue(manager.reserve(42))
        assertTrue(manager.isInUse(42))

        // Cannot reserve again
        assertFalse(manager.reserve(42))
    }

    @Test
    fun testPacketIdReserveDoesNotConflictWithAllocate() = runTest {
        val manager = PacketIdManager()

        // Allocate IDs 1 and 2
        val id1 = manager.allocate()
        val id2 = manager.allocate()
        assertEquals(1, id1)
        assertEquals(2, id2)

        // Reserve 1 should fail (already allocated)
        assertFalse(manager.reserve(1))

        // Reserve 5 should succeed
        assertTrue(manager.reserve(5))
        assertTrue(manager.isInUse(5))

        // Release and re-reserve
        manager.release(5)
        assertFalse(manager.isInUse(5))
        assertTrue(manager.reserve(5))
    }

    @Test
    fun testPacketIdReserveAfterRelease() = runTest {
        val manager = PacketIdManager()

        val id = manager.allocate()
        manager.release(id)
        assertFalse(manager.isInUse(id))

        // Now reserve it (simulating QoS retry)
        assertTrue(manager.reserve(id))
        assertTrue(manager.isInUse(id))
    }

    // ─────────────────── InflightMessage data class ───────────────────

    @Test
    fun testInflightMessageData() {
        val packet = PublishPacket(qos = QoS.AT_LEAST_ONCE, topicName = "test", packetId = 7)
        val msg = InflightMessage(packetId = 7, packet = packet, pubrecReceived = false)

        assertEquals(7, msg.packetId)
        assertEquals("test", msg.packet.topicName)
        assertFalse(msg.pubrecReceived)
    }

    @Test
    fun testInflightMessageQos2WithPubrec() {
        val packet = PublishPacket(qos = QoS.EXACTLY_ONCE, topicName = "qos2", packetId = 15)
        val msg = InflightMessage(packetId = 15, packet = packet, pubrecReceived = true)

        assertEquals(15, msg.packetId)
        assertTrue(msg.pubrecReceived)
    }

    // ─────────────────── MqttClient: initial TLS config ───────────────────

    @Test
    fun testClientDefaultTlsConfig() {
        val client = MqttClient()
        assertNull(client.config.tlsConfig)
        assertFalse(client.config.useTls)
    }
}
