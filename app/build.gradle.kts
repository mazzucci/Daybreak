plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "app.daybreak"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.daybreak"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // MediaPipe's LLM engine is ~20-30 MB per ABI. 64-bit ARM covers current phones and x86_64 covers
        // emulators; 32-bit devices are too weak to run Gemma usefully anyway.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // On-device Gemma via MediaPipe LLM Inference. The model file itself is imported by the user at runtime.
    implementation("com.google.mediapipe:tasks-genai:0.10.35")
    // Home-screen widget (Jetpack Glance) and its periodic refresh.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.glance:glance-appwidget-testing:1.1.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // android.jar's org.json is a stub in JVM unit tests; use the real implementation there.
    testImplementation("org.json:json:20250517")
}
