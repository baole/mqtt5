package io.github.mqtt5.protocol

import io.github.mqtt5.QoS
import io.github.mqtt5.ReasonCode
import io.github.mqtt5.SubscriptionOptions

/**
 * Sealed hierarchy representing all 15 MQTT v5.0 Control Packet types.
 */
sealed class MqttPacket {
    internal abstract val type: PacketType
}

// ─────────────────── CONNECT (1) ───────────────────

internal data class ConnectPacket(
    val cleanStart: Boolean = true,
    val keepAliveSeconds: Int = 60,
    val properties: MqttProperties = MqttProperties(),
    val clientId: String = "",
    val willProperties: MqttProperties? = null,
    val willTopic: String? = null,
    val willPayload: ByteArray? = null,
    val willQos: QoS = QoS.AT_MOST_ONCE,
    val willRetain: Boolean = false,
    val username: String? = null,
    val password: ByteArray? = null,
) : MqttPacket() {
    override val type = PacketType.CONNECT

    val hasWill: Boolean get() = willTopic != null
    val hasUsername: Boolean get() = username != null
    val hasPassword: Boolean get() = password != null

    /** Build the Connect Flags byte. */
    fun connectFlags(): Int {
        var flags = 0
        if (cleanStart) flags = flags or 0x02
        if (hasWill) {
            flags = flags or 0x04
            flags = flags or (willQos.value shl 3)
            if (willRetain) flags = flags or 0x20
        }
        if (hasPassword) flags = flags or 0x40
        if (hasUsername) flags = flags or 0x80
        return flags
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConnectPacket) return false
        return cleanStart == other.cleanStart && keepAliveSeconds == other.keepAliveSeconds &&
                clientId == other.clientId && willTopic == other.willTopic &&
                willQos == other.willQos && willRetain == other.willRetain &&
                username == other.username
    }

    override fun hashCode(): Int = clientId.hashCode()
}

// ─────────────────── CONNACK (2) ───────────────────

internal data class ConnackPacket(
    val sessionPresent: Boolean = false,
    val reasonCode: ReasonCode = ReasonCode.SUCCESS,
    val properties: MqttProperties = MqttProperties(),
) : MqttPacket() {
    override val type = PacketType.CONNACK
}

// ─────────────────── PUBLISH (3) ───────────────────

internal data class PublishPacket(
    val dup: Boolean = false,
    val qos: QoS = QoS.AT_MOST_ONCE,
    val retain: Boolean = false,
    val topicName: String = "",
    val packetId: Int? = null,
    val properties: MqttProperties = MqttProperties(),
    val payload: ByteArray = ByteArray(0),
) : MqttPacket() {
    override val type = PacketType.PUBLISH

    /** Fixed header flags for PUBLISH: DUP(3) QoS(2-1) RETAIN(0) */
    fun fixedHeaderFlags(): Int {
        var flags = 0
        if (dup) flags = flags or 0x08
        flags = flags or (qos.value shl 1)
        if (retain) flags = flags or 0x01
        return flags
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublishPacket) return false
        return dup == other.dup && qos == other.qos && retain == other.retain &&
                topicName == other.topicName && packetId == other.packetId &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = topicName.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

// ─────────────────── PUBACK (4) ───────────────────

internal data class PubackPacket(
    val packetId: Int,
    val reasonCode: ReasonCode = ReasonCode.SUCCESS,
    val properties: MqttProperties = MqttProperties(),
) : MqttPacket() {
    override val type = PacketType.PUBACK
}

// ─────────────────── PUBREC (5) ───────────────────

internal data class PubrecPacket(
    val packetId: Int,
    val reasonCode: ReasonCode = ReasonCode.SUCCESS,
    val properties: MqttProperties = MqttProperties(),
) : MqttPacket() {
    override val type = PacketType.PUBREC
}

// ─────────────────── PUBREL (6) ───────────────────

internal data class PubrelPacket(
    val packetId: Int,
    val reasonCode: ReasonCode = ReasonCode.SUCCESS,
    val properties: MqttProperties = MqttProperties(),
) : MqttPacket() {
    override val type = PacketType.PUBREL
}

// ─────────────────── PUBCOMP (7) ───────────────────

internal data class PubcompPacket(
    val packetId: Int,
    val reasonCode: ReasonCode = ReasonCode.SUCCESS,
    val properties: MqttProperties = MqttProperties(),
) : MqttPacket() {
    override val type = PacketType.PUBCOMP
}

// ─────────────────── SUBSCRIBE (8) ───────────────────

internal data class SubscribePacket(
    val packetId: Int,
    val properties: MqttProperties = MqttProperties(),
    val subscriptions: List<Pair<String, SubscriptionOptions>> = emptyList(),
) : MqttPacket() {
    override val type = PacketType.SUBSCRIBE
}

// ─────────────────── SUBACK (9) ───────────────────

internal data class SubackPacket(
    val packetId: Int,
    val properties: MqttProperties = MqttProperties(),
    val reasonCodes: List<ReasonCode> = emptyList(),
) : MqttPacket() {
    override val type = PacketType.SUBACK
}

// ─────────────────── UNSUBSCRIBE (10) ───────────────────

internal data class UnsubscribePacket(
    val packetId: Int,
    val properties: MqttProperties = MqttProperties(),
    val topicFilters: List<String> = emptyList(),
) : MqttPacket() {
    override val type = PacketType.UNSUBSCRIBE
}

// ─────────────────── UNSUBACK (11) ───────────────────

internal data class UnsubackPacket(
    val packetId: Int,
    val properties: MqttProperties = MqttProperties(),
    val reasonCodes: List<ReasonCode> = emptyList(),
) : MqttPacket() {
    override val type = PacketType.UNSUBACK
}

// ─────────────────── PINGREQ (12) ───────────────────

internal data object PingreqPacket : MqttPacket() {
    override val type = PacketType.PINGREQ
}

// ─────────────────── PINGRESP (13) ───────────────────

internal data object PingrespPacket : MqttPacket() {
    override val type = PacketType.PINGRESP
}

// ─────────────────── DISCONNECT (14) ───────────────────

internal data class DisconnectPacket(
    val reasonCode: ReasonCode = ReasonCode.NORMAL_DISCONNECTION,
    val properties: MqttProperties = MqttProperties(),
) : MqttPacket() {
    override val type = PacketType.DISCONNECT
}

// ─────────────────── AUTH (15) ───────────────────

data class AuthPacket(
    val reasonCode: ReasonCode = ReasonCode.SUCCESS,
    val properties: MqttProperties = MqttProperties(),
) : MqttPacket() {
    internal override val type = PacketType.AUTH
}
