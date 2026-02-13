package io.github.mqtt5

import io.github.mqtt5.internal.*
import io.github.mqtt5.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class MqttClient(configure: MqttConfig.() -> Unit = {}) {
    val config = MqttConfig().apply(configure)

    private val connection = MqttConnection()
    private val packetIdManager = PacketIdManager()
    private val sessionState = SessionState()
    private var topicAliasManagerInbound = TopicAliasManager(0)
    private var topicAliasManagerOutbound = TopicAliasManager(0)

    private val _messages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 256)
    val messages: SharedFlow<MqttMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val offlineQueue = ArrayDeque<PendingOfflinePublish>()
    val offlineQueueSize: Int get() = offlineQueue.size

    private var clientScope: CoroutineScope? = null
    private var readJob: Job? = null
    private var keepAliveJob: Job? = null
    private val logger get() = config.logger

    var onMessage: ((MqttMessage) -> Unit)? = null
    var onDisconnect: ((Throwable?) -> Unit)? = null
    var onReconnecting: ((attempt: Int) -> Unit)? = null
    var onReconnected: (() -> Unit)? = null
    var onAuth: (suspend (AuthPacket) -> AuthPacket?)? = null

    @kotlin.concurrent.Volatile var isConnected: Boolean = false; private set
    @kotlin.concurrent.Volatile var isReconnecting: Boolean = false; private set
    @kotlin.concurrent.Volatile private var userDisconnected: Boolean = false

    val clientId: String get() = sessionState.assignedClientId ?: config.clientId

    suspend fun connect() {
        userDisconnected = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        clientScope = scope
        _connectionState.value = ConnectionState.CONNECTING
        logger?.info(TAG) { "Connecting to ${config.host}:${config.port} (tls=${config.useTls})" }
        try {
            if (config.connectTimeout.isPositive()) {
                try {
                    withTimeout(config.connectTimeout) { connection.connect(config.host, config.port, config.useTls) }
                } catch (e: TimeoutCancellationException) {
                    throw MqttConnectionException("Connection timed out after ${config.connectTimeout}", e)
                }
            } else {
                connection.connect(config.host, config.port, config.useTls)
            }
            val connectProps = MqttProperties().apply {
                if (config.sessionExpiryInterval > 0) sessionExpiryInterval = config.sessionExpiryInterval
                if (config.receiveMaximum != 65535) receiveMaximum = config.receiveMaximum
                if (config.maximumPacketSize > 0) maximumPacketSize = config.maximumPacketSize
                if (config.topicAliasMaximum > 0) topicAliasMaximum = config.topicAliasMaximum
                if (config.requestResponseInformation) requestResponseInformation = 1
                if (!config.requestProblemInformation) requestProblemInformation = 0
                if (config.userProperties.isNotEmpty()) userProperties = config.userProperties.toMutableList()
                config.authenticationMethod?.let { authenticationMethod = it }
                config.authenticationData?.let { authenticationData = it }
            }
            val connectPacket = ConnectPacket(
                cleanStart = config.cleanStart, keepAliveSeconds = config.keepAlive.inWholeSeconds.toInt(),
                properties = connectProps, clientId = config.clientId,
                willProperties = config.will?.buildProperties(), willTopic = config.will?.topic,
                willPayload = config.will?.payload, willQos = config.will?.qos ?: QoS.AT_MOST_ONCE,
                willRetain = config.will?.retain ?: false, username = config.username, password = config.password,
            )
            sessionState.clearForReconnect(config.cleanStart)
            connection.sendPacket(connectPacket)
            val response = connection.readPacket() ?: throw MqttConnectionException("Connection closed before CONNACK received")
            when (response) {
                is ConnackPacket -> handleConnack(response)
                is AuthPacket -> handleEnhancedAuth(response)
                else -> throw MqttProtocolException("Expected CONNACK or AUTH, got ${response.type}")
            }
            isConnected = true
            _connectionState.value = ConnectionState.CONNECTED
            logger?.info(TAG) { "Connected to ${config.host}:${config.port}" }
            readJob = scope.launch { readLoop() }
            startKeepAlive()
            flushOfflineQueue()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.DISCONNECTED
            throw e
        }
    }

    private suspend fun handleConnack(connack: ConnackPacket) {
        if (connack.reasonCode.isError) { connection.close(); throw MqttConnectException(connack.reasonCode) }
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
                val next = connection.readPacket() ?: throw MqttConnectionException("Connection closed during authentication")
                when (next) {
                    is AuthPacket -> currentAuth = next
                    is ConnackPacket -> { handleConnack(next); return }
                    else -> throw MqttProtocolException("Unexpected packet during auth: ${next.type}")
                }
            } else break
        }
    }

    suspend fun disconnect(reasonCode: ReasonCode = ReasonCode.NORMAL_DISCONNECTION, sessionExpiryInterval: Long? = null) {
        if (!isConnected) return
        userDisconnected = true; isConnected = false
        _connectionState.value = ConnectionState.DISCONNECTING
        keepAliveJob?.cancel(); readJob?.cancel()
        logger?.info(TAG) { "Disconnecting (reason=${reasonCode.name})" }
        try {
            val props = MqttProperties(); sessionExpiryInterval?.let { props.sessionExpiryInterval = it }
            connection.sendPacket(DisconnectPacket(reasonCode, props))
        } catch (_: Exception) { } finally {
            connection.close(); clientScope?.cancel()
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    suspend fun publish(topic: String, payload: ByteArray, qos: QoS = QoS.AT_MOST_ONCE, retain: Boolean = false, properties: MqttProperties = MqttProperties()) {
        if (!isConnected) {
            if (config.autoReconnect && !userDisconnected) { enqueueOffline(topic, payload, qos, retain, properties); return }
            throw IllegalStateException("Not connected")
        }
        sendPublish(topic, payload, qos, retain, properties)
    }

    suspend fun publish(topic: String, payload: String, qos: QoS = QoS.AT_MOST_ONCE, retain: Boolean = false, properties: MqttProperties = MqttProperties()) =
        publish(topic, payload.encodeToByteArray(), qos, retain, properties)

    private suspend fun sendPublish(topic: String, payload: ByteArray, qos: QoS, retain: Boolean, properties: MqttProperties) {
        logger?.debug(TAG) { "Publishing to '$topic' (qos=${qos.value}, retain=$retain, ${payload.size} bytes)" }
        val aliasResult = topicAliasManagerOutbound.getOutboundAlias(topic)
        val effectiveTopic = if (aliasResult != null) { properties.topicAlias = aliasResult.second; aliasResult.first } else topic
        val packetId = if (qos.value > 0) packetIdManager.allocate() else null
        val publishPacket = PublishPacket(dup = false, qos = qos, retain = retain, topicName = effectiveTopic, packetId = packetId, properties = properties, payload = payload)
        when (qos) {
            QoS.AT_MOST_ONCE -> connection.sendPacket(publishPacket)
            QoS.AT_LEAST_ONCE -> {
                val pending = PendingPublish(publishPacket); sessionState.addPendingPuback(packetId!!, pending)
                connection.sendPacket(publishPacket); sessionState.sendQuota--
                try { pending.deferred.await() } finally { sessionState.sendQuota++; packetIdManager.release(packetId) }
            }
            QoS.EXACTLY_ONCE -> {
                val state = Qos2OutboundState(publishPacket); sessionState.addPendingQos2Outbound(packetId!!, state)
                connection.sendPacket(publishPacket); sessionState.sendQuota--
                try { state.deferred.await() } finally { sessionState.sendQuota++; packetIdManager.release(packetId) }
            }
        }
    }

    private fun enqueueOffline(topic: String, payload: ByteArray, qos: QoS, retain: Boolean, properties: MqttProperties) {
        val cap = config.offlineQueueCapacity
        if (cap > 0 && offlineQueue.size >= cap) {
            val dropped = offlineQueue.removeFirst()
            logger?.warn(TAG) { "Offline queue full ($cap), dropped oldest message for '${dropped.topic}'" }
        }
        offlineQueue.addLast(PendingOfflinePublish(topic, payload, qos, retain, properties))
        logger?.debug(TAG) { "Queued offline message for '$topic' (queue size: ${offlineQueue.size})" }
    }

    private suspend fun flushOfflineQueue() {
        if (offlineQueue.isEmpty()) return
        logger?.info(TAG) { "Flushing ${offlineQueue.size} queued offline message(s)" }
        while (offlineQueue.isNotEmpty()) {
            val msg = offlineQueue.removeFirst()
            try { sendPublish(msg.topic, msg.payload, msg.qos, msg.retain, msg.properties) }
            catch (e: Exception) { logger?.warn(TAG) { "Failed to flush '${msg.topic}': ${e.message}" }; offlineQueue.addFirst(msg); break }
        }
    }

    suspend fun subscribe(subscriptions: List<Subscription>): List<ReasonCode> {
        require(isConnected) { "Not connected" }; require(subscriptions.isNotEmpty()) { "At least one subscription required" }
        val packetId = packetIdManager.allocate()
        val deferred = CompletableDeferred<SubackPacket>(); sessionState.pendingSuback[packetId] = deferred
        connection.sendPacket(SubscribePacket(packetId = packetId, subscriptions = subscriptions.map { it.topicFilter to it.options }))
        try {
            val suback = deferred.await()
            for ((i, sub) in subscriptions.withIndex()) { val rc = suback.reasonCodes.getOrNull(i); if (rc != null && !rc.isError) sessionState.subscriptions[sub.topicFilter] = sub.options.qos }
            return suback.reasonCodes
        } finally { sessionState.pendingSuback.remove(packetId); packetIdManager.release(packetId) }
    }

    suspend fun subscribe(topicFilter: String, qos: QoS = QoS.AT_MOST_ONCE): ReasonCode =
        subscribe(listOf(Subscription(topicFilter, SubscriptionOptions(qos = qos)))).first()

    suspend fun unsubscribe(topicFilters: List<String>): List<ReasonCode> {
        require(isConnected) { "Not connected" }; require(topicFilters.isNotEmpty()) { "At least one topic filter required" }
        val packetId = packetIdManager.allocate()
        val deferred = CompletableDeferred<UnsubackPacket>(); sessionState.pendingUnsuback[packetId] = deferred
        connection.sendPacket(UnsubscribePacket(packetId = packetId, topicFilters = topicFilters))
        try { val unsuback = deferred.await(); for (f in topicFilters) sessionState.subscriptions.remove(f); return unsuback.reasonCodes }
        finally { sessionState.pendingUnsuback.remove(packetId); packetIdManager.release(packetId) }
    }

    suspend fun unsubscribe(topicFilter: String): ReasonCode = unsubscribe(listOf(topicFilter)).first()

    private fun startKeepAlive() {
        val secs = sessionState.serverKeepAlive ?: config.keepAlive.inWholeSeconds.toInt()
        if (secs <= 0) return
        keepAliveJob = clientScope?.launch {
            while (isActive) { delay(secs * 1000L); if (isConnected) { try { connection.sendPacket(PingreqPacket) } catch (_: Exception) { handleConnectionLost(null); break } } }
        }
    }

    private suspend fun readLoop() {
        try { while (isConnected) { val packet = connection.readPacket() ?: break; handleIncomingPacket(packet) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (isConnected) handleConnectionLost(e) }
    }

    private suspend fun handleIncomingPacket(packet: MqttPacket) {
        when (packet) {
            is PublishPacket -> handleIncomingPublish(packet)
            is PubackPacket -> { val p = sessionState.completePuback(packet.packetId) ?: return; if (packet.reasonCode.isError) p.deferred.completeExceptionally(MqttPublishException(packet.reasonCode)) else p.deferred.complete(Unit) }
            is PubrecPacket -> { val s = sessionState.getPendingQos2Outbound(packet.packetId) ?: return; if (packet.reasonCode.isError) { sessionState.removePendingQos2Outbound(packet.packetId); s.deferred.completeExceptionally(MqttPublishException(packet.reasonCode)) } else { s.pubrecReceived = true; connection.sendPacket(PubrelPacket(packet.packetId)) } }
            is PubrelPacket -> { sessionState.removePendingQos2Inbound(packet.packetId); connection.sendPacket(PubcompPacket(packet.packetId)) }
            is PubcompPacket -> { val s = sessionState.getPendingQos2Outbound(packet.packetId) ?: return; sessionState.removePendingQos2Outbound(packet.packetId); s.deferred.complete(Unit) }
            is SubackPacket -> sessionState.pendingSuback[packet.packetId]?.complete(packet)
            is UnsubackPacket -> sessionState.pendingUnsuback[packet.packetId]?.complete(packet)
            PingrespPacket -> {}
            is DisconnectPacket -> handleServerDisconnect(packet)
            is AuthPacket -> { val h = onAuth ?: return; val r = h(packet); if (r != null) connection.sendPacket(r) }
            else -> {}
        }
    }

    private suspend fun handleIncomingPublish(packet: PublishPacket) {
        val topic = topicAliasManagerInbound.resolve(packet.topicName, packet.properties.topicAlias)
        val message = MqttMessage(topic = topic, payload = packet.payload, qos = packet.qos, retain = packet.retain, properties = packet.properties)
        when (packet.qos) {
            QoS.AT_MOST_ONCE -> deliverMessage(message)
            QoS.AT_LEAST_ONCE -> { deliverMessage(message); connection.sendPacket(PubackPacket(packet.packetId!!)) }
            QoS.EXACTLY_ONCE -> { val pid = packet.packetId!!; if (!sessionState.isPendingQos2Inbound(pid)) { sessionState.addPendingQos2Inbound(pid); deliverMessage(message) }; connection.sendPacket(PubrecPacket(pid)) }
        }
    }

    private fun deliverMessage(message: MqttMessage) { _messages.tryEmit(message); onMessage?.invoke(message) }

    private suspend fun handleServerDisconnect(packet: DisconnectPacket) {
        logger?.warn(TAG) { "Server sent DISCONNECT: ${packet.reasonCode.name}" }
        isConnected = false; _connectionState.value = ConnectionState.DISCONNECTED; connection.close()
        onDisconnect?.invoke(MqttException("Server disconnected: ${packet.reasonCode.name} - ${packet.properties.reasonString ?: ""}"))
    }

    private fun handleConnectionLost(cause: Throwable?) {
        if (!isConnected) return; isConnected = false
        logger?.warn(TAG) { "Connection lost${cause?.let { ": ${it.message}" } ?: ""}" }
        onDisconnect?.invoke(cause)
        val error = MqttConnectionException("Connection lost", cause)
        sessionState.pendingPuback.values.forEach { it.deferred.completeExceptionally(error) }
        sessionState.pendingQos2Outbound.values.forEach { it.deferred.completeExceptionally(error) }
        sessionState.pendingSuback.values.forEach { it.completeExceptionally(error) }
        sessionState.pendingUnsuback.values.forEach { it.completeExceptionally(error) }
        if (config.autoReconnect && !userDisconnected) { _connectionState.value = ConnectionState.RECONNECTING; clientScope?.launch { attemptReconnect() } }
        else _connectionState.value = ConnectionState.DISCONNECTED
    }

    private suspend fun attemptReconnect() {
        if (isReconnecting) return; isReconnecting = true
        val maxAttempts = config.maxReconnectAttempts; var attempt = 0; var currentDelay = config.reconnectDelay
        logger?.info(TAG) { "Auto-reconnect enabled, starting reconnection attempts" }
        try {
            while (true) {
                attempt++
                if (maxAttempts > 0 && attempt > maxAttempts) { logger?.error(TAG) { "Max reconnect attempts ($maxAttempts) reached" }; _connectionState.value = ConnectionState.DISCONNECTED; break }
                logger?.info(TAG) { "Reconnection attempt $attempt${if (maxAttempts > 0) "/$maxAttempts" else ""}" }
                onReconnecting?.invoke(attempt)
                try {
                    keepAliveJob?.cancel(); readJob?.cancel(); connection.close()
                    val saved = config.cleanStart; config.cleanStart = false
                    try { connect() } finally { config.cleanStart = saved }
                    val subs = sessionState.subscriptions.toMap()
                    if (subs.isNotEmpty()) { try { subscribe(subs.map { (f, q) -> Subscription(f, SubscriptionOptions(qos = q)) }) } catch (e: Exception) { logger?.warn(TAG) { "Re-subscribe failed: ${e.message}" } } }
                    logger?.info(TAG) { "Reconnected successfully" }; onReconnected?.invoke(); return
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { logger?.warn(TAG) { "Reconnection attempt $attempt failed: ${e.message}" }; _connectionState.value = ConnectionState.RECONNECTING }
                delay(currentDelay); currentDelay = (currentDelay * 2).coerceAtMost(config.maxReconnectDelay)
            }
        } finally { isReconnecting = false }
    }

    suspend fun reauthenticate(authData: ByteArray? = null) {
        require(isConnected) { "Not connected" }
        val method = config.authenticationMethod ?: throw MqttException("Cannot re-authenticate without authentication method")
        connection.sendPacket(AuthPacket(reasonCode = ReasonCode.RE_AUTHENTICATE, properties = MqttProperties().apply { authenticationMethod = method; authData?.let { authenticationData = it } }))
    }

    companion object { private const val TAG = "MqttClient" }
}

internal data class PendingOfflinePublish(val topic: String, val payload: ByteArray, val qos: QoS, val retain: Boolean, val properties: MqttProperties)
