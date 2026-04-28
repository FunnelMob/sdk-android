package com.funnelmob.sdk

/**
 * Per-user consent state for GDPR / DMA compliance.
 *
 * Pass to [FunnelMob.setConsent] to inform the SDK and backend of the
 * user's consent decisions. The four fields mirror Google Consent Mode v2
 * and AppsFlyer's `AppsFlyerConsent` so values map 1:1 to ad network
 * requirements.
 *
 * When [isUserSubjectToGDPR] is `true` and [hasConsentForDataUsage] is
 * `false`, the SDK stops dispatching new events and clears any pending
 * queue. When [isUserSubjectToGDPR] is `false`, the per-dimension fields
 * are advisory only; the SDK tracks normally.
 */
data class FunnelMobConsent(
    /**
     * Whether the user is subject to GDPR (typically true for EEA users).
     * When `false`, the SDK ignores the per-dimension flags and tracks
     * normally.
     */
    val isUserSubjectToGDPR: Boolean,

    /**
     * Whether the user granted consent for data usage (analytics,
     * attribution). When `false` and [isUserSubjectToGDPR] is `true`, the
     * SDK stops sending events and clears the local queue.
     */
    val hasConsentForDataUsage: Boolean? = null,

    /**
     * Whether the user granted consent for personalized ads. Forwarded
     * to ad networks (Google `ad_personalization`). Does not gate
     * dispatch — the network decides what to do.
     */
    val hasConsentForAdsPersonalization: Boolean? = null,

    /**
     * Whether the user granted consent for ad-related storage (cookies,
     * GAID-style identifiers used for ads). Forwarded to ad networks
     * (Google `ad_storage`). Does not gate dispatch.
     */
    val hasConsentForAdStorage: Boolean? = null,
) {
    /**
     * True when the SDK must stop dispatching: GDPR applies and the user
     * has affirmatively denied data-usage consent. A `null` data-usage
     * flag is treated as "not yet answered" and does not block dispatch
     * (matches the SDK's "track-by-default unless told otherwise" model).
     */
    internal val blocksDispatch: Boolean
        get() = isUserSubjectToGDPR && hasConsentForDataUsage == false
}
