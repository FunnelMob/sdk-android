package com.funnelmob.sdk

import org.junit.Assert.*
import org.junit.Test

class EventValidationTest {

    // MARK: - Valid Event Names

    @Test
    fun `valid event names are accepted`() {
        assertTrue(isValidEventName("purchase"))
        assertTrue(isValidEventName("button_click"))
        assertTrue(isValidEventName("level1Complete"))
        assertTrue(isValidEventName("step2_complete"))
        assertTrue(isValidEventName("a"))
        assertTrue(isValidEventName("fm_registration"))
    }

    // MARK: - Invalid Event Names

    @Test
    fun `empty event name is rejected`() {
        assertFalse(isValidEventName(""))
    }

    @Test
    fun `event name starting with number is rejected`() {
        assertFalse(isValidEventName("2nd_purchase"))
        assertFalse(isValidEventName("123"))
    }

    @Test
    fun `event name with special chars is rejected`() {
        assertFalse(isValidEventName("purchase-complete"))
        assertFalse(isValidEventName("purchase.complete"))
        assertFalse(isValidEventName("purchase@home"))
        assertFalse(isValidEventName("purchase#1"))
    }

    @Test
    fun `event name with spaces is rejected`() {
        assertFalse(isValidEventName("purchase complete"))
        assertFalse(isValidEventName(" purchase"))
        assertFalse(isValidEventName("purchase "))
    }

    @Test
    fun `event name too long is rejected`() {
        val longName = "a".repeat(101)
        assertFalse(isValidEventName(longName))
    }

    @Test
    fun `event name at max length is accepted`() {
        val maxName = "a".repeat(100)
        assertTrue(isValidEventName(maxName))
    }

    // Helper function matching SDK validation
    private fun isValidEventName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name.length > 100) return false

        val pattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")
        return pattern.matches(name)
    }
}
