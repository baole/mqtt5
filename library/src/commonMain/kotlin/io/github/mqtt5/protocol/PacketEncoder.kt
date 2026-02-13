package io.github.mqtt5.protocol

import io.github.mqtt5.MqttProperties
import io.github.mqtt5.MqttProtocolException

/**
 * Encodes [MqttPacket] instances into wire-format byte arrays.
 *
 * Each packet is encoded as: Fixed Header (type + flags + remaining length) + Variable Header + Payload.
 */
internal object PacketEncoder {

    /**
     * Encode an [MqttPacket] into its full wire-format byte array.
     */
    fun encode(packet: MqttPacket): ByteArray {
        return when (packet) {
            is ConnectPacket -> encodeConnect(packet)
            is ConnackPacket -> encodeConnack(packet)
            is PublishPacket -> encodePublish(packet)
            is PubackPacket -> encodePuback(packet)
            is PubrecPacket -> encodePubrec(packet)
            is PubrelPacket -> encodePubrel(packet)
            is PubcompPacket -> encodePubcomp(packet)
            is SubscribePacket -> encodeSubscribe(packet)
            is SubackPacket -> encodeSuback(packet)
            is UnsubscribePacket -> encodeUnsubscribe(packet)
            is UnsubackPacket -> encodeUnsuback(packet)
            PingreqPacket -> encodePingreq()
            PingrespPacket -> encodePingresp()
            is DisconnectPacket -> encodeDisconnect(packet)
            is AuthPacket -> encodeAuth(packet)
        }
    }

    // ─────────────────── CONNECT ───────────────────

    private fun encodeConnect(packet: ConnectPacket): ByteArray {
        val variableHeader = MqttEncoder()

        // Protocol Name: "MQTT"
        variableHeader.writeUtf8String("MQTT")
        // Protocol Version: 5
        variableHeader.writeByte(5)
        // Connect Flags
        variableHeader.writeByte(packet.connectFlags())
        // Keep Alive
        variableHeader.writeTwoByteInteger(packet.keepAliveSeconds)
        // Properties
        val propsBytes = packet.properties.encode()
        variableHeader.writeBytes(propsBytes)

        // Payload
        val payload = MqttEncoder()
        // Client Identifier
        payload.writeUtf8String(packet.clientId)
        // Will Properties + Will Topic + Will Payload
        if (packet.hasWill) {
            val willPropsBytes = (packet.willProperties ?: MqttProperties()).encode()
            payload.writeBytes(willPropsBytes)
            payload.writeUtf8String(packet.willTopic!!)
            payload.writeBinaryData(packet.willPayload ?: ByteArray(0))
        }
        // Username
        if (packet.hasUsername) {
            payload.writeUtf8String(packet.username!!)
        }
        // Password
        if (packet.hasPassword) {
            payload.writeBinaryData(packet.password!!)
        }

        return buildFixedHeader(PacketType.CONNECT, 0, variableHeader, payload)
    }

    // ─────────────────── CONNACK ───────────────────

    private fun encodeConnack(packet: ConnackPacket): ByteArray {
        val variableHeader = MqttEncoder()
        // Connect Acknowledge Flags
        variableHeader.writeByte(if (packet.sessionPresent) 1 else 0)
        // Reason Code
        variableHeader.writeByte(packet.reasonCode.value)
        // Properties
        variableHeader.writeBytes(packet.properties.encode())

        return buildFixedHeader(PacketType.CONNACK, 0, variableHeader)
    }

    // ─────────────────── PUBLISH ───────────────────

    private fun encodePublish(packet: PublishPacket): ByteArray {
        val variableHeader = MqttEncoder()
        // Topic Name
        variableHeader.writeUtf8String(packet.topicName)
        // Packet Identifier (only for QoS > 0)
        if (packet.qos.value > 0) {
            variableHeader.writeTwoByteInteger(packet.packetId ?: throw MqttProtocolException("Packet ID required for QoS > 0"))
        }
        // Properties
        variableHeader.writeBytes(packet.properties.encode())

        // Payload is appended directly (no length prefix)
        val payload = MqttEncoder()
        payload.writeBytes(packet.payload)

        return buildFixedHeader(PacketType.PUBLISH, packet.fixedHeaderFlags(), variableHeader, payload)
    }

