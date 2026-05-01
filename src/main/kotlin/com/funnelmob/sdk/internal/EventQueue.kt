package com.funnelmob.sdk.internal

import android.content.Context
import android.content.SharedPreferences
import com.funnelmob.sdk.FunnelMobConfiguration
import org.json.JSONArray
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min
import kotlin.math.pow

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

    /**
     * In-flight guard so two near-simultaneous flush triggers (timer +
     * lifecycle hook) don't dequeue / re-queue overlapping batches and
     * scramble ordering.
     */
    private var isFlushInFlight = false

    /**
     * Earliest wall-clock time (`System.currentTimeMillis()`) at which the
     * next flush may run. Bumped on every retryable failure to implement
     * exponential backoff. `0L` = no wait pending.
     */
    private var nextFlushAllowedAt = 0L

    init {
        loadPersistedEvents()
    }

    /**
     * Add event to queue. Returns the new queue size. When the cap is
     * exceeded the oldest event is dropped (FIFO).
     */
    fun enqueue(event: Event): Int {
        return lock.withLock {
            events.add(event)
            if (events.size > MAX_QUEUE_SIZE) {
                val dropped = events.size - MAX_QUEUE_SIZE
                repeat(dropped) { events.removeAt(0) }
                Logger.warning("Queue size exceeded $MAX_QUEUE_SIZE; dropped $dropped oldest event(s)")
            }
            persistEvents()
            events.size
        }
    }

    /**
     * Get and remove events for sending
     */
    fun dequeue(maxCount: Int): List<Event> {
        lock.withLock {
            val count = min(maxCount, events.size)
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
            nextFlushAllowedAt = 0L
            prefs.edit().remove(KEY_EVENTS).apply()
        }
    }

    /**
     * Prepend a previously-dequeued batch back to the queue after a
     * retryable send failure. Preserves event ordering: the failed batch
     * retries first, before any newer events tracked while the in-flight
     * POST was outstanding.
     *
     * Re-serializes the entire queue to SharedPreferences on every call
     * (O(n)); bounded by [MAX_QUEUE_SIZE] so the cost is capped.
     */
    fun requeue(batch: List<Event>) {
        lock.withLock {
            events.addAll(0, batch)
            if (events.size > MAX_QUEUE_SIZE) {
                val dropped = events.size - MAX_QUEUE_SIZE
                // Drop the TAIL: the re-queued failed batch keeps retry
                // priority, newer events arriving after it lose first.
                repeat(dropped) { events.removeAt(events.size - 1) }
                Logger.warning("Queue size exceeded $MAX_QUEUE_SIZE after requeue; dropped $dropped newest event(s)")
            }
            persistEvents()
        }
    }

    /**
     * Flush all events. Returns immediately when (a) another flush is in
     * flight, or (b) we're inside an active backoff window from a prior
     * retryable failure.
     */
    fun flush(client: NetworkClient, configuration: FunnelMobConfiguration, deviceId: String, userId: String? = null) {
        val now = System.currentTimeMillis()
        lock.withLock {
            if (isFlushInFlight) {
                Logger.debug("flush() skipped: another flush is already in flight")
                return
            }
            if (now < nextFlushAllowedAt) {
                Logger.debug("flush() skipped: backoff active for ${nextFlushAllowedAt - now}ms more")
                return
            }
            isFlushInFlight = true
        }

        val batch = dequeue(configuration.maxBatchSize)
        if (batch.isEmpty()) {
            lock.withLock { isFlushInFlight = false }
            return
        }

        Logger.debug("Flushing ${batch.size} events")
        client.sendEvents(batch, deviceId, configuration, userId) { result ->
            try {
                result.onSuccess {
                    Logger.debug("Events sent successfully")
                    lock.withLock { nextFlushAllowedAt = 0L }
                }.onFailure { error ->
                    val retryable = (error as? NetworkError)?.isRetryable ?: true
                    if (!retryable) {
                        Logger.error("Dropped ${batch.size} events (non-retryable): ${error.message}")
                        return@onFailure
                    }
                    if (!configuration.enableRetryQueue) {
                        Logger.error("Dropped ${batch.size} events (retryable, but enableRetryQueue=false): ${error.message}")
                        return@onFailure
                    }

                    // Bump every event's attemptCount; if any has now
                    // exceeded the cap, the whole batch is poison.
                    val bumped = batch.map { it.copy(attemptCount = it.attemptCount + 1) }
                    val maxAttempts = bumped.maxOf { it.attemptCount }
                    if (maxAttempts > MAX_RETRY_ATTEMPTS) {
                        Logger.error("Dropped ${bumped.size} events (max retries $MAX_RETRY_ATTEMPTS exceeded): ${error.message}")
                        return@onFailure
                    }

                    requeue(bumped)
                    val nextAt = computeNextFlushAt(maxAttempts)
                    lock.withLock { nextFlushAllowedAt = nextAt }
                    Logger.warning("Re-queued ${bumped.size} events (attempt $maxAttempts/$MAX_RETRY_ATTEMPTS); next flush allowed in ${nextAt - System.currentTimeMillis()}ms: ${error.message}")
                }
            } finally {
                lock.withLock { isFlushInFlight = false }
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

    /**
     * Exponential backoff with jitter: `2^n` seconds capped at
     * [MAX_BACKOFF_MS], plus 0–1s jitter. Returns absolute
     * `System.currentTimeMillis()`-style value.
     */
    private fun computeNextFlushAt(attemptCount: Int): Long {
        val baseMs = (2.0.pow(attemptCount).toLong() * 1000L).coerceAtMost(MAX_BACKOFF_MS)
        val jitterMs = (Math.random() * MAX_JITTER_MS).toLong()
        return System.currentTimeMillis() + baseMs + jitterMs
    }

    companion object {
        private const val PREFS_NAME = "com.funnelmob.sdk.events"
        private const val KEY_EVENTS = "events"

        /**
         * Hard cap on the number of events held in memory + persistence.
         * When [enqueue] would exceed this, the OLDEST event is dropped
         * (FIFO). Same cap applies after [requeue] — events at the tail
         * are dropped first.
         */
        const val MAX_QUEUE_SIZE = 1000

        /**
         * Maximum number of times a batch may be re-queued before it's
         * dropped. Protects against poison-pill events that fail every
         * retry forever.
         */
        const val MAX_RETRY_ATTEMPTS = 5

        /** Hard cap on backoff sleep between retries (ms). */
        const val MAX_BACKOFF_MS = 60_000L

        /** Random jitter added to every backoff (0…this ms). */
        const val MAX_JITTER_MS = 1000L
    }
}
