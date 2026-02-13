package io.github.mqtt5

import io.github.mqtt5.internal.*
import io.github.mqtt5.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * MQTT v5.0 Client.
 *
 * Provides a simple, coroutine-based API for connecting to an MQTT broker,
 * publishing messages, and subscribing to topics.
 *
 * ## Usage
 * ```kotlin
 * val client = MqttClient {
 *     host = "broker.example.com"
 *     port = 1883
 *     clientId = "my-client"
 *     cleanStart = true
 *     keepAlive = 60.seconds
 * }
 *
 * client.connect()
 * client.subscribe("sensor/#", QoS.AT_LEAST_ONCE)
 * client.messages.collect { message ->
 *     println("${message.topic}: ${message.payloadAsString}")
 * }
 * client.publish("sensor/temp", "22.5".encodeToByteArray(), QoS.AT_LEAST_ONCE)
 * client.disconnect()
 * ```
 */
class MqttClient(configure: MqttConfig.() -> Unit = {}) {
    val config = MqttConfig().apply(configure)

    private val connection = MqttConnection()
    private val packetIdManager = PacketIdManager()
    private val sessionState = SessionState()
    private var topicAliasManagerInbound = TopicAliasManager(0)
    private var topicAliasManagerOutbound = TopicAliasManager(0)

