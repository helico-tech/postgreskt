# Building a PostgreSQL client from scratch in pure Kotlin (Multiplatform)

This repository documents my journey of implementing a PostgreSQL client from scratch in pure Kotlin by speaking the wire protocol directly — no JDBC, no libpq, no ORM. Just sockets, bytes, and the protocol.

It’s a Kotlin Multiplatform project targeting JVM, JS, Wasm, and Native. Networking is handled via Ktor’s low-level sockets on supported platforms, and the client exposes a small, composable API for:

- Connecting and authenticating (MD5)
- Running simple queries
- Preparing and executing statements (extended query protocol)
- LISTEN/NOTIFY notifications

This post walks you through the project setup, the protocol implementation step-by-step, and shows how to use it with real code from this repo.


## Project setup (Kotlin Multiplatform)

The project uses Kotlin MPP with Ktor networking and coroutines. The essential parts of the Gradle setup:

```kotlin
// build.gradle.kts
@file:OptIn(ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform") version "2.2.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvm {}
    macosArm64 { binaries.executable() }
    js { nodejs(); binaries.executable() }
    wasmJs { nodejs(); binaries.executable() }

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation(ktorLibs.network)
            }
        }
    }
}
```

For local development, spin up Postgres with MD5 auth enabled using docker-compose:

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: postgres
      POSTGRES_USER: postgres
      POSTGRES_DB: postgres
      POSTGRES_HOST_AUTH_METHOD: md5
      POSTGRES_INITDB_ARGS: --auth-host=md5
    ports:
      - '5432:5432'
```


## Public API at a glance

The client exposes a small asynchronous API:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/Client.kt
interface Client {
    val isConnected: Boolean

    suspend fun connect()
    suspend fun disconnect()

    suspend fun query(queryString: String): QueryResult

    suspend fun prepare(queryString: String): PreparedStatement
    suspend fun execute(preparedStatement: PreparedStatement, values: List<String?> = emptyList()): QueryResult

    suspend fun listen(channel: String): Flow<NotificationResponse>
    suspend fun notify(channel: String, payload: String)

    companion object {
        operator fun invoke(connectionParameters: ConnectionParameters): Client = DefaultClient(connectionParameters)
    }
}
```

A minimal example program (used across platforms in this repo):

```kotlin
// src/commonMain/kotlin/App.kt
suspendingScope {
    val client = DefaultClient(
        connectionParameters = ConnectionParameters(
            host = "localhost", port = 5432,
            username = "postgres", password = "postgres", database = "postgres"
        )
    )

    client.connect()

    val query = """
        SELECT
            id,
            FLOOR(RANDOM() * 100) AS random_number,
            MD5(RANDOM()::TEXT) AS random_string
        FROM
            generate_series(1, 10) AS id;
    """.trimIndent()

    val (metadata, data) = client.query(query)
    data.collect { row ->
        metadata.fields.zip(row.fields).forEach { (field, value) ->
            println("${'$'}{field.field}: ${'$'}{value?.readString()}")
        }
        println()
    }

    client.disconnect()
}
```

Platform bootstrap is done with small expect/actual wrappers, for example on JVM:

```kotlin
// src/jvmMain/kotlin/App.jvm.kt
actual fun suspendingScope(block: suspend () -> Unit) {
    runBlocking { block() }
}
```

On JS/Wasm:

```kotlin
// src/jsMain/kotlin/App.js.kt (same for wasmJs)
actual fun suspendingScope(block: suspend () -> Unit) {
    GlobalScope.promise { block() }
}
```


## Architecture overview

- Sockets and I/O: Ktor’s low-level TCP sockets and ByteRead/ByteWrite channels
- Concurrency: Kotlin coroutines, Flows, and Channels
- State machine: a tiny internal state model that drives the protocol transitions
- Messages: strongly-typed Kotlin data classes for each wire message, with pluggable serializers and deserializers
- Registry: a MessageRegistry that routes bytes to messages and messages to bytes

