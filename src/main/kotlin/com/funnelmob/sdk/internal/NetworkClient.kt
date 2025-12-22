package com.funnelmob.sdk.internal

import com.funnelmob.sdk.FunnelMobConfiguration
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client for sending events to the FunnelMob API
 */
internal class NetworkClient {

    /**
     * Send events to the API
     */
    fun sendEvents(
        events: List<Event>,
        deviceId: String,
        configuration: FunnelMobConfiguration,
        callback: (Result<Unit>) -> Unit
    ) {
        Thread {
            try {
                val result = sendEventsSync(events, deviceId, configuration)
                callback(result)
            } catch (e: Exception) {
                callback(Result.failure(NetworkError.NetworkException(e)))
            }
        }.start()
    }

    private fun sendEventsSync(
        events: List<Event>,
        deviceId: String,
        configuration: FunnelMobConfiguration
    ): Result<Unit> {
        val url = URL("${configuration.environment.baseUrl}/events")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-FM-API-Key", configuration.apiKey)
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.doOutput = true

            val payload = createPayload(events, deviceId, configuration)

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            when (responseCode) {
                in 200..299 -> Result.success(Unit)
                401 -> Result.failure(NetworkError.Unauthorized)
                429 -> Result.failure(NetworkError.RateLimited)
                in 400..499 -> Result.failure(NetworkError.ClientError(responseCode))
                in 500..599 -> Result.failure(NetworkError.ServerError(responseCode))
                else -> Result.failure(NetworkError.UnknownError(responseCode))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun createPayload(
        events: List<Event>,
        deviceId: String,
        configuration: FunnelMobConfiguration
    ): JSONObject {
        val eventsArray = JSONArray()
        events.forEach { event ->
            eventsArray.put(event.toJson())
        }

        return JSONObject().apply {
            put("app_id", configuration.appId)
            put("device_id", deviceId)
            put("events", eventsArray)
        }
    }
}

/**
 * Network errors
 */
sealed class NetworkError : Exception() {
    data object Unauthorized : NetworkError()
    data object RateLimited : NetworkError()
    data class ClientError(val code: Int) : NetworkError()
    data class ServerError(val code: Int) : NetworkError()
    data class UnknownError(val code: Int) : NetworkError()
    data class NetworkException(override val cause: Throwable) : NetworkError()
}
