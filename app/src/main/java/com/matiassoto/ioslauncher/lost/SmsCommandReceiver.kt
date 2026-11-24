package com.matiassoto.ioslauncher.lost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.matiassoto.ioslauncher.data.SecurityStore

/**
 * Listens for "<code word> LOCATE" / "<code word> RING" texts. Only active when the owner
 * has turned on Lost phone mode (off by default) and set a code word.
 */
class SmsCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val store = SecurityStore(context)
        if (!store.lostPhoneEnabled || !store.hasCodeWord) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        messages.groupBy { it.originatingAddress ?: "" }.forEach { (sender, parts) ->
            val body = parts.joinToString("") { it.messageBody ?: "" }.trim()
            val cmd = parseCommand(body) ?: return@forEach
            if (!store.checkCodeWord(cmd.second)) return@forEach
            dispatch(context, cmd.first, sender)
        }
    }

    private fun parseCommand(body: String): Pair<String, String>? {
        for (c in listOf(LostModeService.CMD_LOCATE, LostModeService.CMD_RING)) {
            if (body.length > c.length && body.endsWith(c, ignoreCase = true)) {
                val code = body.dropLast(c.length).trim()
                if (code.isNotEmpty()) return c to code
            }
        }
        return null
    }

    private fun dispatch(context: Context, cmd: String, sender: String) {
        val i = Intent(context, LostModeService::class.java)
            .setAction(if (cmd == LostModeService.CMD_RING) LostModeService.ACTION_RING else LostModeService.ACTION_LOCATE)
            .putExtra(LostModeService.EXTRA_SENDER, sender)
        val started = runCatching { ContextCompat.startForegroundService(context, i) }.isSuccess
        if (!started) {
            // Fallback when the service cannot be started from the background.
            if (cmd == LostModeService.CMD_RING) {
                AlarmPlayer.start(context)
            } else {
                val pending = goAsync()
                LocationHelper.getLocation(context, timeoutMs = 8_000) { loc ->
                    LostModeService.sendLocationSms(context, sender, loc)
                    pending.finish()
                }
            }
        }
    }
}
