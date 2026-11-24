package com.matiassoto.ioslauncher.lost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.matiassoto.ioslauncher.data.SecurityStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (SecurityStore(context).lostPhoneEnabled) LostModeService.start(context)
    }
}