    // ─────────────────── PUBACK ───────────────────

    private fun encodePuback(packet: PubackPacket): ByteArray {
        val variableHeader = MqttEncoder()
        variableHeader.writeTwoByteInteger(packet.packetId)

        // Optimization: if reason code is 0x00 and no properties, omit them
        if (packet.reasonCode == io.github.mqtt5.ReasonCode.SUCCESS && packet.properties.isEmpty) {
            return buildFixedHeader(PacketType.PUBACK, 0, variableHeader)
        }

        variableHeader.writeByte(packet.reasonCode.value)
        if (!packet.properties.isEmpty) {
            variableHeader.writeBytes(packet.properties.encode())
        } else {
            variableHeader.writeVariableByteInteger(0) // empty properties
        }
        return buildFixedHeader(PacketType.PUBACK, 0, variableHeader)
    }

    // ─────────────────── PUBREC ───────────────────

    private fun encodePubrec(packet: PubrecPacket): ByteArray {
        val variableHeader = MqttEncoder()
        variableHeader.writeTwoByteInteger(packet.packetId)

        if (packet.reasonCode == io.github.mqtt5.ReasonCode.SUCCESS && packet.properties.isEmpty) {
            return buildFixedHeader(PacketType.PUBREC, 0, variableHeader)
        }

        variableHeader.writeByte(packet.reasonCode.value)
        if (!packet.properties.isEmpty) {
            variableHeader.writeBytes(packet.properties.encode())
        } else {
            variableHeader.writeVariableByteInteger(0)
        }
        return buildFixedHeader(PacketType.PUBREC, 0, variableHeader)
    }

    // ─────────────────── PUBREL ───────────────────

    private fun encodePubrel(packet: PubrelPacket): ByteArray {
        val variableHeader = MqttEncoder()
        variableHeader.writeTwoByteInteger(packet.packetId)

        if (packet.reasonCode == io.github.mqtt5.ReasonCode.SUCCESS && packet.properties.isEmpty) {
            // Fixed header flags for PUBREL are 0010
            return buildFixedHeader(PacketType.PUBREL, 0x02, variableHeader)
        }

        variableHeader.writeByte(packet.reasonCode.value)
        if (!packet.properties.isEmpty) {
            variableHeader.writeBytes(packet.properties.encode())
        } else {
            variableHeader.writeVariableByteInteger(0)
        }
        return buildFixedHeader(PacketType.PUBREL, 0x02, variableHeader)
    }

    // ─────────────────── PUBCOMP ───────────────────

    private fun encodePubcomp(packet: PubcompPacket): ByteArray {
        val variableHeader = MqttEncoder()
        variableHeader.writeTwoByteInteger(packet.packetId)

        if (packet.reasonCode == io.github.mqtt5.ReasonCode.SUCCESS && packet.properties.isEmpty) {
            return buildFixedHeader(PacketType.PUBCOMP, 0, variableHeader)
        }

        variableHeader.writeByte(packet.reasonCode.value)
        if (!packet.properties.isEmpty) {
            variableHeader.writeBytes(packet.properties.encode())
        } else {
            variableHeader.writeVariableByteInteger(0)
        }
        return buildFixedHeader(PacketType.PUBCOMP, 0, variableHeader)
    }

    // ─────────────────── SUBSCRIBE ───────────────────

    private fun encodeSubscribe(packet: SubscribePacket): ByteArray {
        val variableHeader = MqttEncoder()
        // Packet Identifier
        variableHeader.writeTwoByteInteger(packet.packetId)
        // Properties
        variableHeader.writeBytes(packet.properties.encode())

        // Payload: list of (Topic Filter + Subscription Options)
        val payload = MqttEncoder()
        for ((topicFilter, options) in packet.subscriptions) {
            payload.writeUtf8String(topicFilter)
            payload.writeByte(options.encode())
        }

        // Fixed header flags for SUBSCRIBE are 0010
        return buildFixedHeader(PacketType.SUBSCRIBE, 0x02, variableHeader, payload)
    }

    // ─────────────────── SUBACK ───────────────────

