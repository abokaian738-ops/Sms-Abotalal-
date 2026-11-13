package com.example.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.example.receiver.SmsDeliveryReceiver

object SmsSenderHelper {

    private fun resolveSmsManager(context: Context, subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val baseManager = context.getSystemService(SmsManager::class.java)
            if (subId > 0) baseManager.createForSubscriptionId(subId) else baseManager
        } else {
            if (subId > 0) {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

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
            val smsManager = resolveSmsManager(context, subId)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val smsc = smscAddress?.trim()?.ifBlank { null }
            val parts = smsManager.divideMessage(text)
            val baseRequestCode = (messageId and 0xFFFFFFL).toInt() * 100

            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                val deliveryIntents = if (requestDeliveryReport) ArrayList<PendingIntent?>(parts.size) else null

                for (i in parts.indices) {
                    val sentIntent = Intent(context, SmsDeliveryReceiver::class.java).apply {
                        action = SmsDeliveryReceiver.ACTION_SMS_SENT
                        putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
                    }
                    sentIntents.add(
                        PendingIntent.getBroadcast(context, baseRequestCode + i * 2, sentIntent, flags)
                    )

                    if (deliveryIntents != null) {
                        val deliveryIntent = Intent(context, SmsDeliveryReceiver::class.java).apply {
                            action = SmsDeliveryReceiver.ACTION_SMS_DELIVERED
                            putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
                        }
                        deliveryIntents.add(
                            PendingIntent.getBroadcast(context, baseRequestCode + i * 2 + 1, deliveryIntent, flags)
                        )
                    }
                }

                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    smsc,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                val sentIntent = Intent(context, SmsDeliveryReceiver::class.java).apply {
                    action = SmsDeliveryReceiver.ACTION_SMS_SENT
                    putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
                }
                val sentPI = PendingIntent.getBroadcast(context, baseRequestCode, sentIntent, flags)

                val deliveryPI = if (requestDeliveryReport) {
                    val deliveryIntent = Intent(context, SmsDeliveryReceiver::class.java).apply {
                        action = SmsDeliveryReceiver.ACTION_SMS_DELIVERED
                        putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
                    }
                    PendingIntent.getBroadcast(context, baseRequestCode + 1, deliveryIntent, flags)
                } else {
                    null
                }

                smsManager.sendTextMessage(
                    destinationAddress,
                    smsc,
                    text,
                    sentPI,
                    deliveryPI
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
