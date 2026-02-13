package io.github.mqtt5.protocol

import io.github.mqtt5.MqttProperties
import io.github.mqtt5.MqttProtocolException
import io.github.mqtt5.QoS
import io.github.mqtt5.ReasonCode
import io.github.mqtt5.SubscriptionOptions

/**
 * Decodes wire-format byte arrays into [MqttPacket] instances.
 */
internal object PacketDecoder {

    /**
     * Decode a complete MQTT packet from its wire-format bytes.
     *
     * @param data The complete packet data including Fixed Header.
     * @return The decoded [MqttPacket].
     */
    fun decode(data: ByteArray): MqttPacket {
        val decoder = MqttDecoder(data)
        val byte1 = decoder.readByte()
        val packetType = PacketType.fromValue(byte1 shr 4)
        val flags = byte1 and 0x0F
        val remainingLength = decoder.readVariableByteInteger()

        // Read the remaining bytes
        val bodyBytes = decoder.readBytes(remainingLength)
        val body = MqttDecoder(bodyBytes)

        return when (packetType) {
            PacketType.CONNECT -> decodeConnect(body)
            PacketType.CONNACK -> decodeConnack(body)
            PacketType.PUBLISH -> decodePublish(body, flags, remainingLength)
            PacketType.PUBACK -> decodePuback(body, remainingLength)
            PacketType.PUBREC -> decodePubrec(body, remainingLength)
            PacketType.PUBREL -> decodePubrel(body, remainingLength)
            PacketType.PUBCOMP -> decodePubcomp(body, remainingLength)
            PacketType.SUBSCRIBE -> decodeSubscribe(body)
            PacketType.SUBACK -> decodeSuback(body)
            PacketType.UNSUBSCRIBE -> decodeUnsubscribe(body)
            PacketType.UNSUBACK -> decodeUnsuback(body)
            PacketType.PINGREQ -> PingreqPacket
            PacketType.PINGRESP -> PingrespPacket
            PacketType.DISCONNECT -> decodeDisconnect(body, remainingLength)
            PacketType.AUTH -> decodeAuth(body, remainingLength)
        }
    }

    /**
     * Decode only the fixed header from raw bytes.
     * Returns (PacketType, flags, remainingLength, headerSize).
     */
    fun decodeFixedHeader(data: ByteArray): FixedHeader {
        val decoder = MqttDecoder(data)
        val byte1 = decoder.readByte()
        val packetType = PacketType.fromValue(byte1 shr 4)
        val flags = byte1 and 0x0F
        val remainingLength = decoder.readVariableByteInteger()
        val headerSize = data.size - decoder.remaining
        return FixedHeader(packetType, flags, remainingLength, headerSize)
    }

    data class FixedHeader(
        val packetType: PacketType,
        val flags: Int,
        val remainingLength: Int,
        val headerSize: Int,
    )

    // ─────────────────── CONNECT ───────────────────

    private fun decodeConnect(decoder: MqttDecoder): ConnectPacket {
        // Protocol Name
        val protocolName = decoder.readUtf8String()
        if (protocolName != "MQTT") {
            throw MqttProtocolException("Invalid protocol name: $protocolName")
        }
        // Protocol Version
        val protocolVersion = decoder.readByte()
        if (protocolVersion != 5) {
            throw MqttProtocolException("Unsupported protocol version: $protocolVersion")
        }
        // Connect Flags
        val flags = decoder.readByte()
        val cleanStart = (flags and 0x02) != 0
        val hasWill = (flags and 0x04) != 0
        val willQos = QoS.fromValue((flags shr 3) and 0x03)
        val willRetain = (flags and 0x20) != 0
        val hasPassword = (flags and 0x40) != 0
        val hasUsername = (flags and 0x80) != 0

        // Keep Alive
        val keepAlive = decoder.readTwoByteInteger()
        // Properties
        val properties = decodeMqttProperties(decoder)

        // Payload
        val clientId = decoder.readUtf8String()

        var willProperties: MqttProperties? = null
        var willTopic: String? = null
        var willPayload: ByteArray? = null

        if (hasWill) {
            willProperties = decodeMqttProperties(decoder)
            willTopic = decoder.readUtf8String()
            willPayload = decoder.readBinaryData()
        }

        val username = if (hasUsername) decoder.readUtf8String() else null
        val password = if (hasPassword) decoder.readBinaryData() else null

        return ConnectPacket(
            cleanStart = cleanStart,
            keepAliveSeconds = keepAlive,
            properties = properties,
            clientId = clientId,
            willProperties = willProperties,
            willTopic = willTopic,
            willPayload = willPayload,
            willQos = willQos,
            willRetain = willRetain,
            username = username,
            password = password,
        )
    }

    // ─────────────────── CONNACK ───────────────────

    private fun decodeConnack(decoder: MqttDecoder): ConnackPacket {
        val acknowledgeFlags = decoder.readByte()
        val sessionPresent = (acknowledgeFlags and 0x01) != 0
        val reasonCode = ReasonCode.fromValue(decoder.readByte())
        val properties = decodeMqttProperties(decoder)

        return ConnackPacket(
            sessionPresent = sessionPresent,
            reasonCode = reasonCode,
            properties = properties,
        )
    }

    // ─────────────────── PUBLISH ───────────────────

