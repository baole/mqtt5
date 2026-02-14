package io.github.mqtt5

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class OfflineQueueTest {

    @Test
    fun testOfflineQueueCapacityDefault() {
        val client = MqttClient()
        assertEquals(100, client.config.offlineQueueCapacity)
    }

    @Test
    fun testOfflineQueueCapacityCustom() {
        val client = MqttClient {
            offlineQueueCapacity = 500
        }
        assertEquals(500, client.config.offlineQueueCapacity)
    }

    @Test
    fun testOfflineQueueCapacityUnlimited() {
        val client = MqttClient {
            offlineQueueCapacity = 0
        }
        assertEquals(0, client.config.offlineQueueCapacity)
    }

    @Test
    fun testOfflineQueueSizeInitiallyZero() {
        val client = MqttClient()
        assertEquals(0, client.offlineQueueSize)
    }

    @Test
    fun testPublishThrowsWhenDisconnectedWithoutAutoReconnect() {
        val client = MqttClient {
            autoReconnect = false
        }

        val exception = assertFailsWith<IllegalStateException> {
            kotlinx.coroutines.test.runTest {
                client.publish("test/topic", "hello", QoS.AT_MOST_ONCE)
            }
        }
        assertEquals("Not connected", exception.message)
    }

    @Test
    fun testPublishQueuesWhenDisconnectedWithAutoReconnect() {
        val client = MqttClient {
            autoReconnect = true
        }

        kotlinx.coroutines.test.runTest {
            // Should not throw -- queued instead
            client.publish("test/topic", "hello", QoS.AT_MOST_ONCE)
            assertEquals(1, client.offlineQueueSize)

            client.publish("test/topic2", "world", QoS.AT_LEAST_ONCE)
            assertEquals(2, client.offlineQueueSize)
        }
    }

    @Test
    fun testOfflineQueueCapacityEnforced() {
        val client = MqttClient {
            autoReconnect = true
            offlineQueueCapacity = 3
        }

        kotlinx.coroutines.test.runTest {
            client.publish("topic/1", "msg1")
            client.publish("topic/2", "msg2")
            client.publish("topic/3", "msg3")
            assertEquals(3, client.offlineQueueSize)

            // Adding a 4th should drop the oldest
            client.publish("topic/4", "msg4")
            assertEquals(3, client.offlineQueueSize)
        }
    }

    @Test
    fun testOfflineQueueUnlimitedCapacity() {
        val client = MqttClient {
            autoReconnect = true
            offlineQueueCapacity = 0  // unlimited
        }

        kotlinx.coroutines.test.runTest {
            for (i in 1..200) {
                client.publish("topic/$i", "msg$i")
            }
            assertEquals(200, client.offlineQueueSize)
        }
    }

    @Test
    fun testOfflineQueuePreservesQoS() {
        val client = MqttClient {
            autoReconnect = true
        }

        kotlinx.coroutines.test.runTest {
            client.publish("topic/qos0", "msg", QoS.AT_MOST_ONCE)
            client.publish("topic/qos1", "msg", QoS.AT_LEAST_ONCE)
            client.publish("topic/qos2", "msg", QoS.EXACTLY_ONCE)

            assertEquals(3, client.offlineQueueSize)
        }
    }

    @Test
    fun testOfflineQueueWithRetain() {
        val client = MqttClient {
            autoReconnect = true
        }

        kotlinx.coroutines.test.runTest {
            client.publish("topic/retained", "msg", QoS.AT_LEAST_ONCE, retain = true)
            assertEquals(1, client.offlineQueueSize)
        }
    }

    @Test
    fun testAutoReconnectConfigWithOfflineQueue() {
        val client = MqttClient {
            autoReconnect = true
            reconnectDelay = 2.seconds
            maxReconnectDelay = 30.seconds
            maxReconnectAttempts = 10
            offlineQueueCapacity = 50
        }

        assertTrue(client.config.autoReconnect)
        assertEquals(50, client.config.offlineQueueCapacity)
    }
}
