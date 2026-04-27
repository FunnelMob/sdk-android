package com.funnelmob.sdk

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Callback type for attribution results
 */
fun interface AttributionCallback {
    fun onAttribution(result: AttributionResult?)
}

/**
 * Callback type for remote config loaded events
 */
fun interface ConfigLoadedCallback {
    fun onConfigLoaded(config: Map<String, Any?>)
}

/**
 * Main entry point for the FunnelMob SDK
 */
object FunnelMob {

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isStarted = false

    private var configuration: FunnelMobConfiguration? = null
    private var isEnabled = true
    private var attributionId: String? = null
    private val attributionCallbacks = mutableListOf<AttributionCallback>()
    private var userId: String? = null
    private var userProperties: MutableMap<String, Any>? = null
    private var remoteConfig: Map<String, Any?>? = null
    private val configCallbacks = mutableListOf<ConfigLoadedCallback>()

    private lateinit var appContext: Context
    private lateinit var eventQueue: EventQueue
    private lateinit var networkClient: NetworkClient
    private lateinit var deviceInfo: DeviceInfo
    private lateinit var attributionPrefs: SharedPreferences
    private lateinit var userPrefs: SharedPreferences
    private lateinit var configPrefs: SharedPreferences
    private var flushScheduler: ScheduledExecutorService? = null

    // ─── User identifiers ────────────────────────────────────────────────
    // Stored in memory only — never persisted. Hosts re-supply on each
    // launch (typically from their auth/consent state). GAID explicitly
    // should not be persisted because consent can be revoked between
    // sessions.
    @Volatile private var gaid: String? = null
    @Volatile private var hashedEmail: String? = null
    @Volatile private var hashedPhone: String? = null
    @Volatile private var hashedExternalId: String? = null
    // `identifierLock` guards isIdentifierDirty, isReFireInFlight,
    // identifierHandler, and identifierDebounceRunnable so concurrent setters
    // from arbitrary threads can't double-init the Handler or stomp the
    // pending Runnable bookkeeping.
    private val identifierLock = Any()
    private var isIdentifierDirty = false
    private var isReFireInFlight = false
    private var identifierHandler: Handler? = null
    private var identifierDebounceRunnable: Runnable? = null
    private const val IDENTIFIER_DEBOUNCE_MS = 1000L

    private const val ATTRIBUTION_PREFS = "funnelmob_attribution"
    private const val KEY_ATTRIBUTION_RESULT = "attribution_result"
    private const val USER_PREFS = "funnelmob_user"
    private const val KEY_USER_ID = "user_id"
    private const val CONFIG_PREFS = "funnelmob_config"
    private const val KEY_CONFIG = "config"
    private const val KEY_CONFIG_TS = "config_ts"
    private const val CONFIG_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    private const val REFERRER_TIMEOUT_SECS = 5L

