package io.github.mqtt5

import kotlin.test.*

class ConnectionStateTest {

    @Test
    fun testInitialStateIsDisconnected() {
        val client = MqttClient()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun testConnectionStateEnumValues() {
        val values = ConnectionState.entries
        assertEquals(5, values.size)
        assertTrue(ConnectionState.DISCONNECTED in values)
        assertTrue(ConnectionState.CONNECTING in values)
        assertTrue(ConnectionState.CONNECTED in values)
        assertTrue(ConnectionState.DISCONNECTING in values)
        assertTrue(ConnectionState.RECONNECTING in values)
    }

    @Test
    fun testConnectionStateEnumOrdering() {
        // Verify the enum has a sensible ordering
        assertEquals(0, ConnectionState.DISCONNECTED.ordinal)
        assertEquals(1, ConnectionState.CONNECTING.ordinal)
        assertEquals(2, ConnectionState.CONNECTED.ordinal)
        assertEquals(3, ConnectionState.DISCONNECTING.ordinal)
        assertEquals(4, ConnectionState.RECONNECTING.ordinal)
    }

    @Test
    fun testConnectionStateFlowIsAccessible() {
        val client = MqttClient()
        // StateFlow should always have a current value
        assertNotNull(client.connectionState.value)
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun testDisconnectedStateAfterCreation() {
        val client = MqttClient {
            host = "broker.example.com"
            autoReconnect = true
        }
        // Regardless of config, initial state is always DISCONNECTED
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        assertFalse(client.isConnected)
    }
}
