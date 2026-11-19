plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.resimai"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.resimai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.mediapipe:tasks-vision-image-generator:0.10.21")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
