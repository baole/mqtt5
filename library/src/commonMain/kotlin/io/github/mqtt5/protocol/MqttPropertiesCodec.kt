package io.github.mqtt5.protocol

import io.github.mqtt5.MqttProperties
import io.github.mqtt5.MqttProtocolException

/**
 * MQTT v5.0 Property identifiers per Section 2.2.2.2, Table 2-4.
 */
internal object PropertyId {
    const val PAYLOAD_FORMAT_INDICATOR = 0x01           // Byte
    const val MESSAGE_EXPIRY_INTERVAL = 0x02            // Four Byte Integer
    const val CONTENT_TYPE = 0x03                        // UTF-8 Encoded String
    const val RESPONSE_TOPIC = 0x08                      // UTF-8 Encoded String
    const val CORRELATION_DATA = 0x09                    // Binary Data
    const val SUBSCRIPTION_IDENTIFIER = 0x0B            // Variable Byte Integer
    const val SESSION_EXPIRY_INTERVAL = 0x11            // Four Byte Integer
    const val ASSIGNED_CLIENT_IDENTIFIER = 0x12         // UTF-8 Encoded String
    const val SERVER_KEEP_ALIVE = 0x13                   // Two Byte Integer
    const val AUTHENTICATION_METHOD = 0x15              // UTF-8 Encoded String
    const val AUTHENTICATION_DATA = 0x16                // Binary Data
    const val REQUEST_PROBLEM_INFORMATION = 0x17        // Byte
    const val WILL_DELAY_INTERVAL = 0x18                // Four Byte Integer
    const val REQUEST_RESPONSE_INFORMATION = 0x19       // Byte
    const val RESPONSE_INFORMATION = 0x1A               // UTF-8 Encoded String
    const val SERVER_REFERENCE = 0x1C                    // UTF-8 Encoded String
    const val REASON_STRING = 0x1F                       // UTF-8 Encoded String
    const val RECEIVE_MAXIMUM = 0x21                     // Two Byte Integer
    const val TOPIC_ALIAS_MAXIMUM = 0x22                // Two Byte Integer
    const val TOPIC_ALIAS = 0x23                         // Two Byte Integer
    const val MAXIMUM_QOS = 0x24                         // Byte
    const val RETAIN_AVAILABLE = 0x25                    // Byte
    const val USER_PROPERTY = 0x26                       // UTF-8 String Pair
    const val MAXIMUM_PACKET_SIZE = 0x27                // Four Byte Integer
    const val WILDCARD_SUBSCRIPTION_AVAILABLE = 0x28    // Byte
    const val SUBSCRIPTION_IDENTIFIER_AVAILABLE = 0x29  // Byte
    const val SHARED_SUBSCRIPTION_AVAILABLE = 0x2A      // Byte
}

/**
 * Encode all properties into an MqttEncoder.
 * The caller is responsible for writing the Property Length prefix.
 */
