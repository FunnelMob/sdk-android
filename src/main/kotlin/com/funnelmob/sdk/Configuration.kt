package com.funnelmob.sdk

/**
 * Configuration options for the FunnelMob SDK
 */
data class FunnelMobConfiguration(
    /** API key for authentication */
    val apiKey: String,

    /** Server (production or sandbox) */
    val server: Server = Server.PRODUCTION,

    /** Custom base URL, used when server == Server.CUSTOM */
    val customBaseUrl: String? = null,

    /** Log level for debugging */
    val logLevel: LogLevel = LogLevel.NONE,

    /** Interval between automatic event flushes (in milliseconds) */
    val flushIntervalMs: Long = 30_000L,

    /** Maximum number of events per batch */
    val maxBatchSize: Int = 100
) {

    /** Resolved base URL to use for API calls */
    val baseUrl: String get() = if (server == Server.CUSTOM) customBaseUrl ?: server.baseUrl else server.baseUrl

    /**
     * Server options
     */
    enum class Server(val baseUrl: String) {
        PRODUCTION("https://api.funnelmob.com/v1"),
        SANDBOX("https://sandbox.funnelmob.com/v1"),
        CUSTOM("")
    }

    /**
     * Log level options
     */
    enum class LogLevel(val priority: Int) {
        NONE(0),
        ERROR(1),
        WARNING(2),
        INFO(3),
        DEBUG(4),
        VERBOSE(5)
    }

    /**
     * Builder for FunnelMobConfiguration
     */
    class Builder(
        private val apiKey: String
    ) {
        private var server = Server.PRODUCTION
        private var customBaseUrl: String? = null
        private var logLevel = LogLevel.NONE
        private var flushIntervalMs = 30_000L
        private var maxBatchSize = 100

        fun server(server: Server) = apply { this.server = server }

        fun customUrl(url: String) = apply {
            this.server = Server.CUSTOM
            this.customBaseUrl = url
        }

        fun logLevel(level: LogLevel) = apply { logLevel = level }

        fun flushInterval(ms: Long) = apply {
            flushIntervalMs = maxOf(1000L, ms)
        }

        fun maxBatchSize(size: Int) = apply {
            maxBatchSize = size.coerceIn(1, 100)
        }

        fun build() = FunnelMobConfiguration(
            apiKey = apiKey,
            server = server,
            customBaseUrl = customBaseUrl,
            logLevel = logLevel,
            flushIntervalMs = flushIntervalMs,
            maxBatchSize = maxBatchSize
        )
    }
}
