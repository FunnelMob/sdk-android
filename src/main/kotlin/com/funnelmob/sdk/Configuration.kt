package com.funnelmob.sdk

/**
 * Configuration options for the FunnelMob SDK
 */
data class FunnelMobConfiguration(
    /** API key for authentication */
    val apiKey: String,

    /** Log level for debugging */
    val logLevel: LogLevel = LogLevel.NONE,

    /** Interval between automatic event flushes (in milliseconds) */
    val flushIntervalMs: Long = 30_000L,

    /** Maximum number of events per batch */
    val maxBatchSize: Int = 100,

    /**
     * Optional override for the API base URL. When `null`, the SDK uses
     * the production endpoint (`https://api.funnelmob.com`). The SDK
     * appends `/v1/<endpoint>` itself, so pass the host root only
     * (e.g. `http://10.0.2.2:3080` for the Android emulator).
     *
     * The Builder trims trailing slashes from this value at construction
     * time; `NetworkClient.baseUrl` also trims at use-time so direct
     * data-class construction with a trailing slash still works.
     */
    val customUrl: String? = null,

    /**
     * Whether the SDK starts its active components (attribution session,
     * flush timer, lifecycle observer, automatic Install/ActivateApp events,
     * remote config fetch) immediately when [FunnelMob.initialize] is called.
     *
     * Defaults to `true`. Set to `false` when you need to defer the SDK's
     * network and event activity until you have obtained user consent
     * (e.g. GDPR). When `false`, you must call [FunnelMob.start] after
     * consent is granted.
     *
     * By calling [FunnelMob.start] you represent that you have obtained
     * any user consent required by applicable law.
     */
    val autoStart: Boolean = true
) {

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
        private var logLevel = LogLevel.NONE
        private var flushIntervalMs = 30_000L
        private var maxBatchSize = 100
        private var customUrl: String? = null
        private var autoStart = true

        fun logLevel(level: LogLevel) = apply { logLevel = level }

        fun flushInterval(ms: Long) = apply {
            flushIntervalMs = maxOf(1000L, ms)
        }

        fun maxBatchSize(size: Int) = apply {
            maxBatchSize = size.coerceIn(1, 100)
        }

        /**
         * Override the API base URL. Pass the host root without `/v1`
         * (e.g. `http://10.0.2.2:3080` from the Android emulator pointing
         * at a backend on the host machine).
         */
        fun customUrl(url: String?) = apply {
            customUrl = url?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        }

        /**
         * Configure whether the SDK starts automatically on
         * [FunnelMob.initialize]. Pass `false` to defer all network activity
         * and event tracking until [FunnelMob.start] is called.
         */
        fun autoStart(enabled: Boolean) = apply { autoStart = enabled }

        fun build() = FunnelMobConfiguration(
            apiKey = apiKey,
            logLevel = logLevel,
            flushIntervalMs = flushIntervalMs,
            maxBatchSize = maxBatchSize,
            customUrl = customUrl,
            autoStart = autoStart
        )
    }
}
