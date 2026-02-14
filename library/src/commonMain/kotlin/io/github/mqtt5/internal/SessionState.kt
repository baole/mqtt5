package io.github.mqtt5.internal

import io.github.mqtt5.QoS
import io.github.mqtt5.protocol.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the MQTT session state between a client and server.
 *
 * Per MQTT v5 Section 4.1, session state includes:
 * - QoS 1 and QoS 2 messages sent but not fully acknowledged
 * - QoS 2 messages received but not fully acknowledged
 * - Subscriptions
 */
internal class SessionState {
    private val mutex = Mutex()

    /** Pending outbound QoS 1 messages awaiting PUBACK. */
    val pendingPuback = mutableMapOf<Int, PendingPublish>()

    /** Pending outbound QoS 2 messages in the PUBREC/PUBREL/PUBCOMP flow. */
    val pendingQos2Outbound = mutableMapOf<Int, Qos2OutboundState>()

    /** Pending inbound QoS 2 messages (received PUBLISH, awaiting PUBREL). */
    val pendingQos2Inbound = mutableSetOf<Int>()

    /** Pending subscribe acknowledgements. */
    private val pendingSuback = mutableMapOf<Int, CompletableDeferred<SubackPacket>>()

    /** Pending unsubscribe acknowledgements. */
    private val pendingUnsuback = mutableMapOf<Int, CompletableDeferred<UnsubackPacket>>()

    /** Active subscriptions: topicFilter -> QoS. */
    val subscriptions = mutableMapOf<String, QoS>()

    /** Server-negotiated capabilities from CONNACK. */
    var serverMaxQos: Int = 2
    var serverRetainAvailable: Boolean = true
    var serverMaxPacketSize: Long = Long.MAX_VALUE
    var serverTopicAliasMaximum: Int = 0
    var serverReceiveMaximum: Int = 65535
    var serverWildcardSubscriptionAvailable: Boolean = true
    var serverSubscriptionIdentifierAvailable: Boolean = true
    var serverSharedSubscriptionAvailable: Boolean = true
    var serverKeepAlive: Int? = null
    var assignedClientId: String? = null

    /** Current send quota for flow control. */
    var sendQuota: Int = 65535

    /** Whether the server indicated a session was present in the latest CONNACK. */
    var sessionPresent: Boolean = false

    /** In-flight QoS 1/2 messages saved for retry on reconnect (MQTT v5 Section 4.4). */
    var inflightForRetry: List<InflightMessage> = emptyList()
        private set

    suspend fun addPendingPuback(packetId: Int, publish: PendingPublish) = mutex.withLock {
        pendingPuback[packetId] = publish
    }

    suspend fun completePuback(packetId: Int): PendingPublish? = mutex.withLock {
        pendingPuback.remove(packetId)
    }

    suspend fun addPendingQos2Outbound(packetId: Int, state: Qos2OutboundState) = mutex.withLock {
        pendingQos2Outbound[packetId] = state
    }

    suspend fun getPendingQos2Outbound(packetId: Int): Qos2OutboundState? = mutex.withLock {
        pendingQos2Outbound[packetId]
    }

    suspend fun removePendingQos2Outbound(packetId: Int) = mutex.withLock {
        pendingQos2Outbound.remove(packetId)
    }

    suspend fun addPendingQos2Inbound(packetId: Int) = mutex.withLock {
        pendingQos2Inbound.add(packetId)
    }

    suspend fun removePendingQos2Inbound(packetId: Int) = mutex.withLock {
        pendingQos2Inbound.remove(packetId)
    }

    suspend fun isPendingQos2Inbound(packetId: Int): Boolean = mutex.withLock {
        packetId in pendingQos2Inbound
    }

    suspend fun addPendingSuback(packetId: Int, deferred: CompletableDeferred<SubackPacket>) = mutex.withLock {
        pendingSuback[packetId] = deferred
    }

    suspend fun completePendingSuback(packetId: Int, packet: SubackPacket) = mutex.withLock {
        pendingSuback[packetId]?.complete(packet)
    }

    suspend fun removePendingSuback(packetId: Int) = mutex.withLock {
        pendingSuback.remove(packetId)
    }

    suspend fun addPendingUnsuback(packetId: Int, deferred: CompletableDeferred<UnsubackPacket>) = mutex.withLock {
        pendingUnsuback[packetId] = deferred
    }

