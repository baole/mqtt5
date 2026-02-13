# KMQTT5 - Kotlin Multiplatform MQTT v5.0 Client Library

[![Build & Test](https://github.com/baole/kmqtt5/actions/workflows/build.yml/badge.svg)](https://github.com/baole/kmqtt5/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.baole/kmqtt5.svg)](https://central.sonatype.com/artifact/io.github.baole/kmqtt5)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.10-blue.svg)](https://kotlinlang.org)

A pure Kotlin Multiplatform implementation of the [MQTT v5.0 protocol](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html), using [Ktor](https://ktor.io/) for networking. No third-party MQTT libraries are used.

## Features

### Full MQTT v5.0 Protocol Support

- **All 15 packet types**: CONNECT, CONNACK, PUBLISH, PUBACK, PUBREC, PUBREL, PUBCOMP, SUBSCRIBE, SUBACK, UNSUBSCRIBE, UNSUBACK, PINGREQ, PINGRESP, DISCONNECT, AUTH
- **All QoS levels**: QoS 0 (At most once), QoS 1 (At least once), QoS 2 (Exactly once)
- **Full v5.0 Properties**: Session Expiry, Receive Maximum, Maximum Packet Size, Topic Alias, User Properties, Content Type, Response Topic, Correlation Data, Subscription Identifier, and more
- **Will Messages**: with Will Delay Interval and all Will Properties
- **Keep Alive**: automatic PINGREQ/PINGRESP handling
- **Session Management**: Clean Start, Session Expiry Interval, Session Present
- **Topic Aliases**: bidirectional topic alias support for reduced bandwidth
- **Flow Control**: Receive Maximum-based send quota management
- **Enhanced Authentication**: challenge/response authentication framework (AUTH packets)
- **Re-Authentication**: initiate re-authentication on an active connection
- **Reason Codes**: comprehensive reason code support on all acknowledgment packets
- **Server Capability Discovery**: Maximum QoS, Retain Available, Wildcard/Shared/Subscription-ID availability
- **Plain TCP & TLS/SSL**: supports both unencrypted (port 1883) and secure (port 8883) connections with custom TLS configuration (custom CA, mTLS)

### Reliability & Observability

- **Auto-Reconnect**: automatic reconnection with exponential backoff upon unexpected disconnection; re-subscribes to previously active topics on successful reconnect
- **QoS 1/2 Retry on Reconnect**: per MQTT v5 Section 4.4, unacknowledged QoS 1/2 messages are automatically resent with the DUP flag when the session is resumed after reconnect
- **Offline Message Queue**: publish while disconnected -- messages are buffered and sent automatically when the connection is restored (configurable capacity, drops oldest when full)
- **Connection State Flow**: reactive `StateFlow<ConnectionState>` (DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, RECONNECTING) for driving UI in Compose / SwiftUI
- **Connect Timeout**: configurable timeout for the initial TCP/TLS connection handshake
- **Logging**: optional callback-based logger with configurable log levels (DEBUG, INFO, WARN, ERROR) and lazy message evaluation for zero-cost when disabled

### Supported Platforms

| Platform | Targets |
|----------|---------|
| **JVM** | JVM 17+, Android |
| **iOS** | iosArm64, iosX64, iosSimulatorArm64 |
| **macOS** | macosArm64 (Apple Silicon), macosX64 (Intel) |
| **Linux** | linuxX64 |

### Library Design

- **Ktor Networking**: uses `ktor-network` for raw TCP sockets and `ktor-network-tls` for TLS/SSL with custom configuration support
- **Coroutine-based**: fully suspending API built on Kotlin coroutines
- **Reactive State**: `StateFlow<ConnectionState>` for UI binding + `SharedFlow` for messages
- **Spec-compliant Session Resumption**: QoS 1/2 message retry with DUP flag per MQTT v5 Section 4.4
- **Dual Message Delivery**: `SharedFlow`-based reactive API and callback-based listener
- **Zero third-party MQTT dependencies**: the entire protocol is implemented from scratch

## Installation

Add the following to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.baole:kmqtt5:1.0.0")
}
```

### Kotlin Multiplatform

For Kotlin Multiplatform projects, add the dependency in the `commonMain` source set:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.baole:kmqtt5:1.0.0")
        }
    }
}
```

### Version Catalog (libs.versions.toml)

```toml
[versions]
kmqtt5 = "1.0.0"

[libraries]
kmqtt5 = { module = "io.github.baole:kmqtt5", version.ref = "kmqtt5" }
```

Then in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.kmqtt5)
}
```

## Quick Start

### Basic Usage

```kotlin
import io.github.mqtt5.*
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

