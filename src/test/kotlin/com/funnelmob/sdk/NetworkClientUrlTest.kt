package com.funnelmob.sdk

import com.funnelmob.sdk.internal.NetworkClient.Companion.DEFAULT_BASE_URL
import com.funnelmob.sdk.internal.NetworkClient.Companion.baseUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkClientUrlTest {

    @Test
    fun `default base URL is production with v1 appended`() {
        val config = FunnelMobConfiguration.Builder("fm_test_key").build()

        assertEquals("https://api.funnelmob.com", DEFAULT_BASE_URL)
        assertEquals("https://api.funnelmob.com/v1", baseUrl(config))
    }

    @Test
    fun `customUrl overrides default base URL`() {
        val config = FunnelMobConfiguration.Builder("fm_test_key")
            .customUrl("http://10.0.2.2:3080")
            .build()

        assertEquals("http://10.0.2.2:3080/v1", baseUrl(config))
    }

    @Test
    fun `customUrl trailing slash does not produce double slash`() {
        val config = FunnelMobConfiguration.Builder("fm_test_key")
            .customUrl("http://localhost:3080/")
            .build()

        assertEquals("http://localhost:3080/v1", baseUrl(config))
    }
}
