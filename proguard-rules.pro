# FunnelMob SDK ProGuard Rules

# Keep public API
-keep class com.funnelmob.sdk.FunnelMob { *; }
-keep class com.funnelmob.sdk.FunnelMobConfiguration { *; }
-keep class com.funnelmob.sdk.FunnelMobConfiguration$* { *; }
-keep class com.funnelmob.sdk.FunnelMobEventParameters { *; }
-keep class com.funnelmob.sdk.FunnelMobEventParameters$* { *; }
-keep class com.funnelmob.sdk.FunnelMobRevenue { *; }
-keep class com.funnelmob.sdk.FunnelMobStandardEvents { *; }

# Keep internal classes that are serialized
-keep class com.funnelmob.sdk.internal.Event { *; }
-keep class com.funnelmob.sdk.internal.EventRevenue { *; }
-keep class com.funnelmob.sdk.internal.DeviceContext { *; }
