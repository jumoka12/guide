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

# --- Phase 2+ placeholders ---------------------------------------------------
# WebView JavaScript bridge (Phase 2): the @JavascriptInterface methods are
# called by name from injected JS and must survive shrinking.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Room (Phase 3), Media3 (Phase 4), RevenueCat (Phase 5) and the AppLovin MAX
# adapters (Phase 6) each add their rules here as they are integrated.
