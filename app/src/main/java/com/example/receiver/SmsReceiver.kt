package com.example.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSmsMessage
import com.example.data.SmsEntity
import com.example.data.TalalSmsDatabase
import com.example.service.NotificationHelper
import com.example.telephony.TelephonyManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) {
            return
        }

        val messages: Array<AndroidSmsMessage>? = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            null
        }

        if (messages.isNullOrEmpty()) return

        val subId = intent.getIntExtra("subscription", intent.getIntExtra("slot", -1))
        val db = TalalSmsDatabase.getInstance(context)
        val dao = db.smsDao()

        // Group message parts by originating address
        val messagesBySender = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "غير معروف" }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((sender, messageList) in messagesBySender) {
                    // Check if sender is blocked
                    if (dao.isNumberBlocked(sender)) {
                        continue
                    }

                    val fullBody = buildString {
                        for (msg in messageList) {
                            append(msg.displayMessageBody ?: msg.messageBody ?: "")
                        }
                    }
                    val timestamp = messageList.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val smsc = messageList.firstOrNull()?.serviceCenterAddress

                    // Carrier label
                    val carrierName = if (subId > 0) "شريحة $subId" else "شبكة الجوال"

                    // Calculate or look up threadId
                    val threadId = kotlin.math.abs(sender.hashCode().toLong())

                    val entity = SmsEntity(
                        threadId = threadId,
                        address = sender,
                        body = fullBody,
                        date = timestamp,
                        type = SmsEntity.TYPE_INBOX,
                        subId = subId,
                        carrierName = carrierName,
                        deliveryStatus = 0,
                        read = false,
                        smsc = smsc
                    )
                    dao.insertMessage(entity)

                    // Write to system provider if default app
                    if (TelephonyManagerHelper.isDefaultSmsApp(context)) {
                        try {
                            val values = ContentValues().apply {
                                put(Telephony.Sms.ADDRESS, sender)
                                put(Telephony.Sms.BODY, fullBody)
                                put(Telephony.Sms.DATE, timestamp)
                                put(Telephony.Sms.READ, 0)
                                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                                if (subId > 0) {
                                    put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                                }
                            }
                            context.contentResolver.insert(Uri.parse("content://sms/inbox"), values)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Display Notification
                    val contactName = TelephonyManagerHelper.getContactName(context, sender)
                    NotificationHelper.showIncomingSmsNotification(
                        context = context,
                        senderNumber = sender,
                        senderName = contactName,
                        messageBody = fullBody,
                        threadId = threadId,
                        carrierName = carrierName
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
