package com.matiassoto.ioslauncher.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // Nothing is locked automatically: lock mode is only applied when the owner
        // turns it on from the launcher's settings (behind their PIN).
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "This turns off iOS Style Launcher's lock mode."
}
