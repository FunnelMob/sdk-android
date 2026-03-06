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
    val parameters: Map<String, Any>?,
    val attributionId: String? = null
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

            attributionId?.let {
                put("attribution_id", it)
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
                },
                attributionId = json.optString("attribution_id", null)
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

/**
 * Attribution result from the server
 */
data class AttributionResult(
    val attributionId: String,
    val attributed: Boolean,
    val method: String,
    val campaignId: String?,
    val adNetwork: String?,
    val adGroupId: String?,
    val adId: String?,
    val keyword: String?,
    val confidence: Double
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("attribution_id", attributionId)
            put("attributed", attributed)
            put("method", method)
            campaignId?.let { put("campaign_id", it) }
            adNetwork?.let { put("ad_network", it) }
            adGroupId?.let { put("ad_group_id", it) }
            adId?.let { put("ad_id", it) }
            keyword?.let { put("keyword", it) }
            put("confidence", confidence)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AttributionResult {
            return AttributionResult(
                attributionId = json.getString("attribution_id"),
                attributed = json.getBoolean("attributed"),
                method = json.getString("method"),
                campaignId = json.optString("campaign_id", null),
                adNetwork = json.optString("ad_network", null),
                adGroupId = json.optString("ad_group_id", null),
                adId = json.optString("ad_id", null),
                keyword = json.optString("keyword", null),
                confidence = json.optDouble("confidence", 0.0)
            )
        }
    }
}

/**
 * Identify response from the server
 */
data class IdentifyResponse(
    val status: String
) {
    companion object {
        fun fromJson(json: JSONObject): IdentifyResponse {
            return IdentifyResponse(
                status = json.getString("status")
            )
        }
    }
}

// Extension for public API
internal fun com.funnelmob.sdk.FunnelMobRevenue.toEventRevenue(): EventRevenue {
    return EventRevenue(
        amount = this.amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
        currency = this.currency.uppercase()
    )
}