    private fun decodePublish(decoder: MqttDecoder, flags: Int, remainingLength: Int): PublishPacket {
        val dup = (flags and 0x08) != 0
        val qos = QoS.fromValue((flags shr 1) and 0x03)
        val retain = (flags and 0x01) != 0

        val startRemaining = decoder.remaining

        // Topic Name
        val topicName = decoder.readUtf8String()

        // Packet Identifier (only for QoS > 0)
        val packetId = if (qos.value > 0) decoder.readTwoByteInteger() else null

        // Properties
        val properties = decodeMqttProperties(decoder)

        // Payload: everything that remains
        val consumed = startRemaining - decoder.remaining
        val payloadLength = remainingLength - consumed
        val payload = if (payloadLength > 0) decoder.readBytes(payloadLength) else ByteArray(0)

        return PublishPacket(
            dup = dup,
            qos = qos,
            retain = retain,
            topicName = topicName,
            packetId = packetId,
            properties = properties,
            payload = payload,
        )
    }

    // ─────────────────── PUBACK ───────────────────

    private fun decodePuback(decoder: MqttDecoder, remainingLength: Int): PubackPacket {
        val packetId = decoder.readTwoByteInteger()
        if (remainingLength == 2) {
            return PubackPacket(packetId)
        }
        val reasonCode = ReasonCode.fromValue(decoder.readByte())
        val properties = if (remainingLength >= 4) decodeMqttProperties(decoder) else MqttProperties()
        return PubackPacket(packetId, reasonCode, properties)
    }

    // ─────────────────── PUBREC ───────────────────

    private fun decodePubrec(decoder: MqttDecoder, remainingLength: Int): PubrecPacket {
        val packetId = decoder.readTwoByteInteger()
        if (remainingLength == 2) {
            return PubrecPacket(packetId)
        }
        val reasonCode = ReasonCode.fromValue(decoder.readByte())
        val properties = if (remainingLength >= 4) decodeMqttProperties(decoder) else MqttProperties()
        return PubrecPacket(packetId, reasonCode, properties)
    }

    // ─────────────────── PUBREL ───────────────────

    private fun decodePubrel(decoder: MqttDecoder, remainingLength: Int): PubrelPacket {
        val packetId = decoder.readTwoByteInteger()
        if (remainingLength == 2) {
            return PubrelPacket(packetId)
        }
        val reasonCode = ReasonCode.fromValue(decoder.readByte())
        val properties = if (remainingLength >= 4) decodeMqttProperties(decoder) else MqttProperties()
        return PubrelPacket(packetId, reasonCode, properties)
    }

    // ─────────────────── PUBCOMP ───────────────────

    private fun decodePubcomp(decoder: MqttDecoder, remainingLength: Int): PubcompPacket {
        val packetId = decoder.readTwoByteInteger()
        if (remainingLength == 2) {
            return PubcompPacket(packetId)
        }
        val reasonCode = ReasonCode.fromValue(decoder.readByte())
        val properties = if (remainingLength >= 4) decodeMqttProperties(decoder) else MqttProperties()
        return PubcompPacket(packetId, reasonCode, properties)
    }

    // ─────────────────── SUBSCRIBE ───────────────────

    private fun decodeSubscribe(decoder: MqttDecoder): SubscribePacket {
        val packetId = decoder.readTwoByteInteger()
        val properties = decodeMqttProperties(decoder)

        val subscriptions = mutableListOf<Pair<String, SubscriptionOptions>>()
        while (!decoder.isExhausted) {
            val topicFilter = decoder.readUtf8String()
            val options = SubscriptionOptions.decode(decoder.readByte())
            subscriptions.add(topicFilter to options)
        }

        return SubscribePacket(packetId, properties, subscriptions)
    }

    // ─────────────────── SUBACK ───────────────────

    private fun decodeSuback(decoder: MqttDecoder): SubackPacket {
        val packetId = decoder.readTwoByteInteger()
        val properties = decodeMqttProperties(decoder)

        val reasonCodes = mutableListOf<ReasonCode>()
        while (!decoder.isExhausted) {
            reasonCodes.add(ReasonCode.fromValue(decoder.readByte()))
        }

        return SubackPacket(packetId, properties, reasonCodes)
    }

    // ─────────────────── UNSUBSCRIBE ───────────────────

    private fun decodeUnsubscribe(decoder: MqttDecoder): UnsubscribePacket {
        val packetId = decoder.readTwoByteInteger()
        val properties = decodeMqttProperties(decoder)

        val topicFilters = mutableListOf<String>()
        while (!decoder.isExhausted) {
            topicFilters.add(decoder.readUtf8String())
        }

        return UnsubscribePacket(packetId, properties, topicFilters)
    }

    // ─────────────────── UNSUBACK ───────────────────

    private fun decodeUnsuback(decoder: MqttDecoder): UnsubackPacket {
        val packetId = decoder.readTwoByteInteger()
        val properties = decodeMqttProperties(decoder)

        val reasonCodes = mutableListOf<ReasonCode>()
        while (!decoder.isExhausted) {
            reasonCodes.add(ReasonCode.fromValue(decoder.readByte()))
        }

        return UnsubackPacket(packetId, properties, reasonCodes)
    }

    // ─────────────────── DISCONNECT ───────────────────

    private fun decodeDisconnect(decoder: MqttDecoder, remainingLength: Int): DisconnectPacket {
        if (remainingLength == 0) {
            return DisconnectPacket()
        }
        val reasonCode = ReasonCode.fromValue(decoder.readByte())
        val properties = if (remainingLength >= 2) decodeMqttProperties(decoder) else MqttProperties()
        return DisconnectPacket(reasonCode, properties)
    }

    // ─────────────────── AUTH ───────────────────

    private fun decodeAuth(decoder: MqttDecoder, remainingLength: Int): AuthPacket {
        if (remainingLength == 0) {
            return AuthPacket()
        }
        val reasonCode = ReasonCode.fromValue(decoder.readByte())
        val properties = if (remainingLength >= 2) decodeMqttProperties(decoder) else MqttProperties()
        return AuthPacket(reasonCode, properties)
    }
}
