package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION ||
            action == Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION
        ) {
            // Process WAP Push / MMS notifications in background
            val mimeType = intent.type
            if (mimeType == "application/vnd.wap.mms-message") {
                // Future MMS pdu parsing or notification if needed
            }
        }
    }
}
