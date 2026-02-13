package io.github.mqtt5

import io.github.mqtt5.protocol.MqttDecoder
import io.github.mqtt5.protocol.MqttEncoder
import io.github.mqtt5.protocol.variableByteIntegerSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MqttCodecTest {

    // ─────────────────── Variable Byte Integer ───────────────────

    @Test
    fun testVariableByteIntegerSingleByte() {
        val encoder = MqttEncoder()
        encoder.writeVariableByteInteger(0)
        val bytes = encoder.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(0, bytes[0].toInt())

        val decoder = MqttDecoder(bytes)
        assertEquals(0, decoder.readVariableByteInteger())
    }

    @Test
    fun testVariableByteInteger127() {
        val encoder = MqttEncoder()
        encoder.writeVariableByteInteger(127)
        val bytes = encoder.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(127, bytes[0].toInt() and 0xFF)

        assertEquals(127, MqttDecoder(bytes).readVariableByteInteger())
    }

    @Test
    fun testVariableByteInteger128() {
        val encoder = MqttEncoder()
        encoder.writeVariableByteInteger(128)
        val bytes = encoder.toByteArray()
        assertEquals(2, bytes.size)
        assertEquals(0x80, bytes[0].toInt() and 0xFF)
        assertEquals(0x01, bytes[1].toInt() and 0xFF)

        assertEquals(128, MqttDecoder(bytes).readVariableByteInteger())
    }

    @Test
    fun testVariableByteInteger16383() {
        val encoder = MqttEncoder()
        encoder.writeVariableByteInteger(16383)
        val bytes = encoder.toByteArray()
        assertEquals(2, bytes.size)

        assertEquals(16383, MqttDecoder(bytes).readVariableByteInteger())
    }

    @Test
    fun testVariableByteInteger16384() {
        val encoder = MqttEncoder()
        encoder.writeVariableByteInteger(16384)
        val bytes = encoder.toByteArray()
        assertEquals(3, bytes.size)

        assertEquals(16384, MqttDecoder(bytes).readVariableByteInteger())
    }

    @Test
    fun testVariableByteIntegerMaxValue() {
        val encoder = MqttEncoder()
        encoder.writeVariableByteInteger(268_435_455)
        val bytes = encoder.toByteArray()
        assertEquals(4, bytes.size)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xFF, bytes[1].toInt() and 0xFF)
        assertEquals(0xFF, bytes[2].toInt() and 0xFF)
        assertEquals(0x7F, bytes[3].toInt() and 0xFF)

        assertEquals(268_435_455, MqttDecoder(bytes).readVariableByteInteger())
    }

    @Test
    fun testVariableByteIntegerSize() {
        assertEquals(1, variableByteIntegerSize(0))
        assertEquals(1, variableByteIntegerSize(127))
        assertEquals(2, variableByteIntegerSize(128))
        assertEquals(2, variableByteIntegerSize(16383))
        assertEquals(3, variableByteIntegerSize(16384))
        assertEquals(3, variableByteIntegerSize(2_097_151))
        assertEquals(4, variableByteIntegerSize(2_097_152))
        assertEquals(4, variableByteIntegerSize(268_435_455))
    }

    // ─────────────────── Two Byte Integer ───────────────────

    @Test
    fun testTwoByteInteger() {
        val encoder = MqttEncoder()
        encoder.writeTwoByteInteger(0)
        encoder.writeTwoByteInteger(1)
        encoder.writeTwoByteInteger(256)
        encoder.writeTwoByteInteger(65535)
        val bytes = encoder.toByteArray()

        val decoder = MqttDecoder(bytes)
        assertEquals(0, decoder.readTwoByteInteger())
        assertEquals(1, decoder.readTwoByteInteger())
        assertEquals(256, decoder.readTwoByteInteger())
        assertEquals(65535, decoder.readTwoByteInteger())
    }

    // ─────────────────── Four Byte Integer ───────────────────

    @Test
    fun testFourByteInteger() {
        val encoder = MqttEncoder()
        encoder.writeFourByteInteger(0)
        encoder.writeFourByteInteger(1)
        encoder.writeFourByteInteger(0xFFFFFFFF)
        val bytes = encoder.toByteArray()

        val decoder = MqttDecoder(bytes)
        assertEquals(0L, decoder.readFourByteInteger())
        assertEquals(1L, decoder.readFourByteInteger())
        assertEquals(0xFFFFFFFFL, decoder.readFourByteInteger())
    }

    // ─────────────────── UTF-8 Encoded String ───────────────────

    @Test
    fun testUtf8String() {
        val encoder = MqttEncoder()
        encoder.writeUtf8String("")
        encoder.writeUtf8String("hello")
        encoder.writeUtf8String("MQTT")
        encoder.writeUtf8String("日本語")
        val bytes = encoder.toByteArray()

        val decoder = MqttDecoder(bytes)
        assertEquals("", decoder.readUtf8String())
        assertEquals("hello", decoder.readUtf8String())
        assertEquals("MQTT", decoder.readUtf8String())
        assertEquals("日本語", decoder.readUtf8String())
    }

    // ─────────────────── Binary Data ───────────────────

    @Test
    fun testBinaryData() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        val encoder = MqttEncoder()
        encoder.writeBinaryData(data)
        val bytes = encoder.toByteArray()

        val decoder = MqttDecoder(bytes)
        val result = decoder.readBinaryData()
        assertEquals(data.toList(), result.toList())
    }

    @Test
    fun testEmptyBinaryData() {
        val encoder = MqttEncoder()
        encoder.writeBinaryData(ByteArray(0))
        val bytes = encoder.toByteArray()

        val decoder = MqttDecoder(bytes)
        val result = decoder.readBinaryData()
        assertEquals(0, result.size)
    }

    // ─────────────────── String Pair ───────────────────

    @Test
    fun testStringPair() {
        val encoder = MqttEncoder()
        encoder.writeStringPair("key", "value")
        val bytes = encoder.toByteArray()

        val decoder = MqttDecoder(bytes)
        val (name, value) = decoder.readStringPair()
        assertEquals("key", name)
        assertEquals("value", value)
    }

    // ─────────────────── Error cases ───────────────────

    @Test
    fun testDecoderNotEnoughData() {
        val decoder = MqttDecoder(byteArrayOf(0x01))
        assertFailsWith<MqttProtocolException> {
            decoder.readTwoByteInteger()
        }
    }
}
