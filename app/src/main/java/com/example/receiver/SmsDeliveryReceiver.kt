package com.example.receiver

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.core.app.RemoteInput
import com.example.data.SmsEntity
import com.example.data.SmsPreferences
import com.example.data.TalalSmsDatabase
import com.example.model.DeliveryStatus
import com.example.service.NotificationHelper
import com.example.telephony.SmsSenderHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val db = TalalSmsDatabase.getInstance(context)
        val dao = db.smsDao()
        val prefs = SmsPreferences(context)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_SMS_SENT -> {
                        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                        if (messageId != -1L) {
                            val isSuccess = (resultCode == Activity.RESULT_OK)
                            val status = if (isSuccess) DeliveryStatus.SENT.code else DeliveryStatus.FAILED.code
                            val type = if (isSuccess) SmsEntity.TYPE_SENT else SmsEntity.TYPE_FAILED
                            dao.updateDeliveryAndType(messageId, status, type)
                        }
                    }

                    ACTION_SMS_DELIVERED -> {
                        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                        if (messageId != -1L) {
                            val isDelivered = (resultCode == Activity.RESULT_OK)
                            val status = if (isDelivered) {
                                DeliveryStatus.DELIVERED.code
                            } else {
                                DeliveryStatus.FAILED.code
                            }
                            dao.updateDeliveryStatus(messageId, status)
                            val msg = dao.getMessageById(messageId)
                            if (msg != null && prefs.deliveryReportsEnabled) {
                                NotificationHelper.showDeliveryReportNotification(
                                    context = context,
                                    recipient = msg.address,
                                    delivered = isDelivered
                                )
                            }
                        }
                    }

                    ACTION_MARK_READ -> {
                        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
                        val address = intent.getStringExtra(EXTRA_ADDRESS)
                        if (!address.isNullOrBlank()) {
                            dao.markAddressAsRead(address)
                        } else if (threadId != -1L) {
                            dao.markThreadAsRead(threadId)
                        }
                        if (threadId != -1L) {
                            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancel(threadId.toInt())
                        }
                    }

                    ACTION_QUICK_REPLY -> {
                        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return@launch
                        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
                        val results = RemoteInput.getResultsFromIntent(intent)
                        val replyText = results?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()

                        if (!replyText.isNullOrBlank()) {
                            val newMsg = SmsEntity(
                                threadId = if (threadId > 0) threadId else kotlin.math.abs(address.hashCode().toLong()),
                                address = address,
                                body = replyText.trim(),
                                date = System.currentTimeMillis(),
                                type = SmsEntity.TYPE_OUTBOX,
                                subId = prefs.preferredSubId,
                                carrierName = "رد سريع",
                                deliveryStatus = DeliveryStatus.PENDING.code,
                                read = true
                            )
                            val id = dao.insertMessage(newMsg)
                            val sent = SmsSenderHelper.sendSms(
                                context = context,
                                destinationAddress = address,
                                text = replyText.trim(),
                                messageId = id,
                                subId = prefs.preferredSubId,
                                smscAddress = prefs.customSmsc.ifBlank { null },
                                requestDeliveryReport = prefs.deliveryReportsEnabled
                            )
                            if (!sent) {
                                dao.updateDeliveryAndType(id, DeliveryStatus.FAILED.code, SmsEntity.TYPE_FAILED)
                            }
                        }
                        if (threadId > 0) {
                            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancel(threadId.toInt())
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.talalsms.com.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.talalsms.com.SMS_DELIVERED"
        const val ACTION_MARK_READ = "com.talalsms.com.ACTION_MARK_READ"
        const val ACTION_QUICK_REPLY = "com.talalsms.com.ACTION_QUICK_REPLY"

        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
    }
}