    suspend fun completePendingUnsuback(packetId: Int, packet: UnsubackPacket) = mutex.withLock {
        pendingUnsuback[packetId]?.complete(packet)
    }

    suspend fun removePendingUnsuback(packetId: Int) = mutex.withLock {
        pendingUnsuback.remove(packetId)
    }

    /**
     * Fail all pending subscribe/unsubscribe deferreds and clear the maps.
     */
    suspend fun failPendingSubackAndUnsuback(error: Exception) = mutex.withLock {
        pendingSuback.values.forEach { it.completeExceptionally(error) }
        pendingUnsuback.values.forEach { it.completeExceptionally(error) }
        pendingSuback.clear()
        pendingUnsuback.clear()
    }

    /**
     * Save in-flight QoS 1/2 messages for retry on reconnect.
     * Must be called before completing pending deferreds and before clearForReconnect().
     */
    suspend fun saveInflightForRetry() = mutex.withLock {
        val messages = mutableListOf<InflightMessage>()
        for ((id, pending) in pendingPuback) {
            messages.add(InflightMessage(id, pending.packet))
        }
        for ((id, state) in pendingQos2Outbound) {
            messages.add(InflightMessage(id, state.packet, state.pubrecReceived))
        }
        inflightForRetry = messages
    }

    suspend fun clearInflightRetry() = mutex.withLock {
        inflightForRetry = emptyList()
    }

    /**
     * Fail all pending QoS 1/2 deferreds exceptionally and clear the maps.
     * Must be called under the mutex to avoid races with the read loop.
     * NOTE: does NOT clear inflightForRetry — those messages were saved by
     * saveInflightForRetry() BEFORE this call and must survive until
     * retryInflightMessages() processes them on the next successful connect.
     */
    suspend fun failAndClearPending(error: Exception) = mutex.withLock {
        pendingPuback.values.forEach { it.deferred.completeExceptionally(error) }
        pendingQos2Outbound.values.forEach { it.deferred.completeExceptionally(error) }
        pendingPuback.clear()
        pendingQos2Outbound.clear()
    }

    fun updateFromConnack(connack: ConnackPacket) {
        sessionPresent = connack.sessionPresent
        val props = connack.properties
        props.maximumQos?.let { serverMaxQos = it }
        props.retainAvailable?.let { serverRetainAvailable = it == 1 }
        props.maximumPacketSize?.let { serverMaxPacketSize = it }
        props.topicAliasMaximum?.let { serverTopicAliasMaximum = it }
        props.receiveMaximum?.let { serverReceiveMaximum = it; sendQuota = it }
        props.wildcardSubscriptionAvailable?.let { serverWildcardSubscriptionAvailable = it == 1 }
        props.subscriptionIdentifierAvailable?.let { serverSubscriptionIdentifierAvailable = it == 1 }
        props.sharedSubscriptionAvailable?.let { serverSharedSubscriptionAvailable = it == 1 }
        props.serverKeepAlive?.let { serverKeepAlive = it }
        props.assignedClientIdentifier?.let { assignedClientId = it }
    }

    /**
     * Clear transient state for a new connection while keeping subscriptions
     * if session is being resumed.
     */
    suspend fun clearForReconnect(cleanStart: Boolean) = mutex.withLock {
        pendingPuback.clear()
        pendingQos2Outbound.clear()
        pendingQos2Inbound.clear()
        pendingSuback.clear()
        pendingUnsuback.clear()
        sendQuota = 65535
        if (cleanStart) {
            subscriptions.clear()
        }
    }
}

/**
 * Tracks a pending QoS 1 publish awaiting PUBACK.
 */
internal data class PendingPublish(
    val packet: PublishPacket,
    val deferred: CompletableDeferred<Unit> = CompletableDeferred(),
)

/**
 * Tracks the state of a QoS 2 outbound message flow.
 */
internal data class Qos2OutboundState(
    val packet: PublishPacket,
    val deferred: CompletableDeferred<Unit> = CompletableDeferred(),
    var pubrecReceived: Boolean = false,
)

/**
 * Represents an in-flight QoS 1/2 message saved for retry on reconnect.
 * Per MQTT v5 Section 4.4, these must be resent with the DUP flag when a session is resumed.
 */
internal data class InflightMessage(
    val packetId: Int,
    val packet: PublishPacket,
    val pubrecReceived: Boolean = false,
)
