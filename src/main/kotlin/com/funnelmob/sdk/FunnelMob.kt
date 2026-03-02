package com.funnelmob.sdk

import android.content.Context
import android.content.SharedPreferences
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.funnelmob.sdk.internal.AttributionResult
import com.funnelmob.sdk.internal.DeviceInfo
import com.funnelmob.sdk.internal.Event
import com.funnelmob.sdk.internal.EventQueue
import com.funnelmob.sdk.internal.Logger
import com.funnelmob.sdk.internal.NetworkClient
import com.funnelmob.sdk.internal.toEventRevenue
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Callback type for attribution results
 */
fun interface AttributionCallback {
    fun onAttribution(result: AttributionResult?)
}

/**
 * Main entry point for the FunnelMob SDK
 */
object FunnelMob {

    @Volatile
    private var isInitialized = false

    private var configuration: FunnelMobConfiguration? = null
    private var isEnabled = true
    private var attributionId: String? = null
    private val attributionCallbacks = mutableListOf<AttributionCallback>()

    private lateinit var appContext: Context
    private lateinit var eventQueue: EventQueue
    private lateinit var networkClient: NetworkClient
    private lateinit var deviceInfo: DeviceInfo
    private lateinit var attributionPrefs: SharedPreferences

    private const val ATTRIBUTION_PREFS = "funnelmob_attribution"
    private const val KEY_ATTRIBUTION_RESULT = "attribution_result"
    private const val REFERRER_TIMEOUT_SECS = 5L

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

        appContext = context.applicationContext
        this.configuration = configuration
        this.eventQueue = EventQueue(appContext)
        this.networkClient = NetworkClient()
        this.deviceInfo = DeviceInfo(appContext)
        this.attributionPrefs = appContext.getSharedPreferences(ATTRIBUTION_PREFS, Context.MODE_PRIVATE)

        Logger.logLevel = configuration.logLevel
        Logger.info("FunnelMob initialized")

        isInitialized = true
        startSession()
    }

    /**
     * Register a callback for attribution results.
     * If attribution has already completed, the callback fires immediately.
     *
     * @param callback Attribution result callback
     */
    @JvmStatic
    fun onAttribution(callback: AttributionCallback) {
        attributionCallbacks.add(callback)

        // If we already have a stored result, fire immediately
        val stored = loadAttribution()
        if (stored != null) {
            callback.onAttribution(stored)
        }
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
            parameters = parameters?.toMap(),
            attributionId = attributionId
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
        // Check for existing attribution
        val stored = loadAttribution()
        if (stored != null) {
            attributionId = stored.attributionId
            Logger.debug("Loaded existing attribution")
            notifyCallbacks(stored)
            return
        }

        // First session — read Install Referrer then request attribution
        Thread {
            val referrerToken = readInstallReferrer()
            requestAttribution(referrerToken)
        }.start()
    }

    private fun readInstallReferrer(): String? {
        try {
            val latch = CountDownLatch(1)
            var referrerToken: String? = null

            val client = InstallReferrerClient.newBuilder(appContext).build()
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            val referrer = client.installReferrer?.installReferrer
                            if (referrer != null) {
                                referrerToken = parseReferrerToken(referrer)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.debug("Install referrer read error: ${e.message}")
                    } finally {
                        try { client.endConnection() } catch (_: Exception) {}
                        latch.countDown()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    latch.countDown()
                }
            })

            latch.await(REFERRER_TIMEOUT_SECS, TimeUnit.SECONDS)
            return referrerToken
        } catch (e: Exception) {
            Logger.debug("Install referrer unavailable: ${e.message}")
            return null
        }
    }

    private fun parseReferrerToken(referrer: String): String? {
        // Parse "fm_click=<click_token>" from the referrer string
        val decoded = URLDecoder.decode(referrer, "UTF-8")
        val params = decoded.split("&")
        for (param in params) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2 && parts[0] == "fm_click") {
                return parts[1]
            }
        }
        return null
    }

    private fun requestAttribution(referrerToken: String?) {
        val config = configuration ?: return

        val context = deviceInfo.toContext()
        val payload = JSONObject().apply {
            put("device_id", deviceInfo.deviceId)
            put("platform", "android")
            put("is_first_session", true)
            put("os_version", context.osVersion)
            put("device_model", context.deviceModel)
            put("language", context.locale)
            put("timezone", context.timezone)
            put("screen_width", context.screenWidth)
            put("screen_height", context.screenHeight)
            referrerToken?.let { put("referrer_token", it) }
        }

        networkClient.sendSession(payload, config) { result ->
            result.onSuccess { json ->
                val attrJson = json?.optJSONObject("attribution")
                if (attrJson != null) {
                    val attribution = AttributionResult.fromJson(attrJson)
                    attributionId = attribution.attributionId
                    saveAttribution(attribution)
                    Logger.info("Attribution received")
                    notifyCallbacks(attribution)
                } else {
                    notifyCallbacks(null)
                }
            }.onFailure { error ->
                Logger.error("Attribution request failed: ${error.message}")
                notifyCallbacks(null)
            }
        }
    }

    private fun notifyCallbacks(result: AttributionResult?) {
        for (callback in attributionCallbacks) {
            try {
                callback.onAttribution(result)
            } catch (e: Exception) {
                Logger.error("Attribution callback error: ${e.message}")
            }
        }
    }

    private fun loadAttribution(): AttributionResult? {
        return try {
            val json = attributionPrefs.getString(KEY_ATTRIBUTION_RESULT, null) ?: return null
            AttributionResult.fromJson(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    private fun saveAttribution(result: AttributionResult) {
        try {
            attributionPrefs.edit()
                .putString(KEY_ATTRIBUTION_RESULT, result.toJson().toString())
                .apply()
        } catch (e: Exception) {
            Logger.warning("Failed to save attribution: ${e.message}")
        }
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
