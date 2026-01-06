# FunnelMob Android SDK

A Mobile Measurement Partner (MMP) SDK for attributing app installs to advertising campaigns.

## Installation

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.funnelmob:sdk:0.1.0")
}
```

Or if using Groovy `build.gradle`:

```groovy
dependencies {
    implementation 'com.funnelmob:sdk:0.1.0'
}
```

## Quick Start

```kotlin
import com.funnelmob.sdk.FunnelMob
import com.funnelmob.sdk.FunnelMobConfiguration

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure the SDK
        val config = FunnelMobConfiguration.Builder(
            appId = "com.example.myapp",
            apiKey = "fm_live_abc123"
        ).build()

        // Initialize
        FunnelMob.initialize(this, config)
    }
}

// Track events anywhere in your app
FunnelMob.trackEvent("button_click")

// Flush before app closes (optional)
FunnelMob.flush()
```

## Configuration

### Using Builder (Recommended)

```kotlin
import com.funnelmob.sdk.FunnelMobConfiguration
import com.funnelmob.sdk.FunnelMobConfiguration.Environment
import com.funnelmob.sdk.FunnelMobConfiguration.LogLevel

val config = FunnelMobConfiguration.Builder(
    appId = "com.example.myapp",       // Required: Your app identifier
    apiKey = "fm_live_abc123"          // Required: Your API key
)
    .environment(Environment.PRODUCTION)  // Optional: PRODUCTION (default) or SANDBOX
    .logLevel(LogLevel.NONE)              // Optional: NONE, ERROR, WARNING, INFO, DEBUG, VERBOSE
    .flushInterval(30_000L)               // Optional: Auto-flush interval in ms (min: 1000, default: 30000)
    .maxBatchSize(100)                    // Optional: Events per batch (1-100, default: 100)
    .build()
```

### Using Data Class (Kotlin)

```kotlin
val config = FunnelMobConfiguration(
    appId = "com.example.myapp",
    apiKey = "fm_live_abc123",
    environment = Environment.PRODUCTION,
    logLevel = LogLevel.NONE,
    flushIntervalMs = 30_000L,
    maxBatchSize = 100
)
```

### Environment Options

| Environment | Base URL |
|-------------|----------|
| `Environment.PRODUCTION` | `https://api.funnelmob.com/v1` |
| `Environment.SANDBOX` | `https://sandbox.funnelmob.com/v1` |

## Event Tracking

### Simple Events

```kotlin
FunnelMob.trackEvent("level_complete")
```

### Events with Revenue

```kotlin
import com.funnelmob.sdk.FunnelMobRevenue

val revenue = FunnelMobRevenue.usd(29.99)
FunnelMob.trackEvent("purchase", revenue)

// Other currencies
val eur = FunnelMobRevenue.eur(19.99)
val gbp = FunnelMobRevenue.gbp(14.99)
val jpy = FunnelMobRevenue.of(2000.0, "JPY")

// Using BigDecimal for precision
val precise = FunnelMobRevenue.usd(BigDecimal("99.99"))
```

### Events with Parameters

```kotlin
import com.funnelmob.sdk.FunnelMobEventParameters

// Using Builder
val params = FunnelMobEventParameters.Builder()
    .set("item_id", "sku_123")
    .set("quantity", 2)
    .set("price", 29.99)
    .set("is_gift", false)
    .build()

FunnelMob.trackEvent("add_to_cart", params)

// Using DSL (Kotlin)
val params = FunnelMobEventParameters.build {
    set("item_id", "sku_123")
    set("quantity", 2)
    set("price", 29.99)
}

// From a Map
val params = FunnelMobEventParameters.fromMap(mapOf(
    "item_id" to "sku_123",
    "quantity" to 2
))
```

### Events with Revenue and Parameters

```kotlin
val revenue = FunnelMobRevenue.usd(99.00)
val params = FunnelMobEventParameters.build {
    set("plan", "annual")
    set("trial_days", 7)
}

FunnelMob.trackEvent("subscribe", revenue, params)
```

## Standard Events

Use predefined event names for consistent analytics:

```kotlin
import com.funnelmob.sdk.FunnelMobStandardEvents

FunnelMob.trackEvent(FunnelMobStandardEvents.REGISTRATION)
FunnelMob.trackEvent(FunnelMobStandardEvents.LOGIN)
FunnelMob.trackEvent(FunnelMobStandardEvents.PURCHASE)
FunnelMob.trackEvent(FunnelMobStandardEvents.SUBSCRIBE)
FunnelMob.trackEvent(FunnelMobStandardEvents.TUTORIAL_COMPLETE)
FunnelMob.trackEvent(FunnelMobStandardEvents.LEVEL_COMPLETE)
FunnelMob.trackEvent(FunnelMobStandardEvents.ADD_TO_CART)
FunnelMob.trackEvent(FunnelMobStandardEvents.CHECKOUT)
```

