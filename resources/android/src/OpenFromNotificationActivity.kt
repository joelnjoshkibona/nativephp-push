package com.blutrixx.plugins.push

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The tap on a notification lands here, invisibly: it keeps the page the notification is about for the web (the web asks
 * with Push.TakeLaunchLink when the app opens or comes back) and opens the app. A separate step, because the app's own
 * activity does not hand a new intent to the web view.
 */
class OpenFromNotificationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent.getStringExtra(EXTRA_LINK).orEmpty()
        // a path inside the app only: not another site ("//host") and not a scheme
        if (link.startsWith("/") && !link.startsWith("//")) {
            getSharedPreferences(PushFunctions.PREFS, MODE_PRIVATE).edit().putString(PushFunctions.KEY_LINK, link).apply()
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(it)
        }
        finish()
    }

    companion object {
        const val EXTRA_LINK = "push_link"
    }
}
