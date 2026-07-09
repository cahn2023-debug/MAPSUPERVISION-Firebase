import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.util.Properties
import java.io.FileInputStream


android {
    namespace = "com.mapsupervision.data"
    compileSdk = 36

    val envFile = rootProject.file(".env")
    val envProperties = Properties()
    if (envFile.exists()) {
        envProperties.load(FileInputStream(envFile))
    }

    defaultConfig { 
        minSdk = 24 
        val geminiKey = envProperties.getProperty("GEMINI_API_KEY") ?: "AIzaSy_YOUR_API_KEY_HERE"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        val firebaseProjectId = envProperties.getProperty("FIREBASE_PROJECT_ID") ?: ""
        val firebaseAppId = envProperties.getProperty("FIREBASE_APP_ID") ?: ""
        val firebaseApiKey = envProperties.getProperty("FIREBASE_API_KEY") ?: ""
        val firebaseStorageBucket = envProperties.getProperty("FIREBASE_STORAGE_BUCKET") ?: ""
        val firebaseAuthDomain = envProperties.getProperty("FIREBASE_AUTH_DOMAIN") ?: ""
        val mediaUploadBaseUrl = envProperties.getProperty("MEDIA_UPLOAD_BASE_URL") ?: ""
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"$firebaseStorageBucket\"")
        buildConfigField("String", "FIREBASE_AUTH_DOMAIN", "\"$firebaseAuthDomain\"")
        buildConfigField("String", "MEDIA_UPLOAD_BASE_URL", "\"$mediaUploadBaseUrl\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":storage-core"))
    implementation(project(":ai-core"))
    implementation(project(":ai-prompt"))
    implementation(project(":ai-rag"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")
    implementation("com.google.mediapipe:tasks-text:0.10.32")

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    // MediaPipe LLM - Commented out as it's not yet publicly available in standard Maven repositories
    // implementation("com.google.mediapipe:llm-inference:0.10.7")
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
    }
}
