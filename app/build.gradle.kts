plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.batterypercent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.batterypercent"
        minSdk = 26
        targetSdk = 33
        versionCode = 4
        versionName = "3.1"
    }

    buildTypes {
        debug { isDebuggable = true }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
