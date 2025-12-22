package com.funnelmob.sdk

/**
 * Container for custom event parameters
 */
class FunnelMobEventParameters private constructor(
    private val params: Map<String, Any>
) {

    internal fun toMap(): Map<String, Any>? {
        return params.ifEmpty { null }
    }

    /**
     * Builder for event parameters
     */
    class Builder {
        private val params = mutableMapOf<String, Any>()

        fun set(key: String, value: String) = apply { params[key] = value }

        fun set(key: String, value: Int) = apply { params[key] = value }

        fun set(key: String, value: Long) = apply { params[key] = value }

        fun set(key: String, value: Double) = apply { params[key] = value }

        fun set(key: String, value: Boolean) = apply { params[key] = value }

        fun build() = FunnelMobEventParameters(params.toMap())
    }

    companion object {
        /**
         * Create parameters using a builder DSL
         */
        inline fun build(block: Builder.() -> Unit): FunnelMobEventParameters {
            return Builder().apply(block).build()
        }

        /**
         * Create empty parameters
         */
        fun empty() = FunnelMobEventParameters(emptyMap())

        /**
         * Create parameters from a map
         */
        fun fromMap(map: Map<String, Any>): FunnelMobEventParameters {
            val filtered = map.filterValues { value ->
                value is String || value is Int || value is Long ||
                    value is Double || value is Boolean
            }
            return FunnelMobEventParameters(filtered)
        }
    }
}
