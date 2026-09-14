import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Reads a key from a properties file in the project root, falling back to an
 * environment variable and finally to [default]. No real key is ever committed:
 * `local.properties` and `keystore.properties` are git-ignored.
 */
fun secret(fileName: String, key: String, default: String = ""): String {
    val file = rootProject.file(fileName)
    if (file.exists()) {
        val props = Properties().apply { file.inputStream().use { load(it) } }
        props.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return System.getenv(key)?.takeIf { it.isNotBlank() } ?: default
}

val keystoreFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystoreFile.exists() &&
    secret("keystore.properties", "storeFile").isNotBlank()

android {
    namespace = "com.ampgames.vidsaver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ampgames.vidsaver"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.ampgames.vidsaver.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true

        // Phase 5/6 SDK keys. Placeholders only — supply real values in local.properties.
        buildConfigField(
            "String",
            "REVENUECAT_KEY",
            "\"${secret("local.properties", "REVENUECAT_KEY")}\"",
        )
        buildConfigField(
            "String",
            "APPLOVIN_SDK_KEY",
            "\"${secret("local.properties", "APPLOVIN_SDK_KEY")}\"",
        )
        buildConfigField(
            "String",
            "SINGULAR_API_KEY",
            "\"${secret("local.properties", "SINGULAR_API_KEY")}\"",
        )
        buildConfigField(
            "String",
            "SINGULAR_SECRET",
            "\"${secret("local.properties", "SINGULAR_SECRET")}\"",
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(secret("keystore.properties", "storeFile"))
                storePassword = secret("keystore.properties", "storePassword")
                keyAlias = secret("keystore.properties", "keyAlias")
                keyPassword = secret("keystore.properties", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Falls back to unsigned when no keystore.properties is present, so a
            // fresh clone still builds.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
    }

    // APK splits for sideloaded/alternative distribution. Play delivery uses the
    // App Bundle splits configured below.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += "GradleDependency"
    }
}

ksp {
    // Room schemas are checked in so migrations can be diffed in review.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.webkit)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    // Phase 2 uses Transformer to remux downloaded HLS segments into MP4.
    // Playback (Phase 4) adds the ExoPlayer/UI artifacts.
    implementation(libs.media3.transformer)
    implementation(libs.media3.common)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
