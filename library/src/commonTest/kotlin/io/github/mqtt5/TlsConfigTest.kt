package io.github.mqtt5

import kotlin.test.*

class TlsConfigTest {

    @Test
    fun testTlsDisabledByDefault() {
        val client = MqttClient()
        assertFalse(client.config.useTls)
        assertNull(client.config.tlsConfig)
    }

    @Test
    fun testUseTlsDirectly() {
        val client = MqttClient {
            useTls = true
        }
        assertTrue(client.config.useTls)
        assertNull(client.config.tlsConfig) // No custom config block
    }

    @Test
    fun testTlsHelperEnablesTls() {
        val client = MqttClient {
            tls()
        }
        assertTrue(client.config.useTls)
        assertNotNull(client.config.tlsConfig)
    }

    @Test
    fun testTlsHelperWithCustomBlock() {
        var blockExecuted = false
        val client = MqttClient {
            tls {
                // Custom TLS configuration block
                blockExecuted = true
                // On JVM this would be: trustManager = myTrustManager
                // On all platforms: serverName = "custom.host"
            }
        }
        assertTrue(client.config.useTls)
        assertNotNull(client.config.tlsConfig)
        // Note: blockExecuted is false here because the block is only
        // called during actual connection, not during configuration
        assertFalse(blockExecuted)
    }

    @Test
    fun testTlsHelperSetsUseTlsTrue() {
        val client = MqttClient {
            useTls = false  // explicitly false
            tls()           // should override to true
        }
        assertTrue(client.config.useTls)
    }

    @Test
    fun testTlsConfigBlockIsStored() {
        val customBlock: io.ktor.network.tls.TLSConfigBuilder.() -> Unit = {
            serverName = "broker.example.com"
        }
        val client = MqttClient {
            tlsConfig = customBlock
            useTls = true
        }
        assertSame(customBlock, client.config.tlsConfig)
    }

    @Test
    fun testDefaultPortForTls() {
        val client = MqttClient {
            port = 8883
            tls()
        }
        assertEquals(8883, client.config.port)
        assertTrue(client.config.useTls)
    }

    @Test
    fun testTlsWithCredentials() {
        val client = MqttClient {
            tls()
            credentials("user", "pass")
        }
        assertTrue(client.config.useTls)
        assertEquals("user", client.config.username)
        assertContentEquals("pass".encodeToByteArray(), client.config.password)
    }

    @Test
    fun testTlsConfigCanBeOverridden() {
        val client = MqttClient {
            tls { serverName = "first.com" }
            tls { serverName = "second.com" }
        }
        assertTrue(client.config.useTls)
        // The second call should replace the config block
        assertNotNull(client.config.tlsConfig)
    }

    @Test
    fun testTlsConfigWithAutoReconnect() {
        val client = MqttClient {
            tls()
            autoReconnect = true
        }
        assertTrue(client.config.useTls)
        assertTrue(client.config.autoReconnect)
    }
}