The DefaultClient holds the pieces together:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/DefaultClient.kt
class DefaultClient(
    val connectionParameters: ConnectionParameters,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + CoroutineName("PostgresClient")),
    private val messageRegistry: MessageRegistry = DefaultMessageRegistry,
) : Client {
    private val selectorManager = SelectorManager(scope.coroutineContext + Dispatchers.Default + CoroutineName("PostgresSelectorManager"))
    private var currentSocket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null

    private val currentState: MutableStateFlow<State> = MutableStateFlow(State.Disconnected)
    private val backendMessages: MutableSharedFlow<BackendMessage> = MutableSharedFlow()

    override suspend fun connect() {
        currentSocket = aSocket(selectorManager).tcp().connect(connectionParameters.host, connectionParameters.port)
        readChannel = currentSocket?.openReadChannel()
        writeChannel = currentSocket?.openWriteChannel(autoFlush = true)

        scope.launch { handle() }
        scope.launch { receive() }

        transition(State.Connecting)
        send(StartupMessage(parameters = mapOf(
            "user" to connectionParameters.username,
            "database" to connectionParameters.database,
        )))

        waitForState<State.ReadyForQuery>()
    }

    // ... query, prepare, execute, listen/notify, and helpers
}
```

There are two long-lived coroutines:

- receive(): reads and decodes messages from the socket, feeding them into a SharedFlow
- handle(): consumes backend messages and advances the state machine (e.g., reply to authentication requests, forward DataRow into channels, etc.)


## Speaking the PostgreSQL wire protocol

The core idea is to map each backend/ frontend message to a Kotlin data class and teach the system how to serialize/deserialize them.

### The message registry

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/messages/MessageRegistry.kt
typealias Serializers = Map<KClass<out FrontendMessage>, Serializer<FrontendMessage>>
typealias Deserializers = Map<Char, Deserializer<out BackendMessage>>

class MessageRegistry(
    val serializers: Serializers,
    val deserializers: Deserializers,
) {
    fun deserialize(type: Char, buffer: Buffer): BackendMessage =
        deserializers[type]?.deserialize(type, buffer) ?: Deserializer.Unhandled.deserialize(type, buffer)

    @Suppress("UNCHECKED_CAST")
    fun <T : FrontendMessage> serialize(message: T): Buffer =
        (serializers[message::class] as? Serializer<T>)?.serialize(message)
            ?: throw NoSuchElementException("No serializer for message type '${'$'}{message::class}'.")

    companion object {
        operator fun invoke(body: Builder.() -> Unit): MessageRegistry = Builder().apply(body).build()
    }
}

val DefaultMessageRegistry = MessageRegistry {
    commonResponses()
    parameterStatus()
    backendKeyData()
    startupMessage()
    authentication()
    query()
    terminate()
}
```

The builder helpers allow succinct registration of message handlers per the official protocol type codes (e.g., 'R' for Authentication, 'Z' for ReadyForQuery, etc.).


### Bytes and framing helpers

Most Postgres messages are length-prefixed with an optional type byte. A few helpers keep the code tidy:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/messages/Bytes.kt
fun Sink.writeCString(text: String) { writeString(text); writeNullByte() }
fun Source.readCString(): String { /* read until null byte */ }

fun Buffer.writeSized(type: Char? = null, body: Buffer.() -> Unit) {
    val packet = Buffer().apply(body)
    val size = packet.size.toInt() + Int.SIZE_BYTES
    if (type != null) writeByte(type.code.toByte())
    writeInt(size)
    writePacket(packet)
}
```


### 1) Startup and authentication

Connecting starts with a StartupMessage (no type byte, only a length). The server responds with Authentication… messages. This client implements MD5 authentication:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/messages/Startup.kt
data class StartupMessage(
    val protocolMajorVersion: Int = 3,
    val protocolMinorVersion: Int = 0,
    val parameters: Map<String, String>,
) : FrontendMessage

fun MessageRegistry.Builder.startupMessage() {
    frontend<StartupMessage> { message ->
        writeSized {
            writeInt(message.protocolMajorVersion shl Short.SIZE_BITS or message.protocolMinorVersion)
            message.parameters.forEach { (key, value) ->
                writeCString(key)
                writeCString(value)
            }
            writeNullByte()
        }
    }
}
```

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/messages/Authentication.kt
data object AuthenticationOk : BackendMessage

data class AuthenticationMD5(val salt: ByteArray) : BackendMessage

