package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R
import com.example.receiver.SmsDeliveryReceiver

object NotificationHelper {

    const val CHANNEL_SMS = "talal_sms_messages"
    const val CHANNEL_DELIVERY = "talal_sms_delivery"
    const val KEY_TEXT_REPLY = "key_text_reply"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val smsChannel = NotificationChannel(
                CHANNEL_SMS,
                "رسائل TALAL SMS المستلمة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات الرسائل النصية القصيرة الواردة"
                enableVibration(true)
                setShowBadge(true)
            }

            val deliveryChannel = NotificationChannel(
                CHANNEL_DELIVERY,
                "تقارير تسليم الرسائل",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات تأكيد وصول وتسليم الرسائل إلى المستقبل"
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(smsChannel)
            notificationManager.createNotificationChannel(deliveryChannel)
        }
    }

    fun showIncomingSmsNotification(
        context: Context,
        senderNumber: String,
        senderName: String?,
        messageBody: String,
        threadId: Long,
        carrierName: String
    ) {
        createNotificationChannels(context)

        val displayName = senderName ?: senderNumber
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_THREAD_ID", threadId)
            putExtra("EXTRA_ADDRESS", senderNumber)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPI = PendingIntent.getActivity(context, threadId.toInt(), openIntent, flags)

        // Mark as Read Action
        val markReadIntent = Intent(context, SmsDeliveryReceiver::class.java).apply {
            action = SmsDeliveryReceiver.ACTION_MARK_READ
            putExtra(SmsDeliveryReceiver.EXTRA_THREAD_ID, threadId)
        }
        val markReadPI = PendingIntent.getBroadcast(
            context,
            (threadId * 10).toInt(),
            markReadIntent,
            flags
        )

        // Remote Input for Quick Reply
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("اكتب رداً...")
            .build()

        val replyIntent = Intent(context, SmsDeliveryReceiver::class.java).apply {
            action = SmsDeliveryReceiver.ACTION_QUICK_REPLY
            putExtra(SmsDeliveryReceiver.EXTRA_ADDRESS, senderNumber)
            putExtra(SmsDeliveryReceiver.EXTRA_THREAD_ID, threadId)
        }
        val replyPI = PendingIntent.getBroadcast(
            context,
            (threadId * 10 + 1).toInt(),
            replyIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "رد سريع",
            replyPI
        ).addRemoteInput(remoteInput).build()

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "تعيين كمقروء",
            markReadPI
        ).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_SMS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayName)
            .setContentText(messageBody)
            .setSubText(carrierName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setContentIntent(contentPI)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction)
            .addAction(markReadAction)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(threadId.toInt(), notification)
    }

    fun showDeliveryReportNotification(
        context: Context,
        recipient: String,
        delivered: Boolean
    ) {
        createNotificationChannels(context)

        val title = if (delivered) "تم تسليم الرسالة بنجاح ✓✓" else "تعذر تسليم الرسالة"
        val text = if (delivered) {
            "تم تأكيد استلام الرسالة من قبل: $recipient"
        } else {
            "فشل تسليم الرسالة إلى: $recipient"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DELIVERY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}