internal fun MqttProperties.encodeTo(encoder: MqttEncoder) {
    payloadFormatIndicator?.let {
        encoder.writeVariableByteInteger(PropertyId.PAYLOAD_FORMAT_INDICATOR)
        encoder.writeByte(it)
    }
    messageExpiryInterval?.let {
        encoder.writeVariableByteInteger(PropertyId.MESSAGE_EXPIRY_INTERVAL)
        encoder.writeFourByteInteger(it.toLong())
    }
    contentType?.let {
        encoder.writeVariableByteInteger(PropertyId.CONTENT_TYPE)
        encoder.writeUtf8String(it)
    }
    responseTopic?.let {
        encoder.writeVariableByteInteger(PropertyId.RESPONSE_TOPIC)
        encoder.writeUtf8String(it)
    }
    correlationData?.let {
        encoder.writeVariableByteInteger(PropertyId.CORRELATION_DATA)
        encoder.writeBinaryData(it)
    }
    for (subId in subscriptionIdentifiers) {
        encoder.writeVariableByteInteger(PropertyId.SUBSCRIPTION_IDENTIFIER)
        encoder.writeVariableByteInteger(subId)
    }
    sessionExpiryInterval?.let {
        encoder.writeVariableByteInteger(PropertyId.SESSION_EXPIRY_INTERVAL)
        encoder.writeFourByteInteger(it)
    }
    assignedClientIdentifier?.let {
        encoder.writeVariableByteInteger(PropertyId.ASSIGNED_CLIENT_IDENTIFIER)
        encoder.writeUtf8String(it)
    }
    serverKeepAlive?.let {
        encoder.writeVariableByteInteger(PropertyId.SERVER_KEEP_ALIVE)
        encoder.writeTwoByteInteger(it)
    }
    authenticationMethod?.let {
        encoder.writeVariableByteInteger(PropertyId.AUTHENTICATION_METHOD)
        encoder.writeUtf8String(it)
    }
    authenticationData?.let {
        encoder.writeVariableByteInteger(PropertyId.AUTHENTICATION_DATA)
        encoder.writeBinaryData(it)
    }
    requestProblemInformation?.let {
        encoder.writeVariableByteInteger(PropertyId.REQUEST_PROBLEM_INFORMATION)
        encoder.writeByte(it)
    }
    willDelayInterval?.let {
        encoder.writeVariableByteInteger(PropertyId.WILL_DELAY_INTERVAL)
        encoder.writeFourByteInteger(it.toLong())
    }
    requestResponseInformation?.let {
        encoder.writeVariableByteInteger(PropertyId.REQUEST_RESPONSE_INFORMATION)
        encoder.writeByte(it)
    }
    responseInformation?.let {
        encoder.writeVariableByteInteger(PropertyId.RESPONSE_INFORMATION)
        encoder.writeUtf8String(it)
    }
    serverReference?.let {
        encoder.writeVariableByteInteger(PropertyId.SERVER_REFERENCE)
        encoder.writeUtf8String(it)
    }
    reasonString?.let {
        encoder.writeVariableByteInteger(PropertyId.REASON_STRING)
        encoder.writeUtf8String(it)
    }
    receiveMaximum?.let {
        encoder.writeVariableByteInteger(PropertyId.RECEIVE_MAXIMUM)
        encoder.writeTwoByteInteger(it)
    }
    topicAliasMaximum?.let {
        encoder.writeVariableByteInteger(PropertyId.TOPIC_ALIAS_MAXIMUM)
        encoder.writeTwoByteInteger(it)
    }
    topicAlias?.let {
        encoder.writeVariableByteInteger(PropertyId.TOPIC_ALIAS)
        encoder.writeTwoByteInteger(it)
    }
    maximumQos?.let {
        encoder.writeVariableByteInteger(PropertyId.MAXIMUM_QOS)
        encoder.writeByte(it)
    }
    retainAvailable?.let {
        encoder.writeVariableByteInteger(PropertyId.RETAIN_AVAILABLE)
        encoder.writeByte(it)
    }
    for ((name, value) in userProperties) {
        encoder.writeVariableByteInteger(PropertyId.USER_PROPERTY)
        encoder.writeStringPair(name, value)
    }
    maximumPacketSize?.let {
        encoder.writeVariableByteInteger(PropertyId.MAXIMUM_PACKET_SIZE)
        encoder.writeFourByteInteger(it)
    }
    wildcardSubscriptionAvailable?.let {
        encoder.writeVariableByteInteger(PropertyId.WILDCARD_SUBSCRIPTION_AVAILABLE)
        encoder.writeByte(it)
    }
    subscriptionIdentifierAvailable?.let {
        encoder.writeVariableByteInteger(PropertyId.SUBSCRIPTION_IDENTIFIER_AVAILABLE)
        encoder.writeByte(it)
    }
    sharedSubscriptionAvailable?.let {
        encoder.writeVariableByteInteger(PropertyId.SHARED_SUBSCRIPTION_AVAILABLE)
        encoder.writeByte(it)
    }
}

/**
 * Encode properties and return the full wire format (Property Length + Properties).
 */
internal fun MqttProperties.encode(): ByteArray {
    val propsEncoder = MqttEncoder()
    encodeTo(propsEncoder)
    val propsBytes = propsEncoder.toByteArray()

    val result = MqttEncoder()
    result.writeVariableByteInteger(propsBytes.size)
    result.writeBytes(propsBytes)
    return result.toByteArray()
}

