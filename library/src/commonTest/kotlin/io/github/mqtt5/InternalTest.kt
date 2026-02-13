package io.github.mqtt5

import io.github.mqtt5.internal.PacketIdManager
import io.github.mqtt5.internal.TopicAliasManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InternalTest {

    // ─────────────────── PacketIdManager ───────────────────

    @Test
    fun testPacketIdManagerAllocatesSequentially() = runTest {
        val manager = PacketIdManager()
        assertEquals(1, manager.allocate())
        assertEquals(2, manager.allocate())
        assertEquals(3, manager.allocate())
    }

    @Test
    fun testPacketIdManagerRelease() = runTest {
        val manager = PacketIdManager()
        val id = manager.allocate()
        assertTrue(manager.isInUse(id))
        manager.release(id)
        assertFalse(manager.isInUse(id))
    }

    @Test
    fun testPacketIdManagerReset() = runTest {
        val manager = PacketIdManager()
        manager.allocate()
        manager.allocate()
        manager.reset()
        assertEquals(1, manager.allocate()) // Starts over from 1
    }

    @Test
    fun testPacketIdManagerWrapsAround() = runTest {
        val manager = PacketIdManager()
        // Allocate and release a bunch to advance the counter
        for (i in 1..10) {
            val id = manager.allocate()
            manager.release(id)
        }
        // Should still work
        val id = manager.allocate()
        assertTrue(id in 1..65535)
    }

    // ─────────────────── TopicAliasManager ───────────────────

    @Test
    fun testTopicAliasManagerDisabled() {
        val manager = TopicAliasManager(0)
        assertFalse(manager.isEnabled)
        assertNull(manager.getOutboundAlias("test/topic"))
    }

    @Test
    fun testTopicAliasManagerResolveInbound() {
        val manager = TopicAliasManager(10)

        // First time: topic name + alias -> creates mapping
        val topic = manager.resolve("sensor/temp", 1)
        assertEquals("sensor/temp", topic)

        // Second time: empty topic name + alias -> resolves from mapping
        val resolved = manager.resolve("", 1)
        assertEquals("sensor/temp", resolved)
    }

    @Test
    fun testTopicAliasManagerUpdateMapping() {
        val manager = TopicAliasManager(10)

        manager.resolve("topic-a", 1)
        assertEquals("topic-a", manager.resolve("", 1))

        // Update the mapping for alias 1
        manager.resolve("topic-b", 1)
        assertEquals("topic-b", manager.resolve("", 1))
    }

    @Test
    fun testTopicAliasManagerOutbound() {
        val manager = TopicAliasManager(3)

        // First use: creates new alias
        val first = manager.getOutboundAlias("sensor/temp")
        assertEquals("sensor/temp" to 1, first)

        // Second use: reuses alias with empty topic
        val second = manager.getOutboundAlias("sensor/temp")
        assertEquals("" to 1, second)

        // Different topic: new alias
        val third = manager.getOutboundAlias("sensor/humidity")
        assertEquals("sensor/humidity" to 2, third)

        val fourth = manager.getOutboundAlias("sensor/pressure")
        assertEquals("sensor/pressure" to 3, fourth)

        // No more aliases available
        assertNull(manager.getOutboundAlias("sensor/wind"))
    }

    @Test
    fun testTopicAliasManagerClear() {
        val manager = TopicAliasManager(5)
        manager.resolve("topic-a", 1)
        manager.clear()

        // After clear, alias 1 has no mapping
        assertFailsWith<IllegalStateException> {
            manager.resolve("", 1)
        }
    }

    @Test
    fun testTopicAliasManagerNoAlias() {
        val manager = TopicAliasManager(10)

        // No alias provided -> return topic name as-is
        assertEquals("test/topic", manager.resolve("test/topic", null))
    }

    // ─────────────────── SubscriptionOptions ───────────────────

    @Test
    fun testSubscriptionOptionsEncodeDecode() {
        val options = SubscriptionOptions(
            qos = QoS.EXACTLY_ONCE,
            noLocal = true,
            retainAsPublished = true,
            retainHandling = 2,
        )
        val encoded = options.encode()
        val decoded = SubscriptionOptions.decode(encoded)

        assertEquals(QoS.EXACTLY_ONCE, decoded.qos)
        assertEquals(true, decoded.noLocal)
        assertEquals(true, decoded.retainAsPublished)
        assertEquals(2, decoded.retainHandling)
    }

    @Test
    fun testSubscriptionOptionsDefaults() {
        val options = SubscriptionOptions()
        val encoded = options.encode()
        assertEquals(0, encoded)

        val decoded = SubscriptionOptions.decode(0)
        assertEquals(QoS.AT_MOST_ONCE, decoded.qos)
        assertEquals(false, decoded.noLocal)
        assertEquals(false, decoded.retainAsPublished)
        assertEquals(0, decoded.retainHandling)
    }

    @Test
    fun testSubscriptionOptionsQosOnly() {
        val options = SubscriptionOptions(qos = QoS.AT_LEAST_ONCE)
        val encoded = options.encode()
        assertEquals(1, encoded)

        val decoded = SubscriptionOptions.decode(encoded)
        assertEquals(QoS.AT_LEAST_ONCE, decoded.qos)
    }

    // ─────────────────── QoS ───────────────────

    @Test
    fun testQoSFromValue() {
        assertEquals(QoS.AT_MOST_ONCE, QoS.fromValue(0))
        assertEquals(QoS.AT_LEAST_ONCE, QoS.fromValue(1))
        assertEquals(QoS.EXACTLY_ONCE, QoS.fromValue(2))
    }

    @Test
    fun testQoSFromValueOrNull() {
        assertNull(QoS.fromValueOrNull(3))
        assertNull(QoS.fromValueOrNull(-1))
    }

    // ─────────────────── ReasonCode ───────────────────

    @Test
    fun testReasonCodeIsError() {
        assertFalse(ReasonCode.SUCCESS.isError)
        assertFalse(ReasonCode.GRANTED_QOS_1.isError)
        assertFalse(ReasonCode.NO_MATCHING_SUBSCRIBERS.isError)
        assertTrue(ReasonCode.UNSPECIFIED_ERROR.isError)
        assertTrue(ReasonCode.NOT_AUTHORIZED.isError)
        assertTrue(ReasonCode.MALFORMED_PACKET.isError)
    }
}
