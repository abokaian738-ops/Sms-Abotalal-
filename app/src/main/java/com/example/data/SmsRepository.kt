package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.model.ConversationSummary
import com.example.model.DeliveryStatus
import com.example.model.SimInfo
import com.example.model.SmsMessage
import com.example.telephony.SmsSenderHelper
import com.example.telephony.TelephonyManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    private val db = TalalSmsDatabase.getInstance(context)
    private val dao = db.smsDao()
    val preferences = SmsPreferences(context)

    fun getConversationsFlow(): Flow<List<ConversationSummary>> {
        return dao.getAllMessagesFlow().map { allMessages ->
            // Group messages by address or threadId
            val grouped = allMessages.groupBy { it.address }
            grouped.map { (address, messages) ->
                val latest = messages.maxByOrNull { it.date } ?: messages.first()
                val unreadCount = messages.count { !it.read && it.type == SmsEntity.TYPE_INBOX }
                val contactName = TelephonyManagerHelper.getContactName(context, address)
                ConversationSummary(
                    threadId = latest.threadId,
                    address = address,
                    contactName = contactName,
                    snippet = latest.body,
                    date = latest.date,
                    unreadCount = unreadCount,
                    lastDeliveryStatus = latest.deliveryStatus,
                    carrierName = latest.carrierName,
                    subId = latest.subId
                )
            }.sortedByDescending { it.date }
        }
    }

    fun getMessagesForThread(threadId: Long): Flow<List<SmsMessage>> {
        return dao.getMessagesForThread(threadId).map { entities ->
            entities.map { entity ->
                val contactName = TelephonyManagerHelper.getContactName(context, entity.address)
                SmsMessage(
                    id = entity.id,
                    threadId = entity.threadId,
                    address = entity.address,
                    contactName = contactName,
                    body = entity.body,
                    date = entity.date,
                    type = entity.type,
                    subId = entity.subId,
                    carrierName = entity.carrierName,
                    deliveryStatus = DeliveryStatus.fromCode(entity.deliveryStatus),
                    isRead = entity.read,
                    smsc = entity.smsc
                )
            }
        }
    }

    fun getMessagesForAddress(address: String): Flow<List<SmsMessage>> {
        return dao.getMessagesForAddress(address).map { entities ->
            entities.map { entity ->
                val contactName = TelephonyManagerHelper.getContactName(context, entity.address)
                SmsMessage(
                    id = entity.id,
                    threadId = entity.threadId,
                    address = entity.address,
                    contactName = contactName,
                    body = entity.body,
                    date = entity.date,
                    type = entity.type,
                    subId = entity.subId,
                    carrierName = entity.carrierName,
                    deliveryStatus = DeliveryStatus.fromCode(entity.deliveryStatus),
                    isRead = entity.read,
                    smsc = entity.smsc
                )
            }
        }
    }

    suspend fun sendMessage(
        recipientAddress: String,
        body: String,
        simInfo: SimInfo?
    ): Boolean = withContext(Dispatchers.IO) {
        var finalBody = body.trim()
        if (preferences.signatureEnabled && preferences.signatureText.isNotBlank()) {
            finalBody = "$finalBody\n${preferences.signatureText}"
        }

        val threadId = kotlin.math.abs(recipientAddress.hashCode().toLong())
        val selectedSubId = simInfo?.subscriptionId ?: preferences.preferredSubId
        val carrierName = simInfo?.carrierName ?: "شريحة افتراضية"
        val smsc = if (preferences.customSmsc.isNotBlank()) {
            preferences.customSmsc
        } else {
            simInfo?.smscNumber
        }

        val entity = SmsEntity(
            threadId = threadId,
            address = recipientAddress,
            body = finalBody,
            date = System.currentTimeMillis(),
            type = SmsEntity.TYPE_OUTBOX,
            subId = selectedSubId,
            carrierName = carrierName,
            deliveryStatus = if (preferences.deliveryReportsEnabled) DeliveryStatus.PENDING.code else DeliveryStatus.NONE.code,
            read = true,
            smsc = smsc
        )

        val messageId = dao.insertMessage(entity)

        // Save to system SMS provider if default SMS app
        if (TelephonyManagerHelper.isDefaultSmsApp(context)) {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, recipientAddress)
                    put(Telephony.Sms.BODY, finalBody)
                    put(Telephony.Sms.DATE, entity.date)
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    if (selectedSubId > 0) {
                        put(Telephony.Sms.SUBSCRIPTION_ID, selectedSubId)
                    }
                }
                context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Dispatch via SmsSenderHelper
        SmsSenderHelper.sendSms(
            context = context,
            destinationAddress = recipientAddress,
            text = finalBody,
            messageId = messageId,
            subId = selectedSubId,
            smscAddress = smsc,
            requestDeliveryReport = preferences.deliveryReportsEnabled
        )
    }

    suspend fun markThreadAsRead(threadId: Long) = withContext(Dispatchers.IO) {
        dao.markThreadAsRead(threadId)
    }

    suspend fun markAddressAsRead(address: String) = withContext(Dispatchers.IO) {
        dao.markAddressAsRead(address)
    }

    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        dao.deleteThread(threadId)
    }

    suspend fun deleteAddress(address: String) = withContext(Dispatchers.IO) {
        dao.deleteAddress(address)
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        dao.deleteMessage(messageId)
    }

    suspend fun blockNumber(phoneNumber: String, reason: String = "حظر يدوي") = withContext(Dispatchers.IO) {
        dao.blockNumber(BlockedNumberEntity(phoneNumber = phoneNumber, reason = reason))
    }

    suspend fun unblockNumber(phoneNumber: String) = withContext(Dispatchers.IO) {
        dao.unblockNumber(phoneNumber)
    }

    fun getBlockedNumbersFlow(): Flow<List<BlockedNumberEntity>> = dao.getBlockedNumbers()

    /**
     * Synchronize with system Telephony SMS provider if user granted READ_SMS
     */
    suspend fun syncWithSystemSms(): Int = withContext(Dispatchers.IO) {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasReadPermission) return@withContext 0

        var count = 0
        val cursor: Cursor? = try {
            context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("_id", "thread_id", "address", "body", "date", "type", "read"),
                null,
                null,
                "date DESC LIMIT 50"
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use { c ->
            val addressIdx = c.getColumnIndex("address")
            val bodyIdx = c.getColumnIndex("body")
            val dateIdx = c.getColumnIndex("date")
            val typeIdx = c.getColumnIndex("type")
            val readIdx = c.getColumnIndex("read")

            val entities = mutableListOf<SmsEntity>()
            while (c.moveToNext()) {
                val address = if (addressIdx != -1) c.getString(addressIdx) else null
                val body = if (bodyIdx != -1) c.getString(bodyIdx) else null
                val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                val type = if (typeIdx != -1) c.getInt(typeIdx) else 1
                val read = if (readIdx != -1) c.getInt(readIdx) == 1 else true

                if (!address.isNullOrBlank() && !body.isNullOrBlank()) {
                    val threadId = kotlin.math.abs(address.hashCode().toLong())
                    entities.add(
                        SmsEntity(
                            threadId = threadId,
                            address = address,
                            body = body,
                            date = date,
                            type = if (type == 2) SmsEntity.TYPE_SENT else SmsEntity.TYPE_INBOX,
                            subId = -1,
                            carrierName = "الرسائل السابقة",
                            deliveryStatus = if (type == 2) 2 else 0,
                            read = read
                        )
                    )
                    count++
                }
            }
            if (entities.isNotEmpty()) {
                dao.insertMessages(entities)
            }
        }
        count
    }
}
