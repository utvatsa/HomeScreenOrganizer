import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("signing/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val customStorePath = System.getenv("HOMESTUDIO_KEYSTORE_PATH")
    ?: keystoreProps.getProperty("storeFile")
val customStoreFile = customStorePath?.let { rootProject.file(it.removePrefix("../")) }
val hasCustomSigning = customStoreFile != null && customStoreFile.exists() &&
    (System.getenv("HOMESTUDIO_STORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")) != null

android {
    namespace = "com.homestudio.local"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.homestudio.local"
        minSdk = 31
        targetSdk = 36
        versionCode = 8
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCustomSigning) {
            create("localKey") {
                storeFile = customStoreFile
                storePassword = System.getenv("HOMESTUDIO_STORE_PASSWORD")
                    ?: keystoreProps.getProperty("storePassword")
                keyAlias = System.getenv("HOMESTUDIO_KEY_ALIAS")
                    ?: keystoreProps.getProperty("keyAlias")
                keyPassword = System.getenv("HOMESTUDIO_KEY_PASSWORD")
                    ?: keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasCustomSigning) {
                signingConfig = signingConfigs.getByName("localKey")
            }
        }
        release {
            if (hasCustomSigning) {
                signingConfig = signingConfigs.getByName("localKey")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3:1.5.0-rc01")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
