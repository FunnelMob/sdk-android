package com.funnelmob.sdk

import android.content.Context
import com.funnelmob.sdk.internal.DeviceInfo
import com.funnelmob.sdk.internal.Event
import com.funnelmob.sdk.internal.EventQueue
import com.funnelmob.sdk.internal.Logger
import com.funnelmob.sdk.internal.NetworkClient
import com.funnelmob.sdk.internal.toEventRevenue
import java.util.UUID

/**
 * Main entry point for the FunnelMob SDK
 */
object FunnelMob {

    @Volatile
    private var isInitialized = false

    private var configuration: FunnelMobConfiguration? = null
    private var isEnabled = true

    private lateinit var eventQueue: EventQueue
    private lateinit var networkClient: NetworkClient
    private lateinit var deviceInfo: DeviceInfo

    /**
     * Initialize the SDK with context and configuration
     *
     * @param context Application context
     * @param configuration SDK configuration
     */
    @JvmStatic
    fun initialize(context: Context, configuration: FunnelMobConfiguration) {
        if (isInitialized) {
            Logger.warning("FunnelMob already initialized")
            return
        }

        val appContext = context.applicationContext
        this.configuration = configuration
        this.eventQueue = EventQueue(appContext)
        this.networkClient = NetworkClient()
        this.deviceInfo = DeviceInfo(appContext)

        Logger.logLevel = configuration.logLevel
        Logger.info("FunnelMob initialized for app: ${configuration.appId}")

        isInitialized = true
        startSession()
    }

    /**
     * Track a custom event
     *
     * @param name Event name
     */
    @JvmStatic
    fun trackEvent(name: String) {
        trackEvent(name, revenue = null, parameters = null)
    }

    /**
     * Track an event with parameters
     *
     * @param name Event name
     * @param parameters Custom parameters
     */
    @JvmStatic
    fun trackEvent(name: String, parameters: FunnelMobEventParameters) {
        trackEvent(name, revenue = null, parameters = parameters)
    }

    /**
     * Track a revenue event
     *
     * @param name Event name
     * @param revenue Revenue information
     */
    @JvmStatic
    fun trackEvent(name: String, revenue: FunnelMobRevenue) {
        trackEvent(name, revenue = revenue, parameters = null)
    }

    /**
     * Track a revenue event with parameters
     *
     * @param name Event name
     * @param revenue Revenue information (optional)
     * @param parameters Custom parameters (optional)
     */
    @JvmStatic
    fun trackEvent(
        name: String,
        revenue: FunnelMobRevenue? = null,
        parameters: FunnelMobEventParameters? = null
    ) {
        check(isInitialized) { "FunnelMob SDK not initialized. Call initialize() first." }

        if (!isEnabled) {
            Logger.debug("Tracking disabled, ignoring event: $name")
            return
        }

        // Validate event name
        val validatedName = validateEventName(name)
        if (validatedName == null) {
            Logger.error("Invalid event name: $name")
            return
        }

        val event = Event(
            eventId = UUID.randomUUID().toString(),
            eventName = validatedName,
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date()),
            revenue = revenue?.toEventRevenue(),
            parameters = parameters?.toMap()
        )

        eventQueue.enqueue(event)
        Logger.debug("Event queued: $name")
    }

    /**
     * Force send queued events immediately
     */
    @JvmStatic
    fun flush() {
        if (!isInitialized) return
        val config = configuration ?: return
        eventQueue.flush(networkClient, config)
    }

    /**
     * Enable or disable tracking
     *
     * @param enabled Whether tracking is enabled
     */
    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Logger.info("Tracking ${if (enabled) "enabled" else "disabled"}")
    }

    // MARK: - Private

    private fun startSession() {
        // TODO: Implement session tracking
        Logger.debug("Session started")
    }

    private fun validateEventName(name: String): String? {
        if (name.isEmpty()) return null
        if (name.length > 100) return null

        val pattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")
        if (!pattern.matches(name)) return null

        return name
    }
}

/**
 * Standard event names for common actions
 */
object FunnelMobStandardEvents {
    const val REGISTRATION = "fm_registration"
    const val LOGIN = "fm_login"
    const val PURCHASE = "fm_purchase"
    const val SUBSCRIBE = "fm_subscribe"
    const val TUTORIAL_COMPLETE = "fm_tutorial_complete"
    const val LEVEL_COMPLETE = "fm_level_complete"
    const val ADD_TO_CART = "fm_add_to_cart"
    const val CHECKOUT = "fm_checkout"
}
