package com.mapsupervision.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapsupervision.data.BuildConfig

internal class FirebaseRuntime(private val context: Context) {
    fun firestore(): FirebaseFirestore {
        ensureInitialized()
        return FirebaseFirestore.getInstance()
    }

    fun auth(): FirebaseAuth {
        ensureInitialized()
        return FirebaseAuth.getInstance()
    }

    fun authConfigured(): Boolean {
        ensureInitialized()
        return BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank()
    }

    private fun ensureInitialized() {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            return
        }
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank() ||
            BuildConfig.FIREBASE_APP_ID.isBlank() ||
            BuildConfig.FIREBASE_API_KEY.isBlank()
        ) {
            return
        }
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
            .build()
        FirebaseApp.initializeApp(context, options)
    }
}