    /**
     * Initialize the SDK with context and configuration.
     *
     * When [FunnelMobConfiguration.autoStart] is `true` (the default), this
     * method also calls [start] to begin attribution, the flush timer, the
     * lifecycle observer, and Install/ActivateApp events.
     *
     * When [FunnelMobConfiguration.autoStart] is `false`, this method only
     * wires up internal state (no network activity, no event tracking).
     * Call [start] explicitly once you have obtained any user consent
     * required by applicable law.
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
        this.userPrefs = appContext.getSharedPreferences(USER_PREFS, Context.MODE_PRIVATE)
        this.configPrefs = appContext.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)

        Logger.logLevel = configuration.logLevel
        Logger.info("FunnelMob initialized")

        isInitialized = true

        restoreUserId()
        loadCachedConfig()

        if (configuration.autoStart) {
            start()
        } else {
            Logger.info("autoStart disabled — call FunnelMob.start() when ready")
        }
    }

    /**
     * Start the SDK's active components: attribution session, remote config
     * fetch, flush timer, lifecycle observer, and the automatic
     * Install/ActivateApp events.
     *
     * Called automatically by [initialize] when
     * [FunnelMobConfiguration.autoStart] is `true` (the default). When
     * `autoStart` is `false`, the host application must call [start]
     * explicitly — typically after obtaining user consent (GDPR, CCPA, etc.).
     *
     * By calling [start], you represent that you have obtained any user
     * consent required by applicable law for the data the SDK will collect
     * and transmit.
     */
    @JvmStatic
    fun start() {
        check(isInitialized) { "FunnelMob SDK not initialized. Call initialize() first." }
        if (isStarted) {
            Logger.warning("FunnelMob already started")
            return
        }
        isStarted = true

        val config = configuration!!
        val isFirstLaunch = loadAttribution() == null

        // Initialize the identifier handler eagerly. Setters that fired
        // before start() set the dirty bit without scheduling; the
        // first-session POST below carries those values, and any later
        // setter goes through scheduleDebounce on this single Handler.
        synchronized(identifierLock) {
            if (identifierHandler == null) {
                identifierHandler = Handler(Looper.getMainLooper())
            }
        }

        startSession()
        fetchRemoteConfig()
        startFlushTimer(config)
        registerLifecycleObserver()

        if (isFirstLaunch) {
            trackInstall()
            trackActivateApp(
                FunnelMobEventParameters.build { set("is_first_session", true) }
            )
        } else {
            trackActivateApp()
        }
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

    // MARK: - Remote Config

    /**
     * Get a single remote config value by key.
     *
     * @param key The config key
     * @return The value, or null if not found
     */
    @JvmStatic
    fun getConfig(key: String): Any? {
        return remoteConfig?.get(key)
    }

    /**
     * Get a single remote config value with a default.
     *
     * @param key The config key
     * @param default Value to return if key not found
     * @return The config value or the default
     */
    @JvmStatic
    fun <T> getConfig(key: String, default: T): T {
        @Suppress("UNCHECKED_CAST")
        return (remoteConfig?.get(key) as? T) ?: default
    }

    /**
     * Get all remote config values.
     *
     * @return A copy of all config key-value pairs
     */
    @JvmStatic
    fun getAllConfig(): Map<String, Any?> {
        return remoteConfig?.toMap() ?: emptyMap()
    }

    /**
     * Register a callback that fires when remote config is loaded.
     * If config has already been loaded, the callback fires immediately.
     *
     * @param callback Called with the config map
     */
    @JvmStatic
    fun onConfigLoaded(callback: ConfigLoadedCallback) {
        configCallbacks.add(callback)

        remoteConfig?.let { callback.onConfigLoaded(it) }
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

        if (!isStarted) {
            Logger.debug("FunnelMob not started, ignoring event: $name")
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

        val queueSize = eventQueue.enqueue(event)
        Logger.debug("Event queued: $name")

        configuration?.let { cfg ->
            if (queueSize >= cfg.maxBatchSize) {
                flush()
            }
        }
    }

    /**
     * Force send queued events immediately
     */
    @JvmStatic
    fun flush() {
        if (!isInitialized) return
        val config = configuration ?: return
        eventQueue.flush(networkClient, config, deviceInfo.deviceId, userId)
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

    // MARK: - User Identification

    /**
     * Set the user ID for identified users.
     * Sends an identify request to the server and attaches the user ID to all subsequent events.
     *
     * @param userId The user identifier (must be non-empty)
     */
    @JvmStatic
    fun setUserId(userId: String) {
        check(isInitialized) { "FunnelMob SDK not initialized. Call initialize() first." }

        if (userId.isEmpty()) {
            Logger.error("setUserId: userId cannot be empty")
            return
        }

        this.userId = userId
        persistUserId(userId)
        Logger.info("User ID set: $userId")
        sendIdentify()
    }

    /**
     * Set user properties for the current identified user.
     * Properties are merged with existing properties.
     * Requires setUserId() to be called first.
     *
     * @param properties User properties to set
     */
    @JvmStatic
    fun setUserProperties(properties: Map<String, Any>) {
        check(isInitialized) { "FunnelMob SDK not initialized. Call initialize() first." }

        if (userId == null) {
            Logger.error("setUserProperties: call setUserId() first")
            return
        }

        if (userProperties == null) {
            userProperties = mutableMapOf()
        }
        userProperties!!.putAll(properties)

        Logger.debug("User properties updated")
        sendIdentify()
    }

    /**
     * Clear the current user ID (e.g., on logout).
     * Subsequent events will not include a user_id.
     */
    @JvmStatic
    fun clearUserId() {
        userId = null
        userProperties = null
        if (::userPrefs.isInitialized) {
            userPrefs.edit().remove(KEY_USER_ID).apply()
        }
        Logger.info("User ID cleared")
    }

    // MARK: - Private

    private fun sendIdentify() {
        val config = configuration ?: return
        val uid = userId ?: return

        Thread {
            val context = deviceInfo.toContext()
            val payload = JSONObject().apply {
                put("device_id", deviceInfo.deviceId)
                put("user_id", uid)
                put("platform", "android")
                put("timestamp", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()))
                userProperties?.let { put("user_properties", JSONObject(it as Map<*, *>)) }
                put("context", JSONObject().apply {
                    put("os_version", context.osVersion)
                    put("device_model", context.deviceModel)
                    put("locale", context.locale)
                    put("timezone", context.timezone)
                    put("screen_width", context.screenWidth)
                    put("screen_height", context.screenHeight)
                })
            }

            networkClient.sendIdentify(payload, config) { result ->
                result.onSuccess {
                    Logger.debug("Identify request sent")
                }.onFailure { error ->
                    Logger.error("Identify request failed: ${error.message}")
                }
            }
        }.start()
    }

    private fun persistUserId(userId: String) {
        if (::userPrefs.isInitialized) {
            userPrefs.edit().putString(KEY_USER_ID, userId).apply()
        }
    }

    private fun restoreUserId() {
        if (::userPrefs.isInitialized) {
            val stored = userPrefs.getString(KEY_USER_ID, null)
            if (stored != null) {
                this.userId = stored
                Logger.debug("Restored user ID: $stored")
            }
        }
    }

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

        // Snapshot and clear the dirty bit before sending. This POST carries
        // current identifiers, so a successful round-trip means we've already
        // delivered them; clearing here avoids a redundant re-fire on the
        // first foreground hook. If a setter races during the in-flight POST,
        // it sets dirty back to true (and the in-flight check in
        // markIdentifierDirty defers scheduling until completion).
        val wasDirty: Boolean
        synchronized(identifierLock) {
            wasDirty = isIdentifierDirty
            isIdentifierDirty = false
        }
        val payload = buildSessionPayload(isFirstSession = true, referrerToken = referrerToken)

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
                // Restore dirty so a foreground hook / next setter re-fires.
                synchronized(identifierLock) {
                    if (wasDirty) isIdentifierDirty = true
                }
            }
        }
    }

    /// Build a `/v1/session` payload populated with the current in-memory
    /// identifier set. Shared between the first-session attribution path
    /// and the debounced re-fire path so both produce identically-shaped
    /// payloads.
    private fun buildSessionPayload(
        isFirstSession: Boolean,
        referrerToken: String? = null,
    ): JSONObject {
        val context = deviceInfo.toContext()
        val timestamp = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            java.util.Locale.US,
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        return JSONObject().apply {
            put("device_id", deviceInfo.deviceId)
            put("session_id", UUID.randomUUID().toString())
            put("platform", "android")
            put("timestamp", timestamp)
            put("is_first_session", isFirstSession)
            referrerToken?.let { put("referrer_token", it) }
            gaid?.let { put("gaid", it) }
            hashedEmail?.let { put("email_sha256", it) }
            hashedPhone?.let { put("phone_sha256", it) }
            hashedExternalId?.let { put("external_id_sha256", it) }
            put("context", JSONObject().apply {
                put("os_version", context.osVersion)
                put("device_model", context.deviceModel)
                put("locale", context.locale)
                put("timezone", context.timezone)
                put("screen_width", context.screenWidth)
                put("screen_height", context.screenHeight)
            })
        }
    }

    // MARK: - User identifier setters

    /**
     * Set the device's Google Advertising ID. Pass the value the host read
     * from `AdvertisingIdClient.getAdvertisingIdInfo(...)` after the user
     * granted consent. Pass `null` to remove the value from the SDK's
     * in-memory map.
     *
     * The SDK never reads GAID itself — calling this method is the host's
     * affirmative representation that consent was granted.
     */
    @JvmStatic
    fun setGAID(gaid: String?) {
        this.gaid = gaid
        Logger.debug("GAID ${if (gaid == null) "cleared" else "set"}")
        markIdentifierDirty()
    }

    /**
     * Set the SHA256-hex hash of the user's email (lowercase + trim, then
     * SHA256). Forwarded to Meta CAPI as `user_data.em` and TikTok Events
     * as `user.email`. The SDK never sees the raw value — the host is
     * responsible for normalization and hashing.
     */
    @JvmStatic
    fun setHashedEmail(sha256: String?) {
        this.hashedEmail = sha256
        Logger.debug("Hashed email ${if (sha256 == null) "cleared" else "set"}")
        markIdentifierDirty()
    }

    /**
     * Set the SHA256-hex hash of the user's phone number (E.164 format
     * pre-hash, e.g. `+12025551234`).
     */
    @JvmStatic
    fun setHashedPhone(sha256: String?) {
        this.hashedPhone = sha256
        Logger.debug("Hashed phone ${if (sha256 == null) "cleared" else "set"}")
        markIdentifierDirty()
    }

    /**
     * Set the SHA256-hex hash of an external user identifier (CRM ID,
     * auth user ID).
     */
    @JvmStatic
    fun setHashedExternalId(sha256: String?) {
        this.hashedExternalId = sha256
        Logger.debug("Hashed external_id ${if (sha256 == null) "cleared" else "set"}")
        markIdentifierDirty()
    }

    /**
     * Bypass the 1-second debounce and immediately fire a `/v1/session`
     * re-fire if any identifier has changed since the last successful
     * POST. No-op if not started or not dirty. Useful for tests and for
     * hosts that want a synchronous confirmation point.
     */
    @JvmStatic
    fun flushIdentifiers() {
        synchronized(identifierLock) {
            val handler = identifierHandler
            val runnable = identifierDebounceRunnable
            if (handler != null && runnable != null) {
                handler.removeCallbacks(runnable)
            }
            identifierDebounceRunnable = null
        }
        triggerIdentifierReFire()
    }

    // MARK: - Identifier debounce + re-fire (private)

    /**
     * Mark the in-memory identifier set as dirty and schedule a debounced
     * re-fire. Called by every setter. No-op while `isStarted == false`
     * (the values are buffered and ride on the first `start()` POST) or
     * while a re-fire is already in flight (handled on POST completion).
     */
    private fun markIdentifierDirty() {
        synchronized(identifierLock) {
            isIdentifierDirty = true
            if (!isStarted) return
            if (isReFireInFlight) return
            scheduleDebounceLocked()
        }
    }

    /** Must be called holding `identifierLock`. */
    private fun scheduleDebounceLocked() {
        val handler = identifierHandler ?: return
        identifierDebounceRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { triggerIdentifierReFire() }
        identifierDebounceRunnable = runnable
        handler.postDelayed(runnable, IDENTIFIER_DEBOUNCE_MS)
    }

    private fun triggerIdentifierReFire() {
        val config: FunnelMobConfiguration
        val payload: JSONObject
        synchronized(identifierLock) {
            if (!isStarted) return
            if (!isIdentifierDirty) return
            config = configuration ?: return
            isReFireInFlight = true
            isIdentifierDirty = false
            identifierDebounceRunnable = null
            payload = buildSessionPayload(isFirstSession = false)
        }

        Thread {
            networkClient.sendSession(payload, config) { result ->
                synchronized(identifierLock) {
                    isReFireInFlight = false
                    result.onSuccess {
                        Logger.debug("Identifier re-fire succeeded")
                    }.onFailure { error ->
                        // Restore dirty so foreground hook + next setter recover.
                        isIdentifierDirty = true
                        Logger.warning("Identifier re-fire failed: ${error.message}")
                    }
                    if (isIdentifierDirty) {
                        identifierHandler?.post {
                            synchronized(identifierLock) {
                                if (isIdentifierDirty && !isReFireInFlight) {
                                    scheduleDebounceLocked()
                                }
                            }
                        }
                    }
                }
            }
        }.start()
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

    // MARK: - Remote Config (Private)

    private fun startFlushTimer(configuration: FunnelMobConfiguration) {
        flushScheduler = Executors.newSingleThreadScheduledExecutor()
        flushScheduler?.scheduleAtFixedRate(
            { flush() },
            configuration.flushIntervalMs,
            configuration.flushIntervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun registerLifecycleObserver() {
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    flush()
                    // Recover any pending identifier re-fire that lost a previous
                    // POST attempt while the app was backgrounded.
                    flushIdentifiers()
                }

                override fun onStop(owner: LifecycleOwner) {
                    flush()
                }
            })
        }
    }

    private fun fetchRemoteConfig() {
        val config = configuration ?: return

        networkClient.fetchConfig(config) { result ->
            result.onSuccess { json ->
                val configMap = mutableMapOf<String, Any?>()
                json.keys().forEach { key ->
                    configMap[key] = json.opt(key)
                }
                remoteConfig = configMap
                saveCachedConfig(json)
                Logger.debug("Remote config loaded")
                notifyConfigCallbacks(configMap)
            }.onFailure { error ->
                Logger.error("Failed to fetch remote config: ${error.message}")
            }
        }
    }

    private fun loadCachedConfig() {
        if (!::configPrefs.isInitialized) return
        val ts = configPrefs.getLong(KEY_CONFIG_TS, 0)
        if (System.currentTimeMillis() - ts > CONFIG_CACHE_TTL_MS) return
        val jsonStr = configPrefs.getString(KEY_CONFIG, null) ?: return
        try {
            val json = JSONObject(jsonStr)
            val configMap = mutableMapOf<String, Any?>()
            json.keys().forEach { key -> configMap[key] = json.opt(key) }
            remoteConfig = configMap
            Logger.debug("Loaded cached remote config")
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun saveCachedConfig(json: JSONObject) {
        if (!::configPrefs.isInitialized) return
        try {
            configPrefs.edit()
                .putString(KEY_CONFIG, json.toString())
                .putLong(KEY_CONFIG_TS, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun notifyConfigCallbacks(config: Map<String, Any?>) {
        for (callback in configCallbacks) {
            try {
                callback.onConfigLoaded(config)
            } catch (e: Exception) {
                Logger.error("Config callback error: ${e.message}")
            }
        }
    }

    private fun validateEventName(name: String): String? {
        if (name.isEmpty()) return null
        if (name.length > 100) return null

        val pattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")
        if (!pattern.matches(name)) return null

        return name
    }

    // MARK: - Typed Standard Event Methods

    /** Meta only — fires on every page load */
    @JvmStatic
    fun trackPageView(parameters: FunnelMobEventParameters? = null) {
        trackEvent("PageView", parameters = parameters)
    }

    /** Meta + TikTok — visit to a product detail, landing, or content page */
    @JvmStatic
    fun trackViewContent(parameters: FunnelMobEventParameters? = null) {
        trackEvent("ViewContent", parameters = parameters)
    }

    /** Meta + TikTok — search performed on your site or app */
    @JvmStatic
    fun trackSearch(parameters: FunnelMobEventParameters? = null) {
        trackEvent("Search", parameters = parameters)
    }

    /** Meta + TikTok — item added to shopping cart */
    @JvmStatic
    fun trackAddToCart(parameters: FunnelMobEventParameters? = null) {
        trackEvent("AddToCart", parameters = parameters)
    }

    /** Meta + TikTok — item added to wishlist */
    @JvmStatic
    fun trackAddToWishlist(parameters: FunnelMobEventParameters? = null) {
        trackEvent("AddToWishlist", parameters = parameters)
    }

    /** Meta + TikTok — start of checkout process */
    @JvmStatic
    fun trackInitiateCheckout(parameters: FunnelMobEventParameters? = null) {
        trackEvent("InitiateCheckout", parameters = parameters)
    }

    /** Meta + TikTok — payment info entered during checkout */
    @JvmStatic
    fun trackAddPaymentInfo(parameters: FunnelMobEventParameters? = null) {
        trackEvent("AddPaymentInfo", parameters = parameters)
    }

    /** Meta + TikTok — purchase completed; value and currency are required */
    @JvmStatic
    fun trackPurchase(value: Double, currency: String, parameters: FunnelMobEventParameters? = null) {
        trackEvent("Purchase", revenue = FunnelMobRevenue.of(value, currency), parameters = parameters)
    }

    /** Meta + TikTok — user submits contact information */
    @JvmStatic
    fun trackLead(parameters: FunnelMobEventParameters? = null) {
        trackEvent("Lead", parameters = parameters)
    }

    /** Meta + TikTok — user completes a registration or sign-up flow */
    @JvmStatic
    fun trackCompleteRegistration(parameters: FunnelMobEventParameters? = null) {
        trackEvent("CompleteRegistration", parameters = parameters)
    }

    /** Meta + TikTok — any contact initiated between user and business */
    @JvmStatic
    fun trackContact(parameters: FunnelMobEventParameters? = null) {
        trackEvent("Contact", parameters = parameters)
    }

    /** Meta + TikTok — user books an appointment or reservation */
    @JvmStatic
    fun trackSchedule(parameters: FunnelMobEventParameters? = null) {
        trackEvent("Schedule", parameters = parameters)
    }

    /** Meta + TikTok — user searches for a physical business location */
    @JvmStatic
    fun trackFindLocation(parameters: FunnelMobEventParameters? = null) {
        trackEvent("FindLocation", parameters = parameters)
    }

    /** Meta + TikTok — user customizes a product */
    @JvmStatic
    fun trackCustomizeProduct(parameters: FunnelMobEventParameters? = null) {
        trackEvent("CustomizeProduct", parameters = parameters)
    }

    /** Meta only — donation completed; value and currency are required */
    @JvmStatic
    fun trackDonate(value: Double, currency: String, parameters: FunnelMobEventParameters? = null) {
        trackEvent("Donate", revenue = FunnelMobRevenue.of(value, currency), parameters = parameters)
    }

    /** Meta + TikTok — user submits an application */
    @JvmStatic
    fun trackSubmitApplication(parameters: FunnelMobEventParameters? = null) {
        trackEvent("SubmitApplication", parameters = parameters)
    }

    /** TikTok only — application previously submitted is approved */
    @JvmStatic
    fun trackApplicationApproval(parameters: FunnelMobEventParameters? = null) {
        trackEvent("ApplicationApproval", parameters = parameters)
    }

    /** TikTok only — user downloads a file or asset */
    @JvmStatic
    fun trackDownload(parameters: FunnelMobEventParameters? = null) {
        trackEvent("Download", parameters = parameters)
    }

    /** TikTok legacy — use trackLead() for new implementations */
    @JvmStatic
    fun trackSubmitForm(parameters: FunnelMobEventParameters? = null) {
        trackEvent("SubmitForm", parameters = parameters)
    }

    /** Meta + TikTok — user begins a free trial; value and currency are required */
    @JvmStatic
    fun trackStartTrial(value: Double, currency: String, parameters: FunnelMobEventParameters? = null) {
        trackEvent("StartTrial", revenue = FunnelMobRevenue.of(value, currency), parameters = parameters)
    }

    /** Meta + TikTok — user starts a paid subscription; value and currency are required */
    @JvmStatic
    fun trackSubscribe(value: Double, currency: String, parameters: FunnelMobEventParameters? = null) {
        trackEvent("Subscribe", revenue = FunnelMobRevenue.of(value, currency), parameters = parameters)
    }

    /** Meta only — user reaches a level in your app or game */
    @JvmStatic
    fun trackAchieveLevel(parameters: FunnelMobEventParameters? = null) {
        trackEvent("AchieveLevel", parameters = parameters)
    }

    /** Meta only — user completes a rewarded action or milestone */
    @JvmStatic
    fun trackUnlockAchievement(parameters: FunnelMobEventParameters? = null) {
        trackEvent("UnlockAchievement", parameters = parameters)
    }

    /**
     * Meta only — user spends in-app credits or virtual currency; value is required.
     * Note: value is passed as a parameter (not revenue) because SpentCredits uses
     * virtual currency, which has no ISO 4217 currency code.
     */
    @JvmStatic
    fun trackSpentCredits(value: Double, parameters: FunnelMobEventParameters? = null) {
        val builder = FunnelMobEventParameters.Builder()
        parameters?.toMap()?.forEach { (k, v) ->
            when (v) {
                is String -> builder.set(k, v)
                is Int -> builder.set(k, v)
                is Long -> builder.set(k, v)
                is Double -> builder.set(k, v)
                is Boolean -> builder.set(k, v)
            }
        }
        builder.set("value", value)
        trackEvent("SpentCredits", parameters = builder.build())
    }

    /** Meta only — user submits a rating */
    @JvmStatic
    fun trackRate(parameters: FunnelMobEventParameters? = null) {
        trackEvent("Rate", parameters = parameters)
    }

    /** Meta only — user completes an in-app tutorial */
    @JvmStatic
    fun trackCompleteTutorial(parameters: FunnelMobEventParameters? = null) {
        trackEvent("CompleteTutorial", parameters = parameters)
    }

    /**
     * First-launch install event. Maps to Meta's `Install` event in CAPI and
     * TikTok's `InstallApp` in Events API. Fired automatically by
     * [initialize] on the device's first ever launch.
     */
    @JvmStatic
    fun trackInstall(parameters: FunnelMobEventParameters? = null) {
        trackEvent("Install", parameters = parameters)
    }

    /** App launch / activate. Fired automatically by [initialize] on every cold start. */
    @JvmStatic
    fun trackActivateApp(parameters: FunnelMobEventParameters? = null) {
        trackEvent("ActivateApp", parameters = parameters)
    }

    /** Meta only — in-app ad clicked by user */
    @JvmStatic
    fun trackInAppAdClick(parameters: FunnelMobEventParameters? = null) {
        trackEvent("InAppAdClick", parameters = parameters)
    }

    /** Meta only — in-app ad appeared on-screen */
    @JvmStatic
    fun trackInAppAdImpression(parameters: FunnelMobEventParameters? = null) {
        trackEvent("InAppAdImpression", parameters = parameters)
    }
}

/**
 * Standard event names for common actions.
 *
 * Names mirror Meta/TikTok Standard Events verbatim so the postback layer
 * does not need to translate. INSTALL and ACTIVATE_APP are mobile lifecycle
 * events fired automatically by FunnelMob.initialize().
 */
object FunnelMobStandardEvents {
    const val INSTALL = "Install"
    const val ACTIVATE_APP = "ActivateApp"
    const val PAGE_VIEW = "PageView"
    const val VIEW_CONTENT = "ViewContent"
    const val SEARCH = "Search"
    const val ADD_TO_CART = "AddToCart"
    const val ADD_TO_WISHLIST = "AddToWishlist"
    const val INITIATE_CHECKOUT = "InitiateCheckout"
    const val ADD_PAYMENT_INFO = "AddPaymentInfo"
    const val PURCHASE = "Purchase"
    const val LEAD = "Lead"
    const val COMPLETE_REGISTRATION = "CompleteRegistration"
    const val CONTACT = "Contact"
    const val SCHEDULE = "Schedule"
    const val FIND_LOCATION = "FindLocation"
    const val CUSTOMIZE_PRODUCT = "CustomizeProduct"
    const val DONATE = "Donate"
    const val SUBMIT_APPLICATION = "SubmitApplication"
    const val APPLICATION_APPROVAL = "ApplicationApproval"
    const val DOWNLOAD = "Download"
    const val SUBMIT_FORM = "SubmitForm"
    const val START_TRIAL = "StartTrial"
    const val SUBSCRIBE = "Subscribe"
    const val ACHIEVE_LEVEL = "AchieveLevel"
    const val UNLOCK_ACHIEVEMENT = "UnlockAchievement"
    const val SPENT_CREDITS = "SpentCredits"
    const val RATE = "Rate"
    const val COMPLETE_TUTORIAL = "CompleteTutorial"
    const val IN_APP_AD_CLICK = "InAppAdClick"
    const val IN_APP_AD_IMPRESSION = "InAppAdImpression"
}
