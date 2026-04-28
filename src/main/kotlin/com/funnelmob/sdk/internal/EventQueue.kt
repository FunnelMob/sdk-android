package com.funnelmob.sdk.internal

import android.content.Context
import android.content.SharedPreferences
import com.funnelmob.sdk.FunnelMobConfiguration
import org.json.JSONArray
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe event queue with persistence
 */
internal class EventQueue(context: Context) {

    private val events = mutableListOf<Event>()
    private val lock = ReentrantLock()
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        loadPersistedEvents()
    }

    /**
     * Add event to queue. Returns the new queue size.
     */
    fun enqueue(event: Event): Int {
        return lock.withLock {
            events.add(event)
            persistEvents()
            events.size
        }
    }

    /**
     * Get and remove events for sending
     */
    fun dequeue(maxCount: Int): List<Event> {
        lock.withLock {
            val count = minOf(maxCount, events.size)
            val batch = events.take(count).toList()
            repeat(count) { events.removeAt(0) }
            persistEvents()
            return batch
        }
    }

    /**
     * Current queue count
     */
    val count: Int
        get() = lock.withLock { events.size }

    /**
     * Drop every queued event from memory and persistent storage.
     * Used when the user revokes consent — GDPR requires the SDK to
     * stop processing further data, including data already in flight
     * to the network layer.
     */
    fun clear() {
        lock.withLock {
            events.clear()
            prefs.edit().remove(KEY_EVENTS).apply()
        }
    }

    /**
     * Prepend a previously-dequeued batch back to the queue after a
     * retryable send failure. Preserves event ordering: the failed
     * batch retries first, before any newer events tracked while the
     * in-flight POST was outstanding.
     */
    fun requeue(batch: List<Event>) {
        lock.withLock {
            events.addAll(0, batch)
            persistEvents()
        }
    }

    /**
     * Flush all events
     */
    fun flush(client: NetworkClient, configuration: FunnelMobConfiguration, deviceId: String, userId: String? = null) {
        val batch = dequeue(configuration.maxBatchSize)
        if (batch.isEmpty()) return

        Logger.debug("Flushing ${batch.size} events")
        client.sendEvents(batch, deviceId, configuration, userId) { result ->
            result.onSuccess {
                Logger.debug("Events sent successfully")
            }.onFailure { error ->
                // Treat unclassified errors as retryable (defensive default —
                // most non-NetworkError throwables are transient runtime issues).
                val retryable = (error as? NetworkError)?.isRetryable ?: true
                if (retryable) {
                    requeue(batch)
                    Logger.warning("Re-queued ${batch.size} events: ${error.message}")
                } else {
                    Logger.error("Dropped ${batch.size} events (non-retryable): ${error.message}")
                }
            }
        }
    }

    // MARK: - Persistence

    private fun persistEvents() {
        val jsonArray = JSONArray()
        events.forEach { event ->
            jsonArray.put(event.toJson())
        }
        prefs.edit().putString(KEY_EVENTS, jsonArray.toString()).apply()
    }

    private fun loadPersistedEvents() {
        val json = prefs.getString(KEY_EVENTS, null) ?: return

        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val eventJson = jsonArray.getJSONObject(i)
                events.add(Event.fromJson(eventJson))
            }
            Logger.debug("Loaded ${events.size} persisted events")
        } catch (e: Exception) {
            Logger.error("Failed to load persisted events: ${e.message}")
            prefs.edit().remove(KEY_EVENTS).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "com.funnelmob.sdk.events"
        private const val KEY_EVENTS = "events"
    }
}
