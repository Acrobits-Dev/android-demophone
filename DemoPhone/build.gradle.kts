import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services") apply false
}

// Load license information from gradle.properties
@Suppress("PropertyName")
val acrobits_saas_package: String by project
@Suppress("PropertyName")
val acrobits_saas_key: String by project

android {
    compileSdk = 35
    namespace = "cz.acrobits.demophone.android"

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        applicationId = acrobits_saas_package
        versionCode = 1
        versionName = "DEMO"

        buildConfigField("String", "SAAS_IDENTIFIER", "\"${acrobits_saas_key}\"") 
    }

    packaging { 
        jniLibs {
            // Packages native libraries uncompressed to increase startup time
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Required by all versions of LibSoftphone
    coreLibraryDesugaring(libs.androidx.desugaring)

    // Choose which Softphone library to include:

    // For non-video calls
    // implementation(libs.libsoftphone.novideo)

    // For video calls
    implementation(libs.libsoftphone.video)

    // AssetRequest support
    implementation(libs.libsoftphone.assets)
    implementation(libs.glide)

    // Firebase Cloud Messaging support (for push notifications)
    implementation(enforcedPlatform(libs.google.firebase.bom))
    implementation(libs.google.firebase.messaging)

    // Kotlin support
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)

    // General goodies
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
}

if (project.layout.projectDirectory.file("google-services.json").asFile.exists())
    project.plugins.apply("com.google.gms.google-services")