fun main() = runBlocking {
    // Create client
    val client = MqttClient {
        host = "broker.example.com"
        port = 1883
        clientId = "my-app"
        cleanStart = true
        keepAlive = 60.seconds
    }

    // Connect
    client.connect()

    // Subscribe
    client.subscribe("sensor/#", QoS.AT_LEAST_ONCE)

    // Receive messages via callback
    client.onMessage = { message ->
        println("${message.topic}: ${message.payloadAsString}")
    }

    // Or via Kotlin Flow
    // client.messages.collect { message -> ... }

    // Publish
    client.publish("sensor/temp", "22.5", QoS.AT_LEAST_ONCE)

    // Disconnect
    client.disconnect()
}
```

### Connection State (for UI)

```kotlin
// StateFlow — collect in Compose, SwiftUI, or any coroutine scope
client.connectionState.collect { state ->
    when (state) {
        ConnectionState.CONNECTED -> showOnlineIndicator()
        ConnectionState.CONNECTING -> showSpinner()
        ConnectionState.RECONNECTING -> showReconnectingBanner()
        ConnectionState.DISCONNECTING -> showDisconnecting()
        ConnectionState.DISCONNECTED -> showOfflineIndicator()
    }
}

// Or read the current value synchronously
if (client.connectionState.value == ConnectionState.CONNECTED) { /* ... */ }
```

### Auto-Reconnect with Offline Queue

```kotlin
val client = MqttClient {
    host = "broker.example.com"
    clientId = "reliable-client"

    // Enable auto-reconnect with exponential backoff
    autoReconnect = true
    reconnectDelay = 1.seconds         // initial delay
    maxReconnectDelay = 60.seconds     // cap for exponential backoff
    maxReconnectAttempts = 0           // 0 = unlimited

    // Messages published while disconnected are queued and sent on reconnect
    offlineQueueCapacity = 100         // 0 = unlimited
}

// Publish even while disconnected — it will be queued and sent later
client.publish("sensor/temp", "22.5", QoS.AT_LEAST_ONCE)
println("Queued messages: ${client.offlineQueueSize}")

// Optional: monitor reconnection events
client.onReconnecting = { attempt ->
    println("Reconnecting... attempt $attempt")
}
client.onReconnected = {
    println("Reconnected! Subscriptions and queued messages restored.")
}
```

### Connect Timeout

```kotlin
val client = MqttClient {
    host = "broker.example.com"
    connectTimeout = 10.seconds  // fail fast if broker is unreachable
}
```

### Logging

```kotlin
val client = MqttClient {
    host = "broker.example.com"
    logger = MqttLogger(minLevel = MqttLogLevel.DEBUG) { level, tag, message ->
        println("[$level] $tag: $message")
    }
}
```

### Authentication

```kotlin
val client = MqttClient {
    host = "broker.example.com"
    port = 8883
    useTls = true
    clientId = "secure-client"
    credentials("username", "password")
}
```

### Custom TLS Configuration

The `tls {}` helper enables TLS and gives access to Ktor's `TLSConfigBuilder` for
platform-specific settings such as custom trust managers and client certificates.

```kotlin
// Basic TLS (system trust store)
val client = MqttClient {
    host = "broker.example.com"
    port = 8883
    tls()  // enables TLS with default (system) trust store
}

// Custom CA certificate (JVM) — e.g., for AWS IoT Core, Azure IoT Hub
val client = MqttClient {
    host = "iot.example.com"
    port = 8883
    tls {
        trustManager = myCustomTrustManager  // JVM: javax.net.ssl.TrustManager
    }
}

