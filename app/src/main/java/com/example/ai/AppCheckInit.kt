package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by Firebase SDK and runtime harness.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val TOKEN_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val app = FirebaseApp.getInstance()
        val appCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = app.persistenceKey
                val debugPrefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                val previousSecret = debugPrefs.getString(DEBUG_SECRET_KEY, null)

                // CRITICAL: Must use synchronous commit() before installing DebugAppCheckProviderFactory.
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()

                // Clear any stale cached App Check JWT if the debug secret changed or was newly injected.
                if (previousSecret != debugToken) {
                    val tokenPrefs = context.getSharedPreferences(
                        "$TOKEN_STORE_PREFIX$persistenceKey",
                        Context.MODE_PRIVATE
                    )
                    tokenPrefs.edit().clear().commit()
                }
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra; reusing persisted secret if available.")
            }

            appCheck.setTokenAutoRefreshEnabled(true)
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            // Proactively warm/refresh App Check token with the active debug provider.
            if (!debugToken.isNullOrEmpty()) {
                appCheck.getAppCheckToken(true)
                    .addOnSuccessListener {
                        Log.d(TAG, "App Check debug token verified and refreshed.")
                    }
                    .addOnFailureListener { err ->
                        Log.e(TAG, "App Check initial debug token exchange failed", err)
                    }
            }
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    appCheck.setTokenAutoRefreshEnabled(true)
                    appCheck.installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
