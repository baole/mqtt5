package io.github.mqtt5.internal

import io.github.mqtt5.MqttException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe manager for MQTT Packet Identifiers.
 *
 * Packet Identifiers are 16-bit unsigned integers (1..65535).
 * Each allocated ID must be unique until released.
 */
internal class PacketIdManager {
    private val mutex = Mutex()
    private val inUse = mutableSetOf<Int>()
    private var nextId = 1

    /**
     * Allocate the next available Packet Identifier.
     * @throws MqttException if all 65535 IDs are in use.
     */
    suspend fun allocate(): Int = mutex.withLock {
        if (inUse.size >= 65535) {
            throw MqttException("All packet identifiers are in use")
        }
        while (nextId in inUse) {
            nextId = if (nextId >= 65535) 1 else nextId + 1
        }
        val id = nextId
        inUse.add(id)
        nextId = if (nextId >= 65535) 1 else nextId + 1
        id
    }

    /**
     * Release a Packet Identifier so it can be reused.
     */
    suspend fun release(id: Int) = mutex.withLock {
        inUse.remove(id)
    }

    /**
     * Check if a Packet Identifier is currently in use.
     */
    suspend fun isInUse(id: Int): Boolean = mutex.withLock {
        id in inUse
    }

    /**
     * Reset all packet identifiers (e.g., on reconnect).
     */
    suspend fun reset() = mutex.withLock {
        inUse.clear()
        nextId = 1
    }
}
