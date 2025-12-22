package com.funnelmob.sdk

import java.math.BigDecimal

/**
 * Revenue information for purchase events
 */
data class FunnelMobRevenue(
    /** Revenue amount */
    val amount: BigDecimal,

    /** ISO 4217 currency code (e.g., "USD", "EUR") */
    val currency: String
) {

    init {
        require(currency.length == 3) { "Currency must be a 3-letter ISO 4217 code" }
    }

    companion object {
        /**
         * Create USD revenue
         */
        @JvmStatic
        fun usd(amount: Double) = FunnelMobRevenue(
            amount = BigDecimal.valueOf(amount),
            currency = "USD"
        )

        /**
         * Create USD revenue from BigDecimal
         */
        @JvmStatic
        fun usd(amount: BigDecimal) = FunnelMobRevenue(
            amount = amount,
            currency = "USD"
        )

        /**
         * Create EUR revenue
         */
        @JvmStatic
        fun eur(amount: Double) = FunnelMobRevenue(
            amount = BigDecimal.valueOf(amount),
            currency = "EUR"
        )

        /**
         * Create EUR revenue from BigDecimal
         */
        @JvmStatic
        fun eur(amount: BigDecimal) = FunnelMobRevenue(
            amount = amount,
            currency = "EUR"
        )

        /**
         * Create GBP revenue
         */
        @JvmStatic
        fun gbp(amount: Double) = FunnelMobRevenue(
            amount = BigDecimal.valueOf(amount),
            currency = "GBP"
        )

        /**
         * Create revenue with custom currency
         */
        @JvmStatic
        fun of(amount: Double, currency: String) = FunnelMobRevenue(
            amount = BigDecimal.valueOf(amount),
            currency = currency.uppercase()
        )
    }
}
