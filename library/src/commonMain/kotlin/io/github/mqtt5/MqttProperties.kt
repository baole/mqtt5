package io.github.mqtt5

/**
 * Container for MQTT v5.0 properties.
 *
 * Properties can appear in Variable Headers and Will Properties.
 * Use with [MqttClient.publish] or read from [MqttMessage.properties].
 *
 * ```kotlin
 * val props = MqttProperties().apply {
 *     contentType = "application/json"
 *     messageExpiryInterval = 3600
 *     userProperties.add("trace-id" to "abc-123")
 * }
 * client.publish("topic", payload, QoS.AT_LEAST_ONCE, properties = props)
 * ```
 */
class MqttProperties {
    // --- Byte properties ---
    var payloadFormatIndicator: Int? = null
    var requestProblemInformation: Int? = null
    var requestResponseInformation: Int? = null
    var maximumQos: Int? = null
    var retainAvailable: Int? = null
    var wildcardSubscriptionAvailable: Int? = null
    var subscriptionIdentifierAvailable: Int? = null
    var sharedSubscriptionAvailable: Int? = null

    // --- Two Byte Integer properties ---
    var serverKeepAlive: Int? = null
    var receiveMaximum: Int? = null
    var topicAliasMaximum: Int? = null
    var topicAlias: Int? = null

    // --- Four Byte Integer properties ---
    var messageExpiryInterval: Int? = null
    var sessionExpiryInterval: Long? = null
    var willDelayInterval: Int? = null
    var maximumPacketSize: Long? = null

    // --- Variable Byte Integer properties ---
    /** Subscription identifiers (can appear multiple times in PUBLISH). */
    var subscriptionIdentifiers: MutableList<Int> = mutableListOf()

    // --- UTF-8 Encoded String properties ---
    var contentType: String? = null
    var responseTopic: String? = null
    var assignedClientIdentifier: String? = null
    var authenticationMethod: String? = null
    var responseInformation: String? = null
    var serverReference: String? = null
    var reasonString: String? = null

    // --- Binary Data properties ---
    var correlationData: ByteArray? = null
    var authenticationData: ByteArray? = null

    // --- UTF-8 String Pair (can appear multiple times) ---
    var userProperties: MutableList<Pair<String, String>> = mutableListOf()

    val isEmpty: Boolean
        get() = payloadFormatIndicator == null && requestProblemInformation == null &&
                requestResponseInformation == null && maximumQos == null &&
                retainAvailable == null && wildcardSubscriptionAvailable == null &&
                subscriptionIdentifierAvailable == null && sharedSubscriptionAvailable == null &&
                serverKeepAlive == null && receiveMaximum == null &&
                topicAliasMaximum == null && topicAlias == null &&
                messageExpiryInterval == null && sessionExpiryInterval == null &&
                willDelayInterval == null && maximumPacketSize == null &&
                subscriptionIdentifiers.isEmpty() &&
                contentType == null && responseTopic == null &&
                assignedClientIdentifier == null && authenticationMethod == null &&
                responseInformation == null && serverReference == null &&
                reasonString == null && correlationData == null &&
                authenticationData == null && userProperties.isEmpty()

    /**
     * Create a shallow copy of this properties object.
     * Mutable collections (userProperties, subscriptionIdentifiers) are copied
     * so that modifications to the copy do not affect the original.
     */
    fun copy(): MqttProperties = MqttProperties().also { c ->
        c.payloadFormatIndicator = payloadFormatIndicator
        c.requestProblemInformation = requestProblemInformation
        c.requestResponseInformation = requestResponseInformation
        c.maximumQos = maximumQos
        c.retainAvailable = retainAvailable
        c.wildcardSubscriptionAvailable = wildcardSubscriptionAvailable
        c.subscriptionIdentifierAvailable = subscriptionIdentifierAvailable
        c.sharedSubscriptionAvailable = sharedSubscriptionAvailable
        c.serverKeepAlive = serverKeepAlive
        c.receiveMaximum = receiveMaximum
        c.topicAliasMaximum = topicAliasMaximum
        c.topicAlias = topicAlias
        c.messageExpiryInterval = messageExpiryInterval
        c.sessionExpiryInterval = sessionExpiryInterval
        c.willDelayInterval = willDelayInterval
        c.maximumPacketSize = maximumPacketSize
        c.subscriptionIdentifiers = subscriptionIdentifiers.toMutableList()
        c.contentType = contentType
        c.responseTopic = responseTopic
        c.assignedClientIdentifier = assignedClientIdentifier
        c.authenticationMethod = authenticationMethod
        c.responseInformation = responseInformation
        c.serverReference = serverReference
        c.reasonString = reasonString
        c.correlationData = correlationData?.copyOf()
        c.authenticationData = authenticationData?.copyOf()
        c.userProperties = userProperties.toMutableList()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MqttProperties) return false
        return payloadFormatIndicator == other.payloadFormatIndicator &&
                messageExpiryInterval == other.messageExpiryInterval &&
                sessionExpiryInterval == other.sessionExpiryInterval &&
                contentType == other.contentType &&
                responseTopic == other.responseTopic &&
                topicAlias == other.topicAlias &&
                reasonString == other.reasonString &&
                userProperties == other.userProperties
    }

    override fun hashCode(): Int {
        var result = payloadFormatIndicator ?: 0
        result = 31 * result + (messageExpiryInterval ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}