    private val _messages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 256)

    /** Flow of incoming messages matching active subscriptions. */
    val messages: SharedFlow<MqttMessage> = _messages.asSharedFlow()

    private var clientScope: CoroutineScope? = null
    private var readJob: Job? = null
    private var keepAliveJob: Job? = null

    /**
     * Callback-based message listener as an alternative to the Flow API.
     * Set this before calling [connect] if you prefer callbacks.
     */
    var onMessage: ((MqttMessage) -> Unit)? = null

    /** Called when the connection is lost unexpectedly. */
    var onDisconnect: ((Throwable?) -> Unit)? = null

    /** Called when an AUTH packet is received (for enhanced authentication). */
    var onAuth: (suspend (AuthPacket) -> AuthPacket?)? = null

    /** Current connection state. */
    @kotlin.concurrent.Volatile
    var isConnected: Boolean = false
        private set

    /** The actual client ID (may be server-assigned). */
    val clientId: String
        get() = sessionState.assignedClientId ?: config.clientId

    // ─────────────────── Connect ───────────────────

    /**
     * Connect to the MQTT broker.
     *
     * @throws MqttConnectException if the server refuses the connection.
     * @throws MqttConnectionException if the network connection fails.
     */
    suspend fun connect() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        clientScope = scope

        // Open TCP/TLS connection
        connection.connect(config.host, config.port, config.useTls)

        // Build CONNECT packet
        val connectProps = MqttProperties().apply {
            if (config.sessionExpiryInterval > 0) {
                sessionExpiryInterval = config.sessionExpiryInterval
            }
            if (config.receiveMaximum != 65535) {
                receiveMaximum = config.receiveMaximum
            }
            if (config.maximumPacketSize > 0) {
                maximumPacketSize = config.maximumPacketSize
            }
            if (config.topicAliasMaximum > 0) {
                topicAliasMaximum = config.topicAliasMaximum
            }
            if (config.requestResponseInformation) {
                requestResponseInformation = 1
            }
            if (!config.requestProblemInformation) {
                requestProblemInformation = 0
            }
            if (config.userProperties.isNotEmpty()) {
                userProperties = config.userProperties.toMutableList()
            }
            config.authenticationMethod?.let { authenticationMethod = it }
            config.authenticationData?.let { authenticationData = it }
        }

        val connectPacket = ConnectPacket(
            cleanStart = config.cleanStart,
            keepAliveSeconds = config.keepAlive.inWholeSeconds.toInt(),
            properties = connectProps,
            clientId = config.clientId,
            willProperties = config.will?.buildProperties(),
            willTopic = config.will?.topic,
            willPayload = config.will?.payload,
            willQos = config.will?.qos ?: QoS.AT_MOST_ONCE,
            willRetain = config.will?.retain ?: false,
            username = config.username,
            password = config.password,
        )

        sessionState.clearForReconnect(config.cleanStart)

        // Send CONNECT
        connection.sendPacket(connectPacket)

        // Wait for CONNACK (or AUTH for enhanced auth)
        val response = connection.readPacket()
            ?: throw MqttConnectionException("Connection closed before CONNACK received")

        when (response) {
            is ConnackPacket -> handleConnack(response)
            is AuthPacket -> handleEnhancedAuth(response)
            else -> throw MqttProtocolException("Expected CONNACK or AUTH, got ${response.type}")
        }

        isConnected = true

        // Start background reader
        readJob = scope.launch { readLoop() }

        // Start keep-alive
        startKeepAlive()
    }

    private suspend fun handleConnack(connack: ConnackPacket) {
        if (connack.reasonCode.isError) {
            connection.close()
            throw MqttConnectException(connack.reasonCode)
        }
        sessionState.updateFromConnack(connack)
        topicAliasManagerInbound = TopicAliasManager(config.topicAliasMaximum)
        topicAliasManagerOutbound = TopicAliasManager(sessionState.serverTopicAliasMaximum)
    }

    private suspend fun handleEnhancedAuth(authPacket: AuthPacket) {
        val handler = onAuth ?: throw MqttProtocolException("Enhanced auth required but no onAuth handler set")
        var currentAuth: AuthPacket? = authPacket

        while (currentAuth != null && currentAuth.reasonCode == ReasonCode.CONTINUE_AUTHENTICATION) {
            val response = handler(currentAuth)
            if (response != null) {
                connection.sendPacket(response)
                val next = connection.readPacket()
                    ?: throw MqttConnectionException("Connection closed during authentication")
                when (next) {
                    is AuthPacket -> currentAuth = next
                    is ConnackPacket -> {
                        handleConnack(next)
                        return
                    }
                    else -> throw MqttProtocolException("Unexpected packet during auth: ${next.type}")
                }
            } else {
                break
            }
        }
    }

    // ─────────────────── Disconnect ───────────────────

    /**
     * Disconnect from the MQTT broker gracefully.
     *
     * @param reasonCode The reason for disconnecting.
     * @param sessionExpiryInterval Override the session expiry interval on disconnect.
     */
    suspend fun disconnect(
        reasonCode: ReasonCode = ReasonCode.NORMAL_DISCONNECTION,
        sessionExpiryInterval: Long? = null,
    ) {
        if (!isConnected) return

        isConnected = false
        keepAliveJob?.cancel()
        readJob?.cancel()

        try {
            val props = MqttProperties()
            sessionExpiryInterval?.let { props.sessionExpiryInterval = it }

            val disconnectPacket = DisconnectPacket(reasonCode, props)
            connection.sendPacket(disconnectPacket)
        } catch (_: Exception) {
            // Best effort
        } finally {
            connection.close()
            clientScope?.cancel()
        }
    }

    // ─────────────────── Publish ───────────────────

    /**
     * Publish a message to a topic.
     *
     * @param topic The topic to publish to.
     * @param payload The message payload.
     * @param qos The quality of service level.
     * @param retain Whether the message should be retained.
     * @param properties Optional MQTT v5 properties.
     */
    suspend fun publish(
        topic: String,
        payload: ByteArray,
        qos: QoS = QoS.AT_MOST_ONCE,
        retain: Boolean = false,
        properties: MqttProperties = MqttProperties(),
    ) {
        require(isConnected) { "Not connected" }

        // Apply topic alias if available
        val aliasResult = topicAliasManagerOutbound.getOutboundAlias(topic)
        val effectiveTopic: String
        if (aliasResult != null) {
            effectiveTopic = aliasResult.first
            properties.topicAlias = aliasResult.second
        } else {
            effectiveTopic = topic
        }

        val packetId = if (qos.value > 0) packetIdManager.allocate() else null

        val publishPacket = PublishPacket(
            dup = false,
            qos = qos,
            retain = retain,
            topicName = effectiveTopic,
            packetId = packetId,
            properties = properties,
            payload = payload,
        )

        when (qos) {
            QoS.AT_MOST_ONCE -> {
                connection.sendPacket(publishPacket)
            }

            QoS.AT_LEAST_ONCE -> {
                val pending = PendingPublish(publishPacket)
                sessionState.addPendingPuback(packetId!!, pending)
                connection.sendPacket(publishPacket)
                sessionState.sendQuota--
                try {
                    pending.deferred.await()
                } finally {
                    sessionState.sendQuota++
                    packetIdManager.release(packetId)
                }
            }

            QoS.EXACTLY_ONCE -> {
                val state = Qos2OutboundState(publishPacket)
                sessionState.addPendingQos2Outbound(packetId!!, state)
                connection.sendPacket(publishPacket)
                sessionState.sendQuota--
                try {
                    state.deferred.await()
                } finally {
                    sessionState.sendQuota++
                    packetIdManager.release(packetId)
                }
            }
        }
    }

    /**
     * Publish a string message to a topic.
     */
    suspend fun publish(
        topic: String,
        payload: String,
        qos: QoS = QoS.AT_MOST_ONCE,
        retain: Boolean = false,
        properties: MqttProperties = MqttProperties(),
    ) = publish(topic, payload.encodeToByteArray(), qos, retain, properties)

    // ─────────────────── Subscribe ───────────────────

    /**
     * Subscribe to one or more topics.
     *
     * @param subscriptions List of topic/options pairs.
     * @return List of reason codes from the SUBACK.
     */
    suspend fun subscribe(subscriptions: List<Subscription>): List<ReasonCode> {
        require(isConnected) { "Not connected" }
        require(subscriptions.isNotEmpty()) { "At least one subscription required" }

        val packetId = packetIdManager.allocate()
        val deferred = CompletableDeferred<SubackPacket>()
        sessionState.pendingSuback[packetId] = deferred

        val subscribePacket = SubscribePacket(
            packetId = packetId,
            subscriptions = subscriptions.map { it.topicFilter to it.options },
        )

        connection.sendPacket(subscribePacket)

        try {
            val suback = deferred.await()

            // Update local subscription tracking
            for ((i, sub) in subscriptions.withIndex()) {
                val rc = suback.reasonCodes.getOrNull(i)
                if (rc != null && !rc.isError) {
                    sessionState.subscriptions[sub.topicFilter] = sub.options.qos
                }
            }

            return suback.reasonCodes
        } finally {
            sessionState.pendingSuback.remove(packetId)
            packetIdManager.release(packetId)
        }
    }

    /**
     * Subscribe to a single topic with the given QoS.
     */
    suspend fun subscribe(topicFilter: String, qos: QoS = QoS.AT_MOST_ONCE): ReasonCode {
        val result = subscribe(listOf(Subscription(topicFilter, SubscriptionOptions(qos = qos))))
        return result.first()
    }

    // ─────────────────── Unsubscribe ───────────────────

    /**
     * Unsubscribe from one or more topics.
     *
     * @param topicFilters The topic filters to unsubscribe from.
     * @return List of reason codes from the UNSUBACK.
     */
    suspend fun unsubscribe(topicFilters: List<String>): List<ReasonCode> {
        require(isConnected) { "Not connected" }
        require(topicFilters.isNotEmpty()) { "At least one topic filter required" }

        val packetId = packetIdManager.allocate()
        val deferred = CompletableDeferred<UnsubackPacket>()
        sessionState.pendingUnsuback[packetId] = deferred

        val unsubscribePacket = UnsubscribePacket(
            packetId = packetId,
            topicFilters = topicFilters,
        )

        connection.sendPacket(unsubscribePacket)

        try {
            val unsuback = deferred.await()
            // Remove from local tracking
            for (filter in topicFilters) {
                sessionState.subscriptions.remove(filter)
            }
            return unsuback.reasonCodes
        } finally {
            sessionState.pendingUnsuback.remove(packetId)
            packetIdManager.release(packetId)
        }
    }

    /**
     * Unsubscribe from a single topic.
     */
    suspend fun unsubscribe(topicFilter: String): ReasonCode {
        return unsubscribe(listOf(topicFilter)).first()
    }

    // ─────────────────── Keep-Alive ───────────────────

    private fun startKeepAlive() {
        val keepAliveSeconds = sessionState.serverKeepAlive
            ?: config.keepAlive.inWholeSeconds.toInt()

        if (keepAliveSeconds <= 0) return

        keepAliveJob = clientScope?.launch {
            while (isActive) {
                delay(keepAliveSeconds * 1000L)
                if (isConnected) {
                    try {
                        connection.sendPacket(PingreqPacket)
                    } catch (_: Exception) {
                        handleConnectionLost(null)
                        break
                    }
                }
            }
        }
    }

    // ─────────────────── Read Loop ───────────────────

    private suspend fun readLoop() {
        try {
            while (isConnected) {
                val packet = connection.readPacket() ?: break
                handleIncomingPacket(packet)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isConnected) {
                handleConnectionLost(e)
            }
        }
    }

    private suspend fun handleIncomingPacket(packet: MqttPacket) {
        when (packet) {
            is PublishPacket -> handleIncomingPublish(packet)
            is PubackPacket -> handlePuback(packet)
            is PubrecPacket -> handlePubrec(packet)
            is PubrelPacket -> handlePubrel(packet)
            is PubcompPacket -> handlePubcomp(packet)
            is SubackPacket -> handleSuback(packet)
            is UnsubackPacket -> handleUnsuback(packet)
            PingrespPacket -> { /* Keep-alive response, no action needed */ }
            is DisconnectPacket -> handleServerDisconnect(packet)
            is AuthPacket -> handleAuthPacket(packet)
            else -> { /* Unexpected packet type, ignore */ }
        }
    }

    private suspend fun handleIncomingPublish(packet: PublishPacket) {
        // Resolve topic alias
        val topic = topicAliasManagerInbound.resolve(
            packet.topicName,
            packet.properties.topicAlias
        )

        val message = MqttMessage(
            topic = topic,
            payload = packet.payload,
            qos = packet.qos,
            retain = packet.retain,
            properties = packet.properties,
        )

        when (packet.qos) {
            QoS.AT_MOST_ONCE -> {
                deliverMessage(message)
            }

            QoS.AT_LEAST_ONCE -> {
                deliverMessage(message)
                connection.sendPacket(PubackPacket(packet.packetId!!))
            }

            QoS.EXACTLY_ONCE -> {
                val packetId = packet.packetId!!
                if (!sessionState.isPendingQos2Inbound(packetId)) {
                    // First time receiving this message
                    sessionState.addPendingQos2Inbound(packetId)
                    deliverMessage(message)
                }
                // Send PUBREC (even if duplicate)
                connection.sendPacket(PubrecPacket(packetId))
            }
        }
    }

    private fun deliverMessage(message: MqttMessage) {
        _messages.tryEmit(message)
        onMessage?.invoke(message)
    }

    private suspend fun handlePuback(packet: PubackPacket) {
        val pending = sessionState.completePuback(packet.packetId) ?: return
        if (packet.reasonCode.isError) {
            pending.deferred.completeExceptionally(MqttPublishException(packet.reasonCode))
        } else {
            pending.deferred.complete(Unit)
        }
    }

    private suspend fun handlePubrec(packet: PubrecPacket) {
        val state = sessionState.getPendingQos2Outbound(packet.packetId) ?: return
        if (packet.reasonCode.isError) {
            sessionState.removePendingQos2Outbound(packet.packetId)
            state.deferred.completeExceptionally(MqttPublishException(packet.reasonCode))
        } else {
            state.pubrecReceived = true
            connection.sendPacket(PubrelPacket(packet.packetId))
        }
    }

    private suspend fun handlePubrel(packet: PubrelPacket) {
        sessionState.removePendingQos2Inbound(packet.packetId)
        connection.sendPacket(PubcompPacket(packet.packetId))
    }

    private suspend fun handlePubcomp(packet: PubcompPacket) {
        val state = sessionState.getPendingQos2Outbound(packet.packetId) ?: return
        sessionState.removePendingQos2Outbound(packet.packetId)
        state.deferred.complete(Unit)
    }

    private fun handleSuback(packet: SubackPacket) {
        sessionState.pendingSuback[packet.packetId]?.complete(packet)
    }

    private fun handleUnsuback(packet: UnsubackPacket) {
        sessionState.pendingUnsuback[packet.packetId]?.complete(packet)
    }

    private suspend fun handleServerDisconnect(packet: DisconnectPacket) {
        isConnected = false
        connection.close()
        onDisconnect?.invoke(
            MqttException("Server disconnected: ${packet.reasonCode.name} - ${packet.properties.reasonString ?: ""}")
        )
    }

    private suspend fun handleAuthPacket(packet: AuthPacket) {
        val handler = onAuth ?: return
        val response = handler(packet)
        if (response != null) {
            connection.sendPacket(response)
        }
    }

    private fun handleConnectionLost(cause: Throwable?) {
        isConnected = false
        onDisconnect?.invoke(cause)

        // Complete all pending operations with failure
        val error = MqttConnectionException("Connection lost", cause)
        sessionState.pendingPuback.values.forEach { it.deferred.completeExceptionally(error) }
        sessionState.pendingQos2Outbound.values.forEach { it.deferred.completeExceptionally(error) }
        sessionState.pendingSuback.values.forEach { it.completeExceptionally(error) }
        sessionState.pendingUnsuback.values.forEach { it.completeExceptionally(error) }
    }

    // ─────────────────── Re-authentication ───────────────────

    /**
     * Initiate re-authentication per MQTT v5 Section 4.12.1.
     * Requires that an authentication method was set in the CONNECT packet.
     */
    suspend fun reauthenticate(authData: ByteArray? = null) {
        require(isConnected) { "Not connected" }
        val method = config.authenticationMethod
            ?: throw MqttException("Cannot re-authenticate without authentication method")

        val props = MqttProperties().apply {
            authenticationMethod = method
            authData?.let { authenticationData = it }
        }

        connection.sendPacket(AuthPacket(
            reasonCode = ReasonCode.RE_AUTHENTICATE,
            properties = props,
        ))
    }
}
