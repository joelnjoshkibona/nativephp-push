package com.blutrixx.plugins.push

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * Asks Android's notification permission and writes down that the person answered, so Status can tell "not asked yet or
 * still deciding" (ask) from "answered no" (denied). Invisible, and gone once Android has the answer.
 */
class NotificationPermissionFragment : Fragment() {

    private val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        context?.getSharedPreferences(PushFunctions.PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(PushFunctions.KEY_ANSWERED, true)?.apply()
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val TAG = "push_notification_permission"
    }
}