| Event | Constant | Value |
|-------|----------|-------|
| Registration | `FunnelMobStandardEvents.REGISTRATION` | `fm_registration` |
| Login | `FunnelMobStandardEvents.LOGIN` | `fm_login` |
| Purchase | `FunnelMobStandardEvents.PURCHASE` | `fm_purchase` |
| Subscribe | `FunnelMobStandardEvents.SUBSCRIBE` | `fm_subscribe` |
| Tutorial Complete | `FunnelMobStandardEvents.TUTORIAL_COMPLETE` | `fm_tutorial_complete` |
| Level Complete | `FunnelMobStandardEvents.LEVEL_COMPLETE` | `fm_level_complete` |
| Add to Cart | `FunnelMobStandardEvents.ADD_TO_CART` | `fm_add_to_cart` |
| Checkout | `FunnelMobStandardEvents.CHECKOUT` | `fm_checkout` |

## SDK Control

```kotlin
// Disable tracking (e.g., for GDPR compliance)
FunnelMob.setEnabled(false)

// Re-enable tracking
FunnelMob.setEnabled(true)

// Force send queued events immediately
FunnelMob.flush()
```

## Java Interoperability

All public methods have `@JvmStatic` annotations for seamless Java usage:

```java
import com.funnelmob.sdk.FunnelMob;
import com.funnelmob.sdk.FunnelMobConfiguration;
import com.funnelmob.sdk.FunnelMobRevenue;
import com.funnelmob.sdk.FunnelMobEventParameters;

// Initialize
FunnelMobConfiguration config = new FunnelMobConfiguration.Builder(
    "com.example.myapp",
    "fm_live_abc123"
).build();

FunnelMob.initialize(context, config);

// Track events
FunnelMob.trackEvent("button_click");

// With revenue
FunnelMobRevenue revenue = FunnelMobRevenue.usd(29.99);
FunnelMob.trackEvent("purchase", revenue);

// With parameters
FunnelMobEventParameters params = new FunnelMobEventParameters.Builder()
    .set("item_id", "sku_123")
    .set("quantity", 2)
    .build();
FunnelMob.trackEvent("add_to_cart", params);
```

## Validation

### Event Names

- Must not be empty
- Maximum 100 characters
- Must match pattern: `^[a-zA-Z][a-zA-Z0-9_]*$`
  - Must start with a letter
  - Can contain letters, numbers, and underscores
  - No hyphens, spaces, or special characters

```kotlin
// Valid
FunnelMob.trackEvent("purchase")           // OK
FunnelMob.trackEvent("level_2_complete")   // OK
FunnelMob.trackEvent("buttonClick")        // OK

// Invalid (logged as errors)
FunnelMob.trackEvent("2nd_level")          // Starts with number
FunnelMob.trackEvent("my-event")           // Contains hyphen
FunnelMob.trackEvent("")                   // Empty
```

### Currency

- Must be a 3-letter ISO 4217 code
- Automatically converted to uppercase with `FunnelMobRevenue.of()`

```kotlin
// Valid
FunnelMobRevenue.usd(29.99)
FunnelMobRevenue.of(29.99, "jpy")  // Converted to "JPY"

// Invalid (throws IllegalArgumentException)
FunnelMobRevenue.of(29.99, "US")      // Too short
FunnelMobRevenue.of(29.99, "USDD")    // Too long
```

## Error Handling

The SDK throws `IllegalStateException` if used before initialization:

```kotlin
try {
    FunnelMob.trackEvent("test")
} catch (e: IllegalStateException) {
    Log.e("FunnelMob", "SDK not initialized: ${e.message}")
}
```

Validation errors are logged. Set `logLevel` to see them:

```kotlin
val config = FunnelMobConfiguration.Builder(appId, apiKey)
    .logLevel(LogLevel.DEBUG)  // See validation errors in logcat
    .build()
```

## ProGuard

If you're using ProGuard or R8, the SDK's `consumer-rules.pro` is automatically applied. No additional configuration is needed.

## Requirements

- Android API 21+ (Android 5.0 Lollipop)
- Kotlin 1.9+ / Java 17+
- AndroidX

## License

MIT
