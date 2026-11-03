package com.blutrixx.plugins.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.nativephp.mobile.bridge.BridgeFunction
import java.util.concurrent.TimeUnit

/**
 * Push, the part the web view calls: may notifications be shown, the phone's Firebase token, Android's permission
 * question, the notification channel's name, and the page of the notification the person tapped. The web registers the
 * token with the app's own API; nothing here talks to a server.
 */
object PushFunctions {

    private const val TAG = "Push"
    const val PREFS = "blutrixx_push"
    const val KEY_TOKEN = "token"
    const val KEY_LINK = "launch_link"
    const val KEY_ANSWERED = "answered"
    const val KEY_CHANNEL_NAME = "channel_name"
    const val KEY_CHANNEL_DESCRIPTION = "channel_description"

    /** granted | denied (refused, or turned off in Android's settings) | ask (Android 13 and later: not asked yet) */
    private fun permission(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                return if (NotificationManagerCompat.from(context).areNotificationsEnabled()) "granted" else "denied"
            }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return if (prefs.getBoolean(KEY_ANSWERED, false)) "denied" else "ask"
        }
        return if (NotificationManagerCompat.from(context).areNotificationsEnabled()) "granted" else "denied"
    }

    class Status(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""
            return mapOf("permission" to permission(context), "token" to token)
        }
    }

    /** Asks Android's own question (a fragment shows it and notes the answer); the web reads the answer with Status. */
    class RequestPermission(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            if (permission(activity) == "granted") return mapOf("granted" to true, "asked" to false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return mapOf("granted" to false, "asked" to false)
            activity.runOnUiThread {
                val fm = activity.supportFragmentManager
                if (fm.findFragmentByTag(NotificationPermissionFragment.TAG) != null) return@runOnUiThread
                // android.R.id.content as the container: a fragment added without one may never start
                fm.beginTransaction()
                    .add(android.R.id.content, NotificationPermissionFragment(), NotificationPermissionFragment.TAG)
                    .commitAllowingStateLoss()
            }
            return mapOf("granted" to false, "asked" to true)
        }
    }

    class GetToken(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return try {
                val token = Tasks.await(FirebaseMessaging.getInstance().token, 10, TimeUnit.SECONDS)
                if (!token.isNullOrEmpty()) prefs.edit().putString(KEY_TOKEN, token).apply()
                mapOf("token" to (token ?: ""))
            } catch (e: Exception) {
                Log.w(TAG, "GetToken: ${e.message}")
                mapOf("token" to (prefs.getString(KEY_TOKEN, "") ?: ""))
            }
        }
    }

    /** The page the last tapped notification opens (a path like /orders/42): given once, then forgotten. */
    class TakeLaunchLink(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val link = prefs.getString(KEY_LINK, "") ?: ""
            if (link.isNotEmpty()) prefs.edit().remove(KEY_LINK).apply()
            return mapOf("link" to link)
        }
    }

    /** What Android's notification settings call this app's channel. Used the next time a notification is shown. */
    class SetChannel(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val name = (parameters["name"] as? String)?.trim().orEmpty()
            val description = (parameters["description"] as? String)?.trim().orEmpty()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (name.isNotEmpty()) putString(KEY_CHANNEL_NAME, name)
                if (description.isNotEmpty()) putString(KEY_CHANNEL_DESCRIPTION, description)
            }.apply()
            return mapOf("ok" to true)
        }
    }
}
