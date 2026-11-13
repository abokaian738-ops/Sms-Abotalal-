package com.example.receiver

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSmsMessage
import com.example.data.SmsEntity
import com.example.data.TalalSmsDatabase
import com.example.service.NotificationHelper
import com.example.telephony.ContactsHelper
import com.example.telephony.TelephonyManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isDeliver = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        val isReceived = action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        if (!isDeliver && !isReceived) return

        // Avoid duplicate processing when app is already the default SMS app (which receives SMS_DELIVER)
        if (isReceived && TelephonyManagerHelper.isDefaultSmsApp(context)) {
            return
        }

        val messages: Array<AndroidSmsMessage>? = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (_: Exception) {
            null
        }
        if (messages.isNullOrEmpty()) return

        val subId = extractSubscriptionId(intent)
        val db = TalalSmsDatabase.getInstance(context)
        val dao = db.smsDao()

        val messagesBySender = messages.groupBy {
            it.displayOriginatingAddress ?: it.originatingAddress ?: "غير معروف"
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activeSims = TelephonyManagerHelper.getActiveSimCards(context)
                val matchingSim = activeSims.firstOrNull { it.subscriptionId == subId }
                val carrierName = matchingSim?.carrierName
                    ?: if (subId > 0) "شريحة $subId" else "شبكة الجوال"

                for ((sender, messageList) in messagesBySender) {
                    val normalizedSender = ContactsHelper.normalizePhoneNumber(sender)
                    if (dao.isNumberBlocked(sender) ||
                        (normalizedSender.isNotEmpty() && dao.isNumberBlocked(normalizedSender))
                    ) {
                        continue
                    }

                    val fullBody = buildString {
                        for (msg in messageList) {
                            append(msg.displayMessageBody ?: msg.messageBody ?: "")
                        }
                    }
                    val timestamp = messageList.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val smsc = messageList.firstOrNull()?.serviceCenterAddress
                    val threadId = kotlin.math.abs(sender.hashCode().toLong())

                    var systemSmsId: Long? = null
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
                            val insertedUri = context.contentResolver.insert(
                                Uri.parse("content://sms/inbox"),
                                values
                            )
                            if (insertedUri != null) {
                                systemSmsId = ContentUris.parseId(insertedUri)
                            }
                        } catch (_: Exception) {
                        }
                    }

                    if (!dao.existsByContent(sender, timestamp, fullBody)) {
                        val entity = SmsEntity(
                            systemSmsId = systemSmsId,
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
                    }

                    val contactName = ContactsHelper.getContactNameCached(context, sender)
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

    private fun extractSubscriptionId(intent: Intent): Int {
        val keys = listOf(
            "subscription",
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "simId",
            "slot",
            "com.android.phone.extra.slot"
        )
        for (key in keys) {
            val value = intent.getIntExtra(key, -1)
            if (value >= 0) return value
        }
        return -1
    }
}