// Mutual TLS (mTLS) with client certificates (JVM)
val client = MqttClient {
    host = "iot.example.com"
    port = 8883
    tls {
        trustManager = myCustomTrustManager
        // Add client certificate for mutual authentication
        addKeyStore(keyStore, keyPassword)
    }
}
```

### Will Message

```kotlin
val client = MqttClient {
    host = "broker.example.com"
    clientId = "monitored-client"
    will("status/monitored-client", "offline".encodeToByteArray(), QoS.AT_LEAST_ONCE) {
        retain = true
        delayInterval = 30  // seconds
        payloadFormatIndicator = 1  // UTF-8
        contentType = "text/plain"
    }
}
```

### Request/Response Pattern

```kotlin
import io.github.mqtt5.protocol.MqttProperties

// Requester
val requestProps = MqttProperties().apply {
    responseTopic = "response/my-client"
    correlationData = "req-001".encodeToByteArray()
}
client.publish("service/request", payload, QoS.AT_LEAST_ONCE, properties = requestProps)

// Responder (on receiving the request)
client.onMessage = { message ->
    val responseTopic = message.properties.responseTopic ?: return@onMessage
    val corrData = message.properties.correlationData

    val responseProps = MqttProperties().apply {
        correlationData = corrData
    }
    runBlocking {
        client.publish(responseTopic, "response-payload", QoS.AT_LEAST_ONCE, properties = responseProps)
    }
}
```

### Enhanced Authentication

```kotlin
val client = MqttClient {
    host = "broker.example.com"
    authenticationMethod = "SCRAM-SHA-256"
    authenticationData = initialClientData
}

client.onAuth = { authPacket ->
    // Process server challenge and return client response
    val serverData = authPacket.properties.authenticationData
    val responseData = processChallenge(serverData)

    AuthPacket(
        reasonCode = ReasonCode.CONTINUE_AUTHENTICATION,
        properties = MqttProperties().apply {
            authenticationMethod = "SCRAM-SHA-256"
            authenticationData = responseData
        }
    )
}
```

### Subscription Options

```kotlin
client.subscribe(listOf(
    Subscription("sensor/#", SubscriptionOptions(
        qos = QoS.EXACTLY_ONCE,
        noLocal = false,
        retainAsPublished = true,
        retainHandling = 1,  // Send retained only if subscription doesn't exist
    )),
    Subscription("cmd/+", SubscriptionOptions(
        qos = QoS.AT_LEAST_ONCE,
        noLocal = true,  // Don't receive own messages
    )),
))
```

### Publishing with Properties

```kotlin
val props = MqttProperties().apply {
    contentType = "application/json"
    messageExpiryInterval = 3600  // 1 hour
    payloadFormatIndicator = 1     // UTF-8
    userProperties.add("trace-id" to "abc-123")
    userProperties.add("version" to "1.0")
}

client.publish(
    topic = "events/order",
    payload = """{"orderId": "12345", "status": "created"}""",
    qos = QoS.EXACTLY_ONCE,
    properties = props,
)
```

## Building

```bash
# Build the library
./gradlew :library:build

# Run tests
./gradlew :library:allTests

# Run the sample
./gradlew :sample:run
```

## Requirements

- Kotlin 2.3.10+
- Ktor 3.4.0+
- JDK 17+ (for JVM target)

## Architecture

### Packet Encoding/Decoding

The library implements the full MQTT v5 wire protocol:

1. **`MqttCodec`**: Low-level primitives for Variable Byte Integers, UTF-8 Encoded Strings, Binary Data, and String Pairs
2. **`MqttProperties`**: Encodes/decodes all 28 MQTT v5 property types
3. **`MqttPacket`**: Sealed class hierarchy for all 15 packet types
4. **`PacketEncoder`**: Serializes packets to wire-format byte arrays
5. **`PacketDecoder`**: Deserializes wire-format bytes into packet objects

### Connection Layer

- **`MqttConnection`**: Manages TCP/TLS sockets via Ktor's `aSocket()` API
- Provides `sendPacket()` and `readPacket()` as suspend functions
- Thread-safe write operations via Mutex

### Client Layer

- **`MqttClient`**: The main public API
- Manages packet ID allocation, topic aliases, session state, and keep-alive
- Handles the QoS 1 and QoS 2 acknowledgment flows automatically
- Delivers messages via `SharedFlow` and/or callbacks

## Contributing

Contributions are welcome! Please open issues or submit pull requests.

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
