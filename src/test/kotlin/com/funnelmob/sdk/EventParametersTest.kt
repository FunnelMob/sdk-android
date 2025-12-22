package com.funnelmob.sdk

import org.junit.Assert.*
import org.junit.Test

class EventParametersTest {

    @Test
    fun `empty parameters returns null map`() {
        val params = FunnelMobEventParameters.empty()

        assertNull(params.toMap())
    }

    @Test
    fun `builder sets all parameter types`() {
        val params = FunnelMobEventParameters.Builder()
            .set("string_key", "hello")
            .set("int_key", 42)
            .set("long_key", 100L)
            .set("double_key", 3.14)
            .set("bool_key", true)
            .build()

        val map = params.toMap()
        assertNotNull(map)
        assertEquals("hello", map!!["string_key"])
        assertEquals(42, map["int_key"])
        assertEquals(100L, map["long_key"])
        assertEquals(3.14, map["double_key"])
        assertEquals(true, map["bool_key"])
    }

    @Test
    fun `dsl builder works correctly`() {
        val params = FunnelMobEventParameters.build {
            set("item_id", "sku_123")
            set("quantity", 2)
        }

        val map = params.toMap()
        assertNotNull(map)
        assertEquals("sku_123", map!!["item_id"])
        assertEquals(2, map["quantity"])
    }

    @Test
    fun `fromMap filters unsupported types`() {
        val input = mapOf(
            "valid_string" to "hello",
            "valid_int" to 42,
            "invalid_list" to listOf(1, 2, 3),
            "invalid_map" to mapOf("nested" to "value")
        )

        val params = FunnelMobEventParameters.fromMap(input)
        val map = params.toMap()

        assertNotNull(map)
        assertEquals(2, map!!.size)
        assertEquals("hello", map["valid_string"])
        assertEquals(42, map["valid_int"])
    }
}
