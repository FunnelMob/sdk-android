package com.funnelmob.sdk

import com.funnelmob.sdk.internal.toEventRevenue
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class RevenueTest {

    @Test
    fun `usd creates correct revenue`() {
        val revenue = FunnelMobRevenue.usd(29.99)

        assertEquals(BigDecimal.valueOf(29.99), revenue.amount)
        assertEquals("USD", revenue.currency)
    }

    @Test
    fun `currency is normalized to uppercase`() {
        val revenue = FunnelMobRevenue.of(10.0, "eur")

        assertEquals("EUR", revenue.currency)
    }

    @Test
    fun `toEventRevenue formats amount correctly`() {
        val revenue = FunnelMobRevenue.usd(100.0)
        val eventRevenue = revenue.toEventRevenue()

        assertEquals("100.00", eventRevenue.amount)
        assertEquals("USD", eventRevenue.currency)
    }

    @Test
    fun `toEventRevenue rounds correctly`() {
        val revenue = FunnelMobRevenue.usd(29.999)
        val eventRevenue = revenue.toEventRevenue()

        assertEquals("30.00", eventRevenue.amount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid currency code throws exception`() {
        FunnelMobRevenue.of(10.0, "DOLLARS")
    }
}
