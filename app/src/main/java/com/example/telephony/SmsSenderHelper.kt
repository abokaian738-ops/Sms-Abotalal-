package com.example.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.example.receiver.SmsDeliveryReceiver

object SmsSenderHelper {

    fun sendSms(
        context: Context,
        destinationAddress: String,
        text: String,
        messageId: Long,
        subId: Int = -1,
        smscAddress: String? = null,
        requestDeliveryReport: Boolean = true
    ): Boolean {
        return try {
            val smsManager: SmsManager = when {
                subId > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 -> {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    context.getSystemService(SmsManager::class.java)
                }
                else -> {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            }

            // Create Sent PendingIntent
            val sentIntent = Intent(context, SmsDeliveryReceiver::class.java).apply {
                action = SmsDeliveryReceiver.ACTION_SMS_SENT
                putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val sentPI = PendingIntent.getBroadcast(
                context,
                messageId.toInt() * 2,
                sentIntent,
                flags
            )

            // Create Delivery Report PendingIntent
            val deliveryPI = if (requestDeliveryReport) {
                val deliveryIntent = Intent(context, SmsDeliveryReceiver::class.java).apply {
                    action = SmsDeliveryReceiver.ACTION_SMS_DELIVERED
                    putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
                }
                PendingIntent.getBroadcast(
                    context,
                    messageId.toInt() * 2 + 1,
                    deliveryIntent,
                    flags
                )
            } else {
                null
            }

            val smsc = smscAddress?.ifBlank { null }
            val parts = smsManager.divideMessage(text)

            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent?>()
                for (i in parts.indices) {
                    sentIntents.add(sentPI)
                    if (deliveryPI != null) {
                        deliveryIntents.add(deliveryPI)
                    }
                }
                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    smsc,
                    parts,
                    sentIntents,
                    if (requestDeliveryReport) deliveryIntents else null
                )
            } else {
                smsManager.sendTextMessage(
                    destinationAddress,
                    smsc,
                    text,
                    sentPI,
                    deliveryPI
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
