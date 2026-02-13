package io.github.mqtt5.protocol

import io.github.mqtt5.MqttProtocolException

// ─────────────────── MqttEncoder ───────────────────

/**
 * A growable byte buffer used to encode MQTT packet data.
 */
internal class MqttEncoder {
    private var buffer = ByteArray(256)
    private var position = 0

    val size: Int get() = position

    fun toByteArray(): ByteArray = buffer.copyOf(position)

    // --- Primitive writes ---

    fun writeByte(value: Int) {
        ensureCapacity(1)
        buffer[position++] = value.toByte()
    }

    fun writeTwoByteInteger(value: Int) {
        ensureCapacity(2)
        buffer[position++] = (value shr 8).toByte()
        buffer[position++] = (value and 0xFF).toByte()
    }

    fun writeFourByteInteger(value: Long) {
        ensureCapacity(4)
        buffer[position++] = ((value shr 24) and 0xFF).toByte()
        buffer[position++] = ((value shr 16) and 0xFF).toByte()
        buffer[position++] = ((value shr 8) and 0xFF).toByte()
        buffer[position++] = (value and 0xFF).toByte()
    }

    fun writeBytes(data: ByteArray) {
        ensureCapacity(data.size)
        data.copyInto(buffer, position)
        position += data.size
    }

    // --- MQTT-specific writes ---

    /**
     * Encode a Variable Byte Integer per MQTT 1.5.5.
     */
    fun writeVariableByteInteger(value: Int) {
        require(value in 0..268_435_455) { "Variable Byte Integer out of range: $value" }
        var x = value
        do {
            var encodedByte = x % 128
            x /= 128
            if (x > 0) encodedByte = encodedByte or 128
            writeByte(encodedByte)
        } while (x > 0)
    }

    /**
     * Write a UTF-8 Encoded String per MQTT 1.5.4.
     * Prefixed with a Two Byte Integer length.
     */
    fun writeUtf8String(value: String) {
        val bytes = value.encodeToByteArray()
        require(bytes.size <= 65535) { "UTF-8 string too long: ${bytes.size}" }
        writeTwoByteInteger(bytes.size)
        writeBytes(bytes)
    }

    /**
     * Write Binary Data per MQTT 1.5.6.
     * Prefixed with a Two Byte Integer length.
     */
    fun writeBinaryData(data: ByteArray) {
        require(data.size <= 65535) { "Binary data too long: ${data.size}" }
        writeTwoByteInteger(data.size)
        writeBytes(data)
    }

    /**
     * Write a UTF-8 String Pair per MQTT 1.5.7.
     */
    fun writeStringPair(name: String, value: String) {
        writeUtf8String(name)
        writeUtf8String(value)
    }

    private fun ensureCapacity(additional: Int) {
        if (position + additional > buffer.size) {
            val newSize = maxOf(buffer.size * 2, position + additional)
            buffer = buffer.copyOf(newSize)
        }
    }
}

// ─────────────────── MqttDecoder ───────────────────

/**
 * Reads MQTT-encoded data from a byte array with a movable cursor.
 */
internal class MqttDecoder(private val data: ByteArray, private var position: Int = 0) {
    val remaining: Int get() = data.size - position
    val isExhausted: Boolean get() = position >= data.size

    fun readByte(): Int {
        checkRemaining(1)
        return data[position++].toInt() and 0xFF
    }

    fun readTwoByteInteger(): Int {
        checkRemaining(2)
        val msb = data[position++].toInt() and 0xFF
        val lsb = data[position++].toInt() and 0xFF
        return (msb shl 8) or lsb
    }

    fun readFourByteInteger(): Long {
        checkRemaining(4)
        val b0 = (data[position++].toInt() and 0xFF).toLong()
        val b1 = (data[position++].toInt() and 0xFF).toLong()
        val b2 = (data[position++].toInt() and 0xFF).toLong()
        val b3 = (data[position++].toInt() and 0xFF).toLong()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readBytes(count: Int): ByteArray {
        checkRemaining(count)
        val result = data.copyOfRange(position, position + count)
        position += count
        return result
    }

    /**
     * Decode a Variable Byte Integer per MQTT 1.5.5.
     */
    fun readVariableByteInteger(): Int {
        var multiplier = 1
        var value = 0
        var encodedByte: Int
        do {
            encodedByte = readByte()
            value += (encodedByte and 127) * multiplier
            if (multiplier > 128 * 128 * 128) {
                throw MqttProtocolException("Malformed Variable Byte Integer")
            }
            multiplier *= 128
        } while ((encodedByte and 128) != 0)
        return value
    }

    /**
     * Read a UTF-8 Encoded String per MQTT 1.5.4.
     */
    fun readUtf8String(): String {
        val length = readTwoByteInteger()
        val bytes = readBytes(length)
        return bytes.decodeToString()
    }

    /**
     * Read Binary Data per MQTT 1.5.6.
     */
    fun readBinaryData(): ByteArray {
        val length = readTwoByteInteger()
        return readBytes(length)
    }

    /**
     * Read a UTF-8 String Pair per MQTT 1.5.7.
     */
    fun readStringPair(): Pair<String, String> {
        val name = readUtf8String()
        val value = readUtf8String()
        return name to value
    }

    /**
     * Create a sub-decoder that reads from a portion of this decoder's remaining bytes.
     */
    fun readSlice(length: Int): MqttDecoder {
        checkRemaining(length)
        val slice = MqttDecoder(data.copyOfRange(position, position + length))
        position += length
        return slice
    }

    private fun checkRemaining(needed: Int) {
        if (remaining < needed) {
            throw MqttProtocolException(
                "Not enough data: need $needed bytes but only $remaining available"
            )
        }
    }
}

// ─────────────────── Utility functions ───────────────────

/**
 * Calculate the size of a Variable Byte Integer encoding.
 */
internal fun variableByteIntegerSize(value: Int): Int {
    require(value in 0..268_435_455)
    return when {
        value <= 127 -> 1
        value <= 16_383 -> 2
        value <= 2_097_151 -> 3
        else -> 4
    }
}

/**
 * Encode a Variable Byte Integer to a byte array.
 */
internal fun encodeVariableByteInteger(value: Int): ByteArray {
    val encoder = MqttEncoder()
    encoder.writeVariableByteInteger(value)
    return encoder.toByteArray()
}

/**
 * MQTT v5 Packet types (4-bit values in the fixed header).
 */
internal enum class PacketType(val value: Int) {
    CONNECT(1),
    CONNACK(2),
    PUBLISH(3),
    PUBACK(4),
    PUBREC(5),
    PUBREL(6),
    PUBCOMP(7),
    SUBSCRIBE(8),
    SUBACK(9),
    UNSUBSCRIBE(10),
    UNSUBACK(11),
    PINGREQ(12),
    PINGRESP(13),
    DISCONNECT(14),
    AUTH(15);

    companion object {
        fun fromValue(value: Int): PacketType =
            entries.firstOrNull { it.value == value }
                ?: throw MqttProtocolException("Unknown packet type: $value")
    }
}
