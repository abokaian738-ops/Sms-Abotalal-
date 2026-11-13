package com.example.service

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import com.example.data.SmsEntity
import com.example.data.SmsPreferences
import com.example.data.TalalSmsDatabase
import com.example.model.DeliveryStatus
import com.example.telephony.SmsSenderHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val extras = intent.extras
            if (extras != null) {
                val message = extras.getString(Intent.EXTRA_TEXT)
                val uri: Uri? = intent.data
                val recipient = uri?.schemeSpecificPart?.substringBefore('?')

                if (!recipient.isNullOrBlank() && !message.isNullOrBlank()) {
                    val db = TalalSmsDatabase.getInstance(this)
                    val dao = db.smsDao()
                    val prefs = SmsPreferences(this)
                    val threadId = kotlin.math.abs(recipient.hashCode().toLong())

                    CoroutineScope(Dispatchers.IO).launch {
                        val entity = SmsEntity(
                            threadId = threadId,
                            address = recipient,
                            body = message,
                            date = System.currentTimeMillis(),
                            type = SmsEntity.TYPE_OUTBOX,
                            subId = prefs.preferredSubId,
                            carrierName = "رد تلقائي",
                            deliveryStatus = DeliveryStatus.PENDING.code,
                            read = true
                        )
                        val id = dao.insertMessage(entity)
                        SmsSenderHelper.sendSms(
                            context = this@HeadlessSmsSendService,
                            destinationAddress = recipient,
                            text = message,
                            messageId = id,
                            subId = prefs.preferredSubId,
                            requestDeliveryReport = prefs.deliveryReportsEnabled
                        )
                        stopSelf(startId)
                    }
                    return START_NOT_STICKY
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
