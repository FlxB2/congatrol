package xyz.felixb.congatrol.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Valetudo's REST API (`/api/v2`), which Congatudo serves unchanged, as plain JSON in and out.
 * What the JSON means is the repository's business; this only moves it.
 */
interface RobotApi {
    /** Reads [path] below `/api/v2` on the robot at [address]. Throws when it cannot be had. */
    suspend fun get(
        address: String,
        path: String,
    ): JsonElement

    /** Sends [body] to [path] below `/api/v2`. Throws when the robot does not take it. */
    suspend fun put(
        address: String,
        path: String,
        body: JsonObject,
    )

    /**
     * What the robot sends of its own accord at [path] below `/api/v2` — one of its event streams,
     * such as `robot/state/attributes/sse` — for as long as it is collected: the JSON of each
     * event. Fails when the stream cannot be had or breaks off.
     */
    fun events(
        address: String,
        path: String,
    ): Flow<JsonElement>
}

/** The real thing, over HTTP. */
class HttpRobotApi(
    private val client: HttpClient = defaultClient(),
) : RobotApi {
    override suspend fun get(
        address: String,
        path: String,
    ): JsonElement = Json.parseToJsonElement(client.get("$address/api/v2/$path").bodyAsText())

    override suspend fun put(
        address: String,
        path: String,
        body: JsonObject,
    ) {
        client.put("$address/api/v2/$path") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { requestTimeoutMillis = COMMAND_TIMEOUT_MILLIS }
        }
    }

    override fun events(
        address: String,
        path: String,
    ): Flow<JsonElement> =
        flow {
            client.sse(
                urlString = "$address/api/v2/$path",
                // A stream is open for as long as somebody listens, and silent while nothing changes.
                request = {
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    }
                },
            ) {
                incoming.collect { event -> event.data?.let { emit(Json.parseToJsonElement(it)) } }
            }
        }

    private companion object {
        /**
         * A robot only answers a command once it has carried it out, and some take their time:
         * stopping a run was measured at fifteen seconds on a Conga, which rewrites its cleaning
         * plan and re-reads its map before it says yes.
         */
        const val COMMAND_TIMEOUT_MILLIS = 40_000L

        /** A robot on the same network answers a question at once or not at all. */
        fun defaultClient() =
            HttpClient(CIO) {
                expectSuccess = true
                install(SSE)
                install(HttpTimeout) {
                    connectTimeoutMillis = 4_000
                    requestTimeoutMillis = 10_000
                }
            }
    }
}