/**
 * Decode properties from a decoder. Reads the Property Length first,
 * then parses all properties within that length.
 */
internal fun decodeMqttProperties(decoder: MqttDecoder): MqttProperties {
    val props = MqttProperties()
    val propertyLength = decoder.readVariableByteInteger()
    if (propertyLength == 0) return props

    val propsDecoder = decoder.readSlice(propertyLength)

    while (!propsDecoder.isExhausted) {
        val id = propsDecoder.readVariableByteInteger()
        when (id) {
            PropertyId.PAYLOAD_FORMAT_INDICATOR -> props.payloadFormatIndicator = propsDecoder.readByte()
            PropertyId.MESSAGE_EXPIRY_INTERVAL -> props.messageExpiryInterval = propsDecoder.readFourByteInteger().toInt()
            PropertyId.CONTENT_TYPE -> props.contentType = propsDecoder.readUtf8String()
            PropertyId.RESPONSE_TOPIC -> props.responseTopic = propsDecoder.readUtf8String()
            PropertyId.CORRELATION_DATA -> props.correlationData = propsDecoder.readBinaryData()
            PropertyId.SUBSCRIPTION_IDENTIFIER -> props.subscriptionIdentifiers.add(propsDecoder.readVariableByteInteger())
            PropertyId.SESSION_EXPIRY_INTERVAL -> props.sessionExpiryInterval = propsDecoder.readFourByteInteger()
            PropertyId.ASSIGNED_CLIENT_IDENTIFIER -> props.assignedClientIdentifier = propsDecoder.readUtf8String()
            PropertyId.SERVER_KEEP_ALIVE -> props.serverKeepAlive = propsDecoder.readTwoByteInteger()
            PropertyId.AUTHENTICATION_METHOD -> props.authenticationMethod = propsDecoder.readUtf8String()
            PropertyId.AUTHENTICATION_DATA -> props.authenticationData = propsDecoder.readBinaryData()
            PropertyId.REQUEST_PROBLEM_INFORMATION -> props.requestProblemInformation = propsDecoder.readByte()
            PropertyId.WILL_DELAY_INTERVAL -> props.willDelayInterval = propsDecoder.readFourByteInteger().toInt()
            PropertyId.REQUEST_RESPONSE_INFORMATION -> props.requestResponseInformation = propsDecoder.readByte()
            PropertyId.RESPONSE_INFORMATION -> props.responseInformation = propsDecoder.readUtf8String()
            PropertyId.SERVER_REFERENCE -> props.serverReference = propsDecoder.readUtf8String()
            PropertyId.REASON_STRING -> props.reasonString = propsDecoder.readUtf8String()
            PropertyId.RECEIVE_MAXIMUM -> props.receiveMaximum = propsDecoder.readTwoByteInteger()
            PropertyId.TOPIC_ALIAS_MAXIMUM -> props.topicAliasMaximum = propsDecoder.readTwoByteInteger()
            PropertyId.TOPIC_ALIAS -> props.topicAlias = propsDecoder.readTwoByteInteger()
            PropertyId.MAXIMUM_QOS -> props.maximumQos = propsDecoder.readByte()
            PropertyId.RETAIN_AVAILABLE -> props.retainAvailable = propsDecoder.readByte()
            PropertyId.USER_PROPERTY -> props.userProperties.add(propsDecoder.readStringPair())
            PropertyId.MAXIMUM_PACKET_SIZE -> props.maximumPacketSize = propsDecoder.readFourByteInteger()
            PropertyId.WILDCARD_SUBSCRIPTION_AVAILABLE -> props.wildcardSubscriptionAvailable = propsDecoder.readByte()
            PropertyId.SUBSCRIPTION_IDENTIFIER_AVAILABLE -> props.subscriptionIdentifierAvailable = propsDecoder.readByte()
            PropertyId.SHARED_SUBSCRIPTION_AVAILABLE -> props.sharedSubscriptionAvailable = propsDecoder.readByte()
            else -> throw MqttProtocolException("Unknown property identifier: 0x${id.toString(16)}")
        }
    }
    return props
}
