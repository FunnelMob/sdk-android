package com.funnelmob.sdk.internal

import com.funnelmob.sdk.FunnelMobConfiguration
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Base URL for the FunnelMob API. Hardcoded — there is only one production
 * endpoint and one API key model.
 */
private const val BASE_URL = "https://api.funnelmob.com/v1"

/**
 * HTTP client for sending events to the FunnelMob API
 */
internal class NetworkClient {

    /**
     * Send a session request and receive attribution result
     */
    fun sendSession(
        payload: JSONObject,
        configuration: FunnelMobConfiguration,
        callback: (Result<JSONObject?>) -> Unit
    ) {
        Thread {
            try {
                val result = sendSessionSync(payload, configuration)
                callback(result)
            } catch (e: Exception) {
                callback(Result.failure(NetworkError.NetworkException(e)))
            }
        }.start()
    }

    private fun sendSessionSync(
        payload: JSONObject,
        configuration: FunnelMobConfiguration
    ): Result<JSONObject?> {
        val url = URL("$BASE_URL/session")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-FM-API-Key", configuration.apiKey)
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            when (responseCode) {
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    Result.success(json)
                }
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

    /**
     * Send events to the API
     */
    fun sendEvents(
        events: List<Event>,
        deviceId: String,
        configuration: FunnelMobConfiguration,
        userId: String? = null,
        callback: (Result<Unit>) -> Unit
    ) {
        Thread {
            try {
                val result = sendEventsSync(events, deviceId, configuration, userId)
                callback(result)
            } catch (e: Exception) {
                callback(Result.failure(NetworkError.NetworkException(e)))
            }
        }.start()
    }

    private fun sendEventsSync(
        events: List<Event>,
        deviceId: String,
        configuration: FunnelMobConfiguration,
        userId: String? = null
    ): Result<Unit> {
        val url = URL("$BASE_URL/events")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-FM-API-Key", configuration.apiKey)
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.doOutput = true

            val payload = createPayload(events, deviceId, userId)

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

    /**
     * Send an identify request to link a user to a device
     */
    fun sendIdentify(
        payload: JSONObject,
        configuration: FunnelMobConfiguration,
        callback: (Result<IdentifyResponse>) -> Unit
    ) {
        Thread {
            try {
                val result = sendIdentifySync(payload, configuration)
                callback(result)
            } catch (e: Exception) {
                callback(Result.failure(NetworkError.NetworkException(e)))
            }
        }.start()
    }

    private fun sendIdentifySync(
        payload: JSONObject,
        configuration: FunnelMobConfiguration
    ): Result<IdentifyResponse> {
        val url = URL("$BASE_URL/identify")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-FM-API-Key", configuration.apiKey)
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            when (responseCode) {
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    Result.success(IdentifyResponse.fromJson(json))
                }
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

    /**
     * Fetch remote config from the API
     */
    fun fetchConfig(
        configuration: FunnelMobConfiguration,
        callback: (Result<JSONObject>) -> Unit
    ) {
        Thread {
            try {
                val result = fetchConfigSync(configuration)
                callback(result)
            } catch (e: Exception) {
                callback(Result.failure(NetworkError.NetworkException(e)))
            }
        }.start()
    }

    private fun fetchConfigSync(
        configuration: FunnelMobConfiguration
    ): Result<JSONObject> {
        val url = URL("$BASE_URL/config")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-FM-API-Key", configuration.apiKey)
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            val responseCode = connection.responseCode

            when (responseCode) {
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    Result.success(JSONObject(body))
                }
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
        userId: String? = null
    ): JSONObject {
        val eventsArray = JSONArray()
        events.forEach { event ->
            eventsArray.put(event.toJson())
        }

        return JSONObject().apply {
            put("platform", "android")
            put("device_id", deviceId)
            userId?.let { put("user_id", it) }
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
