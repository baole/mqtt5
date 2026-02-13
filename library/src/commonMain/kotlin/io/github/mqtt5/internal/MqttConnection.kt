package io.github.mqtt5.internal

import io.github.mqtt5.MqttConnectionException
import io.github.mqtt5.MqttProtocolException
import io.github.mqtt5.protocol.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * Manages the raw TCP/TLS connection to an MQTT broker using Ktor networking.
 *
 * Responsible for:
 * - Establishing and closing TCP/TLS connections
 * - Sending MQTT packets (serializing to bytes)
 * - Reading MQTT packets (deserializing from bytes)
 */
internal class MqttConnection {
    private var socket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null
    private var selectorManager: SelectorManager? = null
    private val writeMutex = Mutex()

    val isConnected: Boolean get() = socket != null && !socket!!.isClosed

    /**
     * Open a TCP connection to the broker, optionally upgrading to TLS.
     */
    suspend fun connect(host: String, port: Int, useTls: Boolean = false) {
        try {
            val sm = SelectorManager(Dispatchers.Default)
            selectorManager = sm

            val builder = aSocket(sm).tcp()
            var rawSocket = builder.connect(host, port)

            if (useTls) {
                rawSocket = rawSocket.tls(coroutineContext) {
                    // Use default TLS settings (system trust store)
                }
            }

            socket = rawSocket
            readChannel = rawSocket.openReadChannel()
            writeChannel = rawSocket.openWriteChannel(autoFlush = false)
        } catch (e: Exception) {
            close()
            throw MqttConnectionException("Failed to connect to $host:$port", e)
        }
    }

    /**
     * Send an MQTT packet over the connection.
     */
    suspend fun sendPacket(packet: MqttPacket) {
        val wc = writeChannel ?: throw MqttConnectionException("Not connected")
        val bytes = PacketEncoder.encode(packet)
        writeMutex.withLock {
            try {
                wc.writeFully(bytes, 0, bytes.size)
                wc.flush()
            } catch (e: Exception) {
                throw MqttConnectionException("Failed to send ${packet.type}", e)
            }
        }
    }

    /**
     * Read the next MQTT packet from the connection.
     * This is a suspending call that blocks until a complete packet is available.
     *
     * @return The decoded packet, or null if the connection was closed.
     */
    suspend fun readPacket(): MqttPacket? {
        val rc = readChannel ?: throw MqttConnectionException("Not connected")

        try {
            // Read fixed header byte 1
            val byte1 = rc.readByte().toInt() and 0xFF

            // Read remaining length (Variable Byte Integer)
            var multiplier = 1
            var remainingLength = 0
            var encodedByte: Int
            do {
                encodedByte = rc.readByte().toInt() and 0xFF
                remainingLength += (encodedByte and 127) * multiplier
                if (multiplier > 128 * 128 * 128) {
                    throw MqttProtocolException("Malformed Variable Byte Integer in fixed header")
                }
                multiplier *= 128
            } while ((encodedByte and 128) != 0)

            // Read the remaining bytes
            val body = ByteArray(remainingLength)
            if (remainingLength > 0) {
                rc.readFully(body, 0, remainingLength)
            }

            // Reconstruct the full packet bytes for decoding
            val headerEncoder = MqttEncoder()
            headerEncoder.writeByte(byte1)
            headerEncoder.writeVariableByteInteger(remainingLength)
            val headerBytes = headerEncoder.toByteArray()

            val fullPacket = ByteArray(headerBytes.size + remainingLength)
            headerBytes.copyInto(fullPacket, 0)
            body.copyInto(fullPacket, headerBytes.size)

            return PacketDecoder.decode(fullPacket)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MqttProtocolException) {
            throw e
        } catch (e: Exception) {
            if (isConnected) {
                throw MqttConnectionException("Failed to read packet", e)
            }
            return null
        }
    }

    /**
     * Close the connection gracefully.
     */
    suspend fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        try {
            selectorManager?.close()
        } catch (_: Exception) {
        }
        socket = null
        readChannel = null
        writeChannel = null
        selectorManager = null
    }
}
