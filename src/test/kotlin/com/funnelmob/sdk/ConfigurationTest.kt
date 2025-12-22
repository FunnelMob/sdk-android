package com.funnelmob.sdk

import org.junit.Assert.*
import org.junit.Test

class ConfigurationTest {

    @Test
    fun `configuration has correct defaults`() {
        val config = FunnelMobConfiguration(
            appId = "com.test.app",
            apiKey = "fm_test_key"
        )

        assertEquals("com.test.app", config.appId)
        assertEquals("fm_test_key", config.apiKey)
        assertEquals(FunnelMobConfiguration.Environment.PRODUCTION, config.environment)
        assertEquals(FunnelMobConfiguration.LogLevel.NONE, config.logLevel)
        assertEquals(30_000L, config.flushIntervalMs)
        assertEquals(100, config.maxBatchSize)
    }

    @Test
    fun `builder sets all properties`() {
        val config = FunnelMobConfiguration.Builder(
            appId = "com.test.app",
            apiKey = "fm_test_key"
        )
            .environment(FunnelMobConfiguration.Environment.SANDBOX)
            .logLevel(FunnelMobConfiguration.LogLevel.DEBUG)
            .flushInterval(10_000L)
            .maxBatchSize(50)
            .build()

        assertEquals(FunnelMobConfiguration.Environment.SANDBOX, config.environment)
        assertEquals(FunnelMobConfiguration.LogLevel.DEBUG, config.logLevel)
        assertEquals(10_000L, config.flushIntervalMs)
        assertEquals(50, config.maxBatchSize)
    }

    @Test
    fun `flush interval minimum is enforced`() {
        val config = FunnelMobConfiguration.Builder(
            appId = "com.test.app",
            apiKey = "fm_test_key"
        )
            .flushInterval(500L) // Below minimum
            .build()

        assertEquals(1000L, config.flushIntervalMs)
    }

    @Test
    fun `max batch size is clamped`() {
        val configLow = FunnelMobConfiguration.Builder(
            appId = "com.test.app",
            apiKey = "fm_test_key"
        )
            .maxBatchSize(0)
            .build()

        val configHigh = FunnelMobConfiguration.Builder(
            appId = "com.test.app",
            apiKey = "fm_test_key"
        )
            .maxBatchSize(200)
            .build()

        assertEquals(1, configLow.maxBatchSize)
        assertEquals(100, configHigh.maxBatchSize)
    }

    @Test
    fun `production environment has correct URL`() {
        val env = FunnelMobConfiguration.Environment.PRODUCTION
        assertEquals("https://api.funnelmob.com/v1", env.baseUrl)
    }

    @Test
    fun `sandbox environment has correct URL`() {
        val env = FunnelMobConfiguration.Environment.SANDBOX
        assertEquals("https://sandbox.funnelmob.com/v1", env.baseUrl)
    }
}
