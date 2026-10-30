import java.util.Properties
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localSettings = Properties().apply {
    val config = rootProject.file("local.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
fun configString(name: String, default: String = "") =
    (localSettings.getProperty(name) ?: System.getenv(name) ?: default)
        .replace("\\", "\\\\").replace("\"", "\\\"")
val signingFile = rootProject.file(".secrets/signing.properties")
val releaseSettings = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }

// Public Android configuration only; a missing project keeps the offline app usable.
val firebaseFile = rootProject.file(".secrets/firebase/google-services.json")
val firebaseValues = mutableMapOf<String, String>()
if (firebaseFile.exists()) {
    val config = JsonSlurper().parse(firebaseFile) as Map<*, *>
    require("private_key" !in config && config["type"] != "service_account") { "Use Android google-services.json, never a Firebase Admin key." }
    val project = config["project_info"] as? Map<*, *> ?: error("Firebase project_info is missing.")
    val clients = config["client"] as? List<*> ?: error("Firebase Android clients are missing.")
    val client = clients.filterIsInstance<Map<*, *>>().firstOrNull {
        val info = it["client_info"] as? Map<*, *>
        (info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.runmoni.app"
    } ?: error("Firebase configuration must include Android package com.runmoni.app.")
    val info = client["client_info"] as Map<*, *>
    val apiKey = (client["api_key"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull()?.get("current_key") as? String
    val webClient = (client["oauth_client"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }?.get("client_id") as? String
    firebaseValues["FIREBASE_PROJECT_ID"] = project["project_id"] as? String ?: ""
    firebaseValues["FIREBASE_APP_ID"] = info["mobilesdk_app_id"] as? String ?: ""
    firebaseValues["FIREBASE_API_KEY"] = apiKey.orEmpty()
    firebaseValues["FIREBASE_WEB_CLIENT_ID"] = webClient.orEmpty()
    require(firebaseValues.values.all { it.isNotBlank() }) { "Firebase Android configuration is incomplete. Enable Google sign-in and download the updated google-services.json." }
    require(firebaseValues["FIREBASE_PROJECT_ID"] == "runmoni-3c08e") { "Expected Firebase project runmoni-3c08e. Download this project's Android configuration." }
    require(firebaseValues["FIREBASE_PROJECT_ID"]!!.matches(Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]"))) { "Firebase project_id format is invalid." }
    require(firebaseValues["FIREBASE_APP_ID"]!!.matches(Regex("1:[0-9]+:android:[A-Za-z0-9]+"))) { "Firebase Android application ID format is invalid." }
    require(firebaseValues["FIREBASE_API_KEY"]!!.matches(Regex("[A-Za-z0-9_-]{20,200}"))) { "Firebase Android API key format is invalid." }
    require(firebaseValues["FIREBASE_WEB_CLIENT_ID"]!!.matches(Regex("[0-9]+-[A-Za-z0-9]+\\.apps\\.googleusercontent\\.com"))) { "Enable Google sign-in and download the updated Android configuration." }
}

android {
    namespace = "com.runmoni.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.runmoni.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "0.1.8-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PILOT_API_URL", "\"${configString("RUNMONI_API_URL", "http://127.0.0.1:8787")}\"")
        buildConfigField("String", "NAVER_MAP_CLIENT_ID", "\"${configString("NAVER_MAP_CLIENT_ID")}\"")
        buildConfigField("boolean", "FIREBASE_ENABLED", firebaseFile.exists().toString())
        for (name in listOf("FIREBASE_PROJECT_ID", "FIREBASE_APP_ID", "FIREBASE_API_KEY", "FIREBASE_WEB_CLIENT_ID")) {
            val value = firebaseValues[name].orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", name, "\"$value\"")
        }
        manifestPlaceholders["naverMapClientId"] = configString("NAVER_MAP_CLIENT_ID")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (signingFile.exists()) create("runmoniRelease") {
            storeFile = rootProject.file(releaseSettings.getProperty("storeFile"))
            storePassword = releaseSettings.getProperty("storePassword")
            keyAlias = releaseSettings.getProperty("keyAlias")
            keyPassword = releaseSettings.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (signingFile.exists()) signingConfig = signingConfigs.getByName("runmoniRelease")
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    implementation("androidx.compose.foundation:foundation:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // Pin the compatible baseline while retaining the existing Kotlin 2.0 toolchain.
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.livekit:livekit-android:2.29.0")
    implementation("com.naver.maps:map-sdk:3.24.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.0")
}
