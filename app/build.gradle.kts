plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") // Koog tool args in src/test
    id("com.google.devtools.ksp") // generates the @Tool wrappers and the Room DAO
}

// Firebase AI Logic is opt-in: put your own google-services.json next to this file.
// Without it the app runs on the offline DemoModel and needs no keys at all.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "dev.omerdurmaz.adkexpense"
    compileSdk { version = release(37) } // ADK 1.4.0 AARs declare minCompileSdk 37

    defaultConfig {
        applicationId = "dev.omerdurmaz.adkexpense"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // ADK's Android logger calls android.util.Log, which throws in plain JVM unit tests.
    testOptions { unitTests.isReturnDefaultValues = true }

    // ADK pulls in google-auth jars that each ship these two metadata files.
    packaging { resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES") }
}

dependencies {
    // ADK for Kotlin. 1.5.0 exists; see the README before upgrading.
    implementation("com.google.adk:google-adk-kotlin-core:1.5.0")
    ksp("com.google.adk:google-adk-kotlin-processor:1.5.0")
    implementation("com.google.adk:google-adk-kotlin-firebase-android:1.5.0")

    // Firebase AI Logic + App Check (only used when google-services.json is present).
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Expenses live in their own Room database; ADK's RoomSessionService uses the same Room.
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // Koog is only here for the side-by-side AddExpenseTool in src/test; it isn't in the APK.
    testImplementation("ai.koog:koog-agents:1.3.0")
}
