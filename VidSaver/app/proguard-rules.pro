# VidSaver R8 configuration.
# Rules are added per phase as each SDK lands; keep them grouped and commented.

# --- Kotlin / coroutines -----------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- kotlinx.serialization ---------------------------------------------------
# Keep the generated serializers for every @Serializable model. Without this the
# app config fails to parse in release builds only.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ampgames.vidsaver.data.config.** {
    *** Companion;
}
-keepclasseswithmembers class com.ampgames.vidsaver.data.config.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ampgames.vidsaver.data.config.**$$serializer { *; }

# --- Hilt / Dagger -----------------------------------------------------------
# Hilt ships its own consumer rules; these only cover our entry points.
-keep class com.ampgames.vidsaver.VidSaverApplication { *; }

# --- Timber ------------------------------------------------------------------
-dontwarn org.jetbrains.annotations.**

# --- WebView JavaScript bridge (Phase 2) -------------------------------------
# video_sniffer.js calls these by name; without this the bridge silently stops
# working in release builds only.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.ampgames.vidsaver.data.browser.sniffer.VideoSnifferBridge { *; }

# The DOM payload is deserialized from the bridge, so it needs serializers too.
-keepclassmembers class com.ampgames.vidsaver.data.browser.sniffer.** {
    *** Companion;
}
-keepclasseswithmembers class com.ampgames.vidsaver.data.browser.sniffer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ampgames.vidsaver.data.browser.sniffer.**$$serializer { *; }

# --- OkHttp / Okio (Phase 2) -------------------------------------------------
# OkHttp ships consumer rules; these silence the optional-dependency warnings.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**

# --- Room (Phase 2) ----------------------------------------------------------
# Room generates implementations that are looked up reflectively by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Media3 (Phase 2 remuxing, Phase 4 playback) ------------------------------
-dontwarn androidx.media3.**
-keep class androidx.media3.common.** { *; }

# --- Coil --------------------------------------------------------------------
-dontwarn coil.**

# --- RevenueCat (Phase 5) ----------------------------------------------------
# The purchases AAR ships consumer rules (Parcelables, enums, Billing). Nothing
# extra is needed; kept explicit so a future minify failure is easy to place.
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# The AppLovin MAX adapters (Phase 6) add their rules here as they are integrated.
