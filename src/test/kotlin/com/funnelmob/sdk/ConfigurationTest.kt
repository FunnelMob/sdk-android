package com.funnelmob.sdk

import org.junit.Assert.*
import org.junit.Test

class ConfigurationTest {

    @Test
    fun `configuration has correct defaults`() {
        val config = FunnelMobConfiguration(
            apiKey = "fm_test_key"
        )

        assertEquals("fm_test_key", config.apiKey)
        assertEquals(FunnelMobConfiguration.LogLevel.NONE, config.logLevel)
        assertEquals(30_000L, config.flushIntervalMs)
        assertEquals(100, config.maxBatchSize)
        assertNull(config.customUrl)
    }

    @Test
    fun `customUrl is null by default`() {
        val config = FunnelMobConfiguration.Builder("fm_test_key").build()
        assertNull(config.customUrl)
    }

    @Test
    fun `customUrl override is honored`() {
        val config = FunnelMobConfiguration.Builder("fm_test_key")
            .customUrl("http://10.0.2.2:3080")
            .build()

        assertEquals("http://10.0.2.2:3080", config.customUrl)
    }

    @Test
    fun `customUrl trailing slash is trimmed`() {
        val config = FunnelMobConfiguration.Builder("fm_test_key")
            .customUrl("http://localhost:3080/")
            .build()

        assertEquals("http://localhost:3080", config.customUrl)
    }

    @Test
    fun `customUrl null clears any previous value`() {
        val config = FunnelMobConfiguration.Builder("fm_test_key")
            .customUrl("http://localhost:3080")
            .customUrl(null)
            .build()

        assertNull(config.customUrl)
    }

    @Test
    fun `builder sets all properties`() {
        val config = FunnelMobConfiguration.Builder(
            apiKey = "fm_test_key"
        )
            .logLevel(FunnelMobConfiguration.LogLevel.DEBUG)
            .flushInterval(10_000L)
            .maxBatchSize(50)
            .build()

        assertEquals(FunnelMobConfiguration.LogLevel.DEBUG, config.logLevel)
        assertEquals(10_000L, config.flushIntervalMs)
        assertEquals(50, config.maxBatchSize)
    }

    @Test
    fun `flush interval minimum is enforced`() {
        val config = FunnelMobConfiguration.Builder(
            apiKey = "fm_test_key"
        )
            .flushInterval(500L) // Below minimum
            .build()

        assertEquals(1000L, config.flushIntervalMs)
    }

    @Test
    fun `max batch size is clamped`() {
        val configLow = FunnelMobConfiguration.Builder(
            apiKey = "fm_test_key"
        )
            .maxBatchSize(0)
            .build()

        val configHigh = FunnelMobConfiguration.Builder(
            apiKey = "fm_test_key"
        )
            .maxBatchSize(200)
            .build()

        assertEquals(1, configLow.maxBatchSize)
        assertEquals(100, configHigh.maxBatchSize)
    }
}