data class PasswordMessage(val password: String) : FrontendMessage {
    companion object {
        suspend fun md5(username: String, password: String, salt: ByteArray): PasswordMessage {
            val firstHash = Digest("MD5").let { d -> d += password.toByteArray(); d += username.toByteArray(); d.build().toHexString() }
            val secondHash = Digest("MD5").let { d -> d += firstHash.toByteArray(); d += salt; d.build().toHexString() }
            return PasswordMessage("md5$secondHash")
        }
    }
}

fun MessageRegistry.Builder.authentication() {
    frontend<PasswordMessage> { message ->
        writeSized('p') { writeCString(message.password) }
    }
    backend('R') { type ->
        when (readInt()) {
            0 -> AuthenticationOk
            5 -> AuthenticationMD5(readByteArray())
            else -> BackendMessage.Unhandled(type, this)
        }
    }
}
```

The DefaultClient’s handler reacts to the server’s AuthenticationMD5 by computing the correct password hash and sending PasswordMessage. Once ReadyForQuery arrives, the handshake is done.


### 2) Simple Query protocol (Query/RowDescription/DataRow/CommandComplete)

Sending a text query is as simple as:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/messages/Query.kt
data class Query(val query: String) : FrontendMessage

fun MessageRegistry.Builder.query() {
    backend('Z') { ReadyForQuery(readByte().toInt().toChar()) }

    backend('T') { /* RowDescription: list of fields with metadata */ }
    backend('D') { /* DataRow: list of optional buffers per column */ }
    backend('C') { CommandComplete(tag = readCString()) }

    frontend<Query> { message ->
        writeSized('Q') { writeCString(message.query) }
    }
}
```

The client’s query method transitions into a Collecting state, sends Query, waits for RowDescription, and streams DataRow values through a channel until ReadyForQuery or CommandComplete closes the stream:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/DefaultClient.kt (excerpt)
override suspend fun query(queryString: String): QueryResult {
    waitForState<State.ReadyForQuery>()
    val resultChannel = Channel<DataRow>()
    transition(State.Collecting(resultChannel))
    send(Query(queryString))
    val rowDescription = waitForMessage<RowDescription>()
    return QueryResult(rowDescription, resultChannel.consumeAsFlow())
}
```

The handler wiring:

```kotlin
private suspend fun handle() {
    backendMessages.collect { message ->
        if (message is ErrorResponse) throw IllegalStateException("An error response was received: ${'$'}message")
        when (val state = currentState.value) {
            State.Connecting -> when (message) {
                is AuthenticationMD5 -> send(PasswordMessage.md5(connectionParameters.username, connectionParameters.password, message.salt))
                is ReadyForQuery -> transition(State.ReadyForQuery)
                else -> unhandled(message)
            }
            is State.Collecting -> when (message) {
                is ReadyForQuery, is CommandComplete -> { state.resultChannel.close(); transition(State.ReadyForQuery) }
                is DataRow -> state.resultChannel.send(message)
                else -> unhandled(message)
            }
            // ...
        }
    }
}
```


### 3) Extended Query protocol (Parse/Bind/Execute/Sync)

Prepared statements are done via Parse + Describe + Sync to fetch metadata, and execution is Bind + Execute + Close + Sync.

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/messages/Query.kt
data class Parse(val name: String, val query: String) : FrontendMessage
data class Describe(val type: Char, val name: String) : FrontendMessage

data class ParameterDescription(val parameterOid: List<Int>) : BackendMessage

data class Bind(val name: String, val preparedStatement: String, val values: List<String?>) : FrontendMessage

data class Execute(val name: String) : FrontendMessage

data class Close(val type: Char, val name: String) : FrontendMessage

data object ParseComplete : BackendMessage

data object BindComplete : BackendMessage

data object CloseComplete : BackendMessage
```

Serialization for these messages adheres to the protocol’s framing and text formats by default:

```kotlin
// within MessageRegistry.Builder.query()
frontend<Parse> { message ->
    writeSized('P') {
        writeCString(message.name)
        writeCString(message.query)
        writeShort(0) // no param type OIDs specified
    }
}

frontend<Bind> { message ->
    writeSized('B') {
        writeCString(message.name)
        writeCString(message.preparedStatement)
        writeShort(0) // parameter format codes: 0 = text for all
        writeShort(message.values.size.toShort())
        message.values.forEach { value ->
            val bytes = value?.toByteArray()
            if (bytes == null) writeInt(-1) else { writeInt(bytes.size); writeFully(bytes) }
        }
        writeShort(0) // result format codes
    }
}

frontend<Execute> { message ->
    writeSized('E') { writeCString(message.name); writeInt(0) }
}

frontend<Close> { message ->
    writeSized('C') { writeByte(message.type.code.toByte()); writeCString(message.name) }
}

frontend<Sync> {
    writeByte('S'.code.toByte())
    writeInt(4)
}
```

The client orchestration looks like:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/DefaultClient.kt (excerpt)
@OptIn(ExperimentalUuidApi::class)
override suspend fun prepare(queryString: String): PreparedStatement {
    waitForState<State.ReadyForQuery>()
    val identifier = Uuid.random().toString()
    send(Parse(name = identifier, queryString))
    send(Describe('S', identifier))
    send(Sync)
    val parameterDescription = waitForMessage<ParameterDescription>()
    val rowDescription = waitForMessage<RowDescription>()
    return PreparedStatement(identifier, queryString, parameterDescription, rowDescription)
}

@OptIn(ExperimentalUuidApi::class)
override suspend fun execute(preparedStatement: PreparedStatement, values: List<String?>): QueryResult {
    waitForState<State.ReadyForQuery>()
    val resultChannel = Channel<DataRow>()
    val portalId = Uuid.random().toString()
    transition(State.Collecting(resultChannel))
    send(Bind(portalId, preparedStatement.identifier, values))
    send(Execute(portalId))
    send(Close('P', portalId))
    send(Sync)
    return QueryResult(preparedStatement.rowDescription, resultChannel.consumeAsFlow())
}
```


### 4) Notifications (LISTEN/NOTIFY)

PostgreSQL supports async notifications. The client models NotificationResponse and exposes listen/notify:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/messages/Common.kt
data class NotificationResponse(val backendPID: Int, val channel: String, val payload: String) : BackendMessage

fun MessageRegistry.Builder.commonResponses() {
    backend('A') { NotificationResponse(readInt(), readCString(), readCString()) }
}
```

Client side:

```kotlin
// src/commonMain/kotlin/nl/helico/postgreskt/DefaultClient.kt (excerpt)
override suspend fun listen(channel: String): Flow<NotificationResponse> {
    waitForState<State.ReadyForQuery>()
    val resultChannel = Channel<NotificationResponse>()
    transition(State.Listening(resultChannel))
    send(Query("LISTEN ${'$'}channel"))
    return resultChannel.consumeAsFlow().onCompletion {
        send(Query("UNLISTEN ${'$'}channel"))
        transition(State.ReadyForQuery)
        resultChannel.close()
    }
}

override suspend fun notify(channel: String, payload: String) {
    send(Query("NOTIFY ${'$'}channel, '${'$'}payload'"))
    waitForMessage<CommandComplete>()
}
```


## Receiving and sending bytes

Two small loops wire everything:

```kotlin
// Receive loop: read type, length, body and decode
private suspend fun receive() {
    while (readChannel?.isClosedForRead != true) {
        readChannel?.also {
            val type = it.readByte().toInt().toChar()
            val length = it.readInt()
            val remaining = length - Int.SIZE_BYTES
            val buffer = it.readBuffer(remaining)
            val msg = messageRegistry.deserialize(type, buffer)
            backendMessages.emit(msg)
        }
    }
}

// Send helper: serialize and write packet
private suspend fun send(message: FrontendMessage) {
    writeChannel?.also {
        it.writePacket(messageRegistry.serialize(message))
    }
}
```


## Limitations and next steps

This is an educational/client prototype. Some obvious next steps:

- SSL negotiation and SCRAM-SHA-256 auth
- Binary formats and type decoding beyond text values
- Pipelining and more nuanced state handling
- Extended error handling and retries
- Connection pool and multiplexing


## Try it locally

1) Start Postgres

```bash
docker compose up -d postgres
```

2) Run the JVM sample (from your IDE or Gradle task)

```bash
./gradlew :jvmRun # or run the platform executable configuration
```

3) Or run JS/Wasm Node or Native targets (created as executables in this project’s Gradle config).
