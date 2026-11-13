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

        when (action) {
            ACTION_SMS_SENT -> {
                val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                if (messageId != -1L) {
                    val status = when (resultCode) {
                        Activity.RESULT_OK -> DeliveryStatus.SENT.code
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE,
                        SmsManager.RESULT_ERROR_NO_SERVICE,
                        SmsManager.RESULT_ERROR_NULL_PDU,
                        SmsManager.RESULT_ERROR_RADIO_OFF -> DeliveryStatus.FAILED.code
                        else -> DeliveryStatus.FAILED.code
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.updateDeliveryStatus(messageId, status)
                    }
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
                    CoroutineScope(Dispatchers.IO).launch {
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
            }

            ACTION_MARK_READ -> {
                val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
                if (threadId != -1L) {
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.markThreadAsRead(threadId)
                    }
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(threadId.toInt())
                }
            }

            ACTION_QUICK_REPLY -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
                val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
                val results = RemoteInput.getResultsFromIntent(intent)
                val replyText = results?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()

                if (!replyText.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val newMsg = SmsEntity(
                            threadId = if (threadId > 0) threadId else System.currentTimeMillis(),
                            address = address,
                            body = replyText,
                            date = System.currentTimeMillis(),
                            type = SmsEntity.TYPE_OUTBOX,
                            subId = prefs.preferredSubId,
                            carrierName = "إرسال سريع",
                            deliveryStatus = DeliveryStatus.PENDING.code,
                            read = true
                        )
                        val id = dao.insertMessage(newMsg)
                        SmsSenderHelper.sendSms(
                            context = context,
                            destinationAddress = address,
                            text = replyText,
                            messageId = id,
                            subId = prefs.preferredSubId,
                            requestDeliveryReport = prefs.deliveryReportsEnabled
                        )
                    }
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (threadId > 0) {
                        notificationManager.cancel(threadId.toInt())
                    }
                }
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
