package com.mapsupervision.data.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.data.BuildConfig
import kotlinx.coroutines.tasks.await

internal open class FirebaseRuntime(private val context: Context) {

    init {
        registerLifecycleCallbacks()
    }

    private fun registerLifecycleCallbacks() {
        if (isLifecycleRegistered) return
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) {
                    setFirestoreNetwork(enabled = true)
                }
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0) {
                    setFirestoreNetwork(enabled = false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        isLifecycleRegistered = true
    }

    private fun setFirestoreNetwork(enabled: Boolean) {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val firestore = FirebaseFirestore.getInstance()
                if (enabled) {
                    firestore.enableNetwork()
                    AppLogger.d("Firestore network enabled")
                } else {
                    firestore.disableNetwork()
                    AppLogger.d("Firestore network disabled")
                }
            }
        }.onFailure { error ->
            AppLogger.e(error, "Failed to set firestore network enabled=$enabled")
        }
    }

    open fun firestore(): FirebaseFirestore {
        ensureInitialized()
        return FirebaseFirestore.getInstance()
    }

    open fun auth(): FirebaseAuth {
        ensureInitialized()
        return FirebaseAuth.getInstance()
    }

    open suspend fun getFirebaseToken(): String {
        ensureInitialized()
        val user = auth().currentUser ?: error("Firebase user is not signed in.")
        return user.getIdToken(false).await().token ?: error("Firebase ID token missing.")
    }

    open fun authConfigured(): Boolean {
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

    companion object {
        private var isLifecycleRegistered = false
    }
}
