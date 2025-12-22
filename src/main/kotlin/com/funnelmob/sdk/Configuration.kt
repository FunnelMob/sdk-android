package com.funnelmob.sdk

/**
 * Configuration options for the FunnelMob SDK
 */
data class FunnelMobConfiguration(
    /** Application identifier (e.g., package name) */
    val appId: String,

    /** API key for authentication */
    val apiKey: String,

    /** Environment (production or sandbox) */
    val environment: Environment = Environment.PRODUCTION,

    /** Log level for debugging */
    val logLevel: LogLevel = LogLevel.NONE,

    /** Interval between automatic event flushes (in milliseconds) */
    val flushIntervalMs: Long = 30_000L,

    /** Maximum number of events per batch */
    val maxBatchSize: Int = 100
) {

    /**
     * Environment options
     */
    enum class Environment(val baseUrl: String) {
        PRODUCTION("https://api.funnelmob.com/v1"),
        SANDBOX("https://sandbox.funnelmob.com/v1")
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
        private val appId: String,
        private val apiKey: String
    ) {
        private var environment = Environment.PRODUCTION
        private var logLevel = LogLevel.NONE
        private var flushIntervalMs = 30_000L
        private var maxBatchSize = 100

        fun environment(env: Environment) = apply { environment = env }

        fun logLevel(level: LogLevel) = apply { logLevel = level }

        fun flushInterval(ms: Long) = apply {
            flushIntervalMs = maxOf(1000L, ms)
        }

        fun maxBatchSize(size: Int) = apply {
            maxBatchSize = size.coerceIn(1, 100)
        }

        fun build() = FunnelMobConfiguration(
            appId = appId,
            apiKey = apiKey,
            environment = environment,
            logLevel = logLevel,
            flushIntervalMs = flushIntervalMs,
            maxBatchSize = maxBatchSize
        )
    }
}
