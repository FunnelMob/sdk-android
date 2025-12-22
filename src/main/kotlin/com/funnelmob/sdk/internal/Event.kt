package com.funnelmob.sdk.internal

import org.json.JSONObject

/**
 * Internal event representation
 */
internal data class Event(
    val eventId: String,
    val eventName: String,
    val timestamp: String,
    val revenue: EventRevenue?,
    val parameters: Map<String, Any>?
) {

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("event_id", eventId)
            put("event_name", eventName)
            put("timestamp", timestamp)

            revenue?.let {
                put("revenue", JSONObject().apply {
                    put("amount", it.amount)
                    put("currency", it.currency)
                })
            }

            parameters?.let { params ->
                put("parameters", JSONObject(params))
            }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Event {
            val revenueJson = json.optJSONObject("revenue")
            val paramsJson = json.optJSONObject("parameters")

            return Event(
                eventId = json.getString("event_id"),
                eventName = json.getString("event_name"),
                timestamp = json.getString("timestamp"),
                revenue = revenueJson?.let {
                    EventRevenue(
                        amount = it.getString("amount"),
                        currency = it.getString("currency")
                    )
                },
                parameters = paramsJson?.let { obj ->
                    obj.keys().asSequence().associateWith { key -> obj.get(key) }
                }
            )
        }
    }
}

/**
 * Internal revenue representation
 */
internal data class EventRevenue(
    val amount: String,
    val currency: String
)

// Extension for public API
internal fun com.funnelmob.sdk.FunnelMobRevenue.toEventRevenue(): EventRevenue {
    return EventRevenue(
        amount = this.amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
        currency = this.currency.uppercase()
    )
}
