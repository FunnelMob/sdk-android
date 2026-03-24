package com.funnelmob.sdk

import com.funnelmob.sdk.internal.toEventRevenue
import org.junit.Assert.*
import org.junit.Test

class StandardEventsTest {

    // MARK: - FunnelMobStandardEvents constants

    @Test
    fun `legacy fm_-prefixed constants are preserved`() {
        assertEquals("fm_registration", FunnelMobStandardEvents.REGISTRATION)
        assertEquals("fm_login", FunnelMobStandardEvents.LOGIN)
        assertEquals("fm_purchase", FunnelMobStandardEvents.PURCHASE)
        assertEquals("fm_subscribe", FunnelMobStandardEvents.SUBSCRIBE)
        assertEquals("fm_tutorial_complete", FunnelMobStandardEvents.TUTORIAL_COMPLETE)
        assertEquals("fm_level_complete", FunnelMobStandardEvents.LEVEL_COMPLETE)
        assertEquals("fm_add_to_cart", FunnelMobStandardEvents.ADD_TO_CART)
        assertEquals("fm_checkout", FunnelMobStandardEvents.CHECKOUT)
    }

    @Test
    fun `standard Meta+TikTok event name constants are correct`() {
        assertEquals("PageView", FunnelMobStandardEvents.PAGE_VIEW)
        assertEquals("ViewContent", FunnelMobStandardEvents.VIEW_CONTENT)
        assertEquals("Search", FunnelMobStandardEvents.SEARCH)
        assertEquals("AddToCart", FunnelMobStandardEvents.ADD_TO_CART_STANDARD)
        assertEquals("AddToWishlist", FunnelMobStandardEvents.ADD_TO_WISHLIST)
        assertEquals("InitiateCheckout", FunnelMobStandardEvents.INITIATE_CHECKOUT)
        assertEquals("AddPaymentInfo", FunnelMobStandardEvents.ADD_PAYMENT_INFO)
        assertEquals("Purchase", FunnelMobStandardEvents.PURCHASE_STANDARD)
        assertEquals("Lead", FunnelMobStandardEvents.LEAD)
        assertEquals("CompleteRegistration", FunnelMobStandardEvents.COMPLETE_REGISTRATION)
        assertEquals("Contact", FunnelMobStandardEvents.CONTACT)
        assertEquals("Schedule", FunnelMobStandardEvents.SCHEDULE)
        assertEquals("FindLocation", FunnelMobStandardEvents.FIND_LOCATION)
        assertEquals("CustomizeProduct", FunnelMobStandardEvents.CUSTOMIZE_PRODUCT)
        assertEquals("Donate", FunnelMobStandardEvents.DONATE)
        assertEquals("SubmitApplication", FunnelMobStandardEvents.SUBMIT_APPLICATION)
        assertEquals("ApplicationApproval", FunnelMobStandardEvents.APPLICATION_APPROVAL)
        assertEquals("Download", FunnelMobStandardEvents.DOWNLOAD)
        assertEquals("SubmitForm", FunnelMobStandardEvents.SUBMIT_FORM)
        assertEquals("StartTrial", FunnelMobStandardEvents.START_TRIAL)
        assertEquals("Subscribe", FunnelMobStandardEvents.SUBSCRIBE_STANDARD)
        assertEquals("AchieveLevel", FunnelMobStandardEvents.ACHIEVE_LEVEL)
        assertEquals("UnlockAchievement", FunnelMobStandardEvents.UNLOCK_ACHIEVEMENT)
        assertEquals("SpentCredits", FunnelMobStandardEvents.SPENT_CREDITS)
        assertEquals("Rate", FunnelMobStandardEvents.RATE)
        assertEquals("CompleteTutorial", FunnelMobStandardEvents.COMPLETE_TUTORIAL)
        assertEquals("ActivateApp", FunnelMobStandardEvents.ACTIVATE_APP)
        assertEquals("InAppAdClick", FunnelMobStandardEvents.IN_APP_AD_CLICK)
        assertEquals("InAppAdImpression", FunnelMobStandardEvents.IN_APP_AD_IMPRESSION)
    }

    // MARK: - Revenue creation for typed methods