    private fun encodeSuback(packet: SubackPacket): ByteArray {
        val variableHeader = MqttEncoder()
        variableHeader.writeTwoByteInteger(packet.packetId)
        variableHeader.writeBytes(packet.properties.encode())

        val payload = MqttEncoder()
        for (rc in packet.reasonCodes) {
            payload.writeByte(rc.value)
        }

        return buildFixedHeader(PacketType.SUBACK, 0, variableHeader, payload)
    }

    // ─────────────────── UNSUBSCRIBE ───────────────────

    private fun encodeUnsubscribe(packet: UnsubscribePacket): ByteArray {
        val variableHeader = MqttEncoder()
        variableHeader.writeTwoByteInteger(packet.packetId)
        variableHeader.writeBytes(packet.properties.encode())

        val payload = MqttEncoder()
        for (filter in packet.topicFilters) {
            payload.writeUtf8String(filter)
        }

        // Fixed header flags for UNSUBSCRIBE are 0010
        return buildFixedHeader(PacketType.UNSUBSCRIBE, 0x02, variableHeader, payload)
    }

    // ─────────────────── UNSUBACK ───────────────────

    private fun encodeUnsuback(packet: UnsubackPacket): ByteArray {
        val variableHeader = MqttEncoder()
        variableHeader.writeTwoByteInteger(packet.packetId)
        variableHeader.writeBytes(packet.properties.encode())

        val payload = MqttEncoder()
        for (rc in packet.reasonCodes) {
            payload.writeByte(rc.value)
        }

        return buildFixedHeader(PacketType.UNSUBACK, 0, variableHeader, payload)
    }

    // ─────────────────── PINGREQ ───────────────────

    private fun encodePingreq(): ByteArray = byteArrayOf(0xC0.toByte(), 0x00)

    // ─────────────────── PINGRESP ───────────────────

    private fun encodePingresp(): ByteArray = byteArrayOf(0xD0.toByte(), 0x00)

    // ─────────────────── DISCONNECT ───────────────────

    private fun encodeDisconnect(packet: DisconnectPacket): ByteArray {
        // Optimization: if reason code is 0x00 and no properties, remaining length is 0
        if (packet.reasonCode == io.github.mqtt5.ReasonCode.NORMAL_DISCONNECTION && packet.properties.isEmpty) {
            return byteArrayOf(0xE0.toByte(), 0x00)
        }

        val variableHeader = MqttEncoder()
        variableHeader.writeByte(packet.reasonCode.value)

        if (!packet.properties.isEmpty) {
            variableHeader.writeBytes(packet.properties.encode())
        } else {
            variableHeader.writeVariableByteInteger(0)
        }

        return buildFixedHeader(PacketType.DISCONNECT, 0, variableHeader)
    }

    // ─────────────────── AUTH ───────────────────

    private fun encodeAuth(packet: AuthPacket): ByteArray {
        if (packet.reasonCode == io.github.mqtt5.ReasonCode.SUCCESS && packet.properties.isEmpty) {
            return byteArrayOf(0xF0.toByte(), 0x00)
        }

        val variableHeader = MqttEncoder()
        variableHeader.writeByte(packet.reasonCode.value)
        variableHeader.writeBytes(packet.properties.encode())

        return buildFixedHeader(PacketType.AUTH, 0, variableHeader)
    }

    // ─────────────────── Helper ───────────────────

    /**
     * Build the complete packet: Fixed Header + Variable Header + Payload.
     */
    private fun buildFixedHeader(
        packetType: PacketType,
        flags: Int,
        variableHeader: MqttEncoder,
        payload: MqttEncoder? = null,
    ): ByteArray {
        val vhBytes = variableHeader.toByteArray()
        val plBytes = payload?.toByteArray() ?: ByteArray(0)
        val remainingLength = vhBytes.size + plBytes.size

        val result = MqttEncoder()
        // Byte 1: packet type (4 bits) + flags (4 bits)
        result.writeByte((packetType.value shl 4) or (flags and 0x0F))
        // Remaining Length
        result.writeVariableByteInteger(remainingLength)
        // Variable Header
        result.writeBytes(vhBytes)
        // Payload
        result.writeBytes(plBytes)

        return result.toByteArray()
    }
}
