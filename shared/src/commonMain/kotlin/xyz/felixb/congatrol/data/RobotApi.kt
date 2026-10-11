package xyz.felixb.congatrol.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
        }
    }

    private companion object {
        /** A robot on the same network answers at once or not at all. */
        fun defaultClient() =
            HttpClient(CIO) {
                expectSuccess = true
                install(HttpTimeout) {
                    connectTimeoutMillis = 4_000
                    requestTimeoutMillis = 10_000
                }
            }
    }
}
