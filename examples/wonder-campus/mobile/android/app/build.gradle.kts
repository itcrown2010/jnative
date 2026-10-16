// Author: 张晓达
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.jnative.examples.wondercampus"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.jnative.examples.wondercampus"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // Emulator → host loopback for wonder-server on 18880
        buildConfigField("String", "API_BASE", "\"http://10.0.2.2:18880\"")
        buildConfigField("boolean", "ALLOW_DEMO_LOGIN", "true")
        buildConfigField("String", "WC_ENV", "\"dev\"")
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            buildConfigField("String", "API_BASE", "\"http://10.0.2.2:18880\"")
            buildConfigField("boolean", "ALLOW_DEMO_LOGIN", "true")
            buildConfigField("String", "WC_ENV", "\"dev\"")
        }
        release {
            isMinifyEnabled = false
            // Production: no demo login; replace host before store release.
            buildConfigField("String", "API_BASE", "\"https://api.example.invalid\"")
            buildConfigField("boolean", "ALLOW_DEMO_LOGIN", "false")
            buildConfigField("String", "WC_ENV", "\"prod\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("com.google.android.material:material:1.12.0")
}