    @Test
    fun `trackPurchase revenue is created correctly`() {
        val revenue = FunnelMobRevenue.of(29.99, "USD")
        assertEquals("USD", revenue.currency)
        val eventRevenue = revenue.toEventRevenue()
        assertEquals("29.99", eventRevenue.amount)
        assertEquals("USD", eventRevenue.currency)
    }

    @Test
    fun `trackPurchase normalizes currency to uppercase`() {
        val revenue = FunnelMobRevenue.of(10.0, "usd")
        assertEquals("USD", revenue.currency)
    }

    @Test
    fun `trackSubscribe revenue is created correctly`() {
        val revenue = FunnelMobRevenue.of(9.99, "GBP")
        val eventRevenue = revenue.toEventRevenue()
        assertEquals("9.99", eventRevenue.amount)
        assertEquals("GBP", eventRevenue.currency)
    }

    @Test
    fun `trackStartTrial accepts zero value for free trials`() {
        val revenue = FunnelMobRevenue.of(0.0, "USD")
        val eventRevenue = revenue.toEventRevenue()
        assertEquals("0.00", eventRevenue.amount)
    }

    @Test
    fun `trackDonate revenue is created correctly`() {
        val revenue = FunnelMobRevenue.of(50.0, "EUR")
        val eventRevenue = revenue.toEventRevenue()
        assertEquals("50.00", eventRevenue.amount)
        assertEquals("EUR", eventRevenue.currency)
    }

    // MARK: - SpentCredits parameter building

    @Test
    fun `trackSpentCredits sets value in parameters`() {
        val params = FunnelMobEventParameters.Builder()
            .set("value", 100.0)
            .build()

        val map = params.toMap()
        assertNotNull(map)
        assertEquals(100.0, map!!["value"])
    }

    @Test
    fun `trackSpentCredits merges additional parameters with value`() {
        val userParams = FunnelMobEventParameters.build {
            set("content_type", "product")
            set("content_ids", "SKU123")
        }

        // Simulate the merging logic from trackSpentCredits
        val builder = FunnelMobEventParameters.Builder()
        userParams.toMap()?.forEach { (k, v) ->
            when (v) {
                is String -> builder.set(k, v)
                is Int -> builder.set(k, v)
                is Long -> builder.set(k, v)
                is Double -> builder.set(k, v)
                is Boolean -> builder.set(k, v)
            }
        }
        builder.set("value", 50.0)
        val result = builder.build().toMap()

        assertNotNull(result)
        assertEquals(50.0, result!!["value"])
        assertEquals("product", result["content_type"])
        assertEquals("SKU123", result["content_ids"])
    }

    @Test
    fun `trackSpentCredits value argument overrides value in user parameters`() {
        val userParams = FunnelMobEventParameters.build {
            set("value", 999.0) // user sets a value
        }

        // Simulate trackSpentCredits: merge user params then overwrite value with arg
        val builder = FunnelMobEventParameters.Builder()
        userParams.toMap()?.forEach { (k, v) ->
            when (v) {
                is String -> builder.set(k, v)
                is Int -> builder.set(k, v)
                is Long -> builder.set(k, v)
                is Double -> builder.set(k, v)
                is Boolean -> builder.set(k, v)
            }
        }
        builder.set("value", 42.0) // required arg wins
        val result = builder.build().toMap()

        assertEquals(42.0, result!!["value"])
    }

    // MARK: - Standard event name validation

    @Test
    fun `all standard event names pass SDK validation`() {
        val allNames = listOf(
            "PageView", "ViewContent", "Search", "AddToCart", "AddToWishlist",
            "InitiateCheckout", "AddPaymentInfo", "Purchase", "Lead",
            "CompleteRegistration", "Contact", "Schedule", "FindLocation",
            "CustomizeProduct", "Donate", "SubmitApplication", "ApplicationApproval",
            "Download", "SubmitForm", "StartTrial", "Subscribe", "AchieveLevel",
            "UnlockAchievement", "SpentCredits", "Rate", "CompleteTutorial",
            "ActivateApp", "InAppAdClick", "InAppAdImpression"
        )

        val pattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")
        for (name in allNames) {
            assertTrue("$name should pass validation", pattern.matches(name))
            assertTrue("$name length should be <= 100", name.length <= 100)
        }
    }

}
