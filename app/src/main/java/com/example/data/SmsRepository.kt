package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.model.ContactInfo
import com.example.model.ConversationSummary
import com.example.model.DeliveryStatus
import com.example.model.SimInfo
import com.example.model.SmsMessage
import com.example.telephony.ContactsHelper
import com.example.telephony.SmsSenderHelper
import com.example.telephony.TelephonyManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    private val db = TalalSmsDatabase.getInstance(context)
    private val dao = db.smsDao()
    val preferences = SmsPreferences(context)

    fun getConversationsFlow(): Flow<List<ConversationSummary>> {
        return dao.getAllMessagesFlow()
            .map { allMessages ->
                val grouped = allMessages.groupBy { it.address }
                grouped.map { (address, messages) ->
                    val latest = messages.maxByOrNull { it.date } ?: messages.first()
                    val unreadCount = messages.count { !it.read && it.type == SmsEntity.TYPE_INBOX }
                    val contactName = ContactsHelper.getContactNameCached(context, address)
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
            .flowOn(Dispatchers.IO)
    }

    fun getMessagesForAddress(address: String): Flow<List<SmsMessage>> {
        return dao.getMessagesForAddress(address)
            .map { entities ->
                val contactName = ContactsHelper.getContactNameCached(context, address)
                entities.map { entity ->
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
            .flowOn(Dispatchers.IO)
    }

    suspend fun loadDeviceContacts(): List<ContactInfo> {
        return ContactsHelper.loadAllContacts(context)
    }

    suspend fun resolvePickedContact(uri: Uri): ContactInfo? {
        return ContactsHelper.resolvePickedContactUri(context, uri)
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

        val cleanAddress = recipientAddress.trim()
        val threadId = kotlin.math.abs(cleanAddress.hashCode().toLong())
        val selectedSubId = simInfo?.subscriptionId ?: preferences.preferredSubId
        val carrierName = simInfo?.carrierName ?: "شبكة الجوال"
        val smsc = preferences.customSmsc.trim().ifBlank { simInfo?.smscNumber }
        val now = System.currentTimeMillis()

        var systemSmsId: Long? = null
        if (TelephonyManagerHelper.isDefaultSmsApp(context)) {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, cleanAddress)
                    put(Telephony.Sms.BODY, finalBody)
                    put(Telephony.Sms.DATE, now)
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    if (selectedSubId > 0) {
                        put(Telephony.Sms.SUBSCRIPTION_ID, selectedSubId)
                    }
                }
                val insertedUri = context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
                if (insertedUri != null) {
                    systemSmsId = ContentUris.parseId(insertedUri)
                }
            } catch (_: Exception) {
            }
        }

        val entity = SmsEntity(
            systemSmsId = systemSmsId,
            threadId = threadId,
            address = cleanAddress,
            body = finalBody,
            date = now,
            type = SmsEntity.TYPE_OUTBOX,
            subId = selectedSubId,
            carrierName = carrierName,
            deliveryStatus = if (preferences.deliveryReportsEnabled) {
                DeliveryStatus.PENDING.code
            } else {
                DeliveryStatus.NONE.code
            },
            read = true,
            smsc = smsc
        )

        val messageId = dao.insertMessage(entity)

        val dispatched = SmsSenderHelper.sendSms(
            context = context,
            destinationAddress = cleanAddress,
            text = finalBody,
            messageId = messageId,
            subId = selectedSubId,
            smscAddress = smsc,
            requestDeliveryReport = preferences.deliveryReportsEnabled
        )

        if (!dispatched) {
            dao.updateDeliveryAndType(messageId, DeliveryStatus.FAILED.code, SmsEntity.TYPE_FAILED)
        }
        dispatched
    }

    suspend fun markAddressAsRead(address: String) = withContext(Dispatchers.IO) {
        dao.markAddressAsRead(address)
        if (TelephonyManagerHelper.isDefaultSmsApp(context)) {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                }
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(address)
                )
            } catch (_: Exception) {
            }
        }
    }

    suspend fun deleteAddress(address: String) = withContext(Dispatchers.IO) {
        dao.deleteAddress(address)
        if (TelephonyManagerHelper.isDefaultSmsApp(context)) {
            try {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.ADDRESS} = ?",
                    arrayOf(address)
                )
            } catch (_: Exception) {
            }
        }
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        dao.deleteMessage(messageId)
    }

    suspend fun blockNumber(phoneNumber: String, reason: String = "حظر يدوي") = withContext(Dispatchers.IO) {
        val clean = phoneNumber.trim()
        if (clean.isNotEmpty()) {
            dao.blockNumber(BlockedNumberEntity(phoneNumber = clean, reason = reason))
        }
    }

    suspend fun unblockNumber(phoneNumber: String) = withContext(Dispatchers.IO) {
        dao.unblockNumber(phoneNumber)
    }

    fun getBlockedNumbersFlow(): Flow<List<BlockedNumberEntity>> = dao.getBlockedNumbers()

    /**
     * Synchronize real SMS messages from the Android system SMS provider without duplicates
     */
    suspend fun syncWithSystemSms(): Int = withContext(Dispatchers.IO) {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasReadPermission) return@withContext 0

        val activeSims = TelephonyManagerHelper.getActiveSimCards(context)
        val simCarrierMap = activeSims.associate { it.subscriptionId to it.carrierName }

        val projection = mutableListOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS,
            Telephony.Sms.SERVICE_CENTER
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            projection.add(Telephony.Sms.SUBSCRIPTION_ID)
        }

        var syncedCount = 0
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection.toTypedArray(),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 500"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = c.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = c.getColumnIndex(Telephony.Sms.READ)
                val statusIdx = c.getColumnIndex(Telephony.Sms.STATUS)
                val smscIdx = c.getColumnIndex(Telephony.Sms.SERVICE_CENTER)
                val subIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                } else {
                    -1
                }

                val entities = mutableListOf<SmsEntity>()
                while (c.moveToNext()) {
                    val sysId = if (idIdx != -1) c.getLong(idIdx) else null
                    val address = if (addressIdx != -1) c.getString(addressIdx)?.trim() else null
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) else null
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                    val sysType = if (typeIdx != -1) c.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val read = if (readIdx != -1) c.getInt(readIdx) == 1 else true
                    val sysStatus = if (statusIdx != -1) c.getInt(statusIdx) else Telephony.Sms.STATUS_NONE
                    val smsc = if (smscIdx != -1) c.getString(smscIdx) else null
                    val subId = if (subIdx != -1) c.getInt(subIdx) else -1

                    if (!address.isNullOrBlank() && !body.isNullOrBlank()) {
                        val mappedType = when (sysType) {
                            Telephony.Sms.MESSAGE_TYPE_SENT -> SmsEntity.TYPE_SENT
                            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> SmsEntity.TYPE_OUTBOX
                            Telephony.Sms.MESSAGE_TYPE_FAILED -> SmsEntity.TYPE_FAILED
                            Telephony.Sms.MESSAGE_TYPE_DRAFT -> SmsEntity.TYPE_DRAFT
                            else -> SmsEntity.TYPE_INBOX
                        }

                        val mappedDeliveryStatus = when {
                            mappedType == SmsEntity.TYPE_FAILED -> DeliveryStatus.FAILED.code
                            sysStatus == Telephony.Sms.STATUS_COMPLETE -> DeliveryStatus.DELIVERED.code
                            sysStatus == Telephony.Sms.STATUS_PENDING -> DeliveryStatus.PENDING.code
                            sysStatus == Telephony.Sms.STATUS_FAILED -> DeliveryStatus.FAILED.code
                            mappedType == SmsEntity.TYPE_SENT -> DeliveryStatus.SENT.code
                            else -> DeliveryStatus.NONE.code
                        }

                        val carrierName = simCarrierMap[subId] ?: "شبكة الجوال"
                        val threadId = kotlin.math.abs(address.hashCode().toLong())

                        entities.add(
                            SmsEntity(
                                systemSmsId = sysId,
                                threadId = threadId,
                                address = address,
                                body = body,
                                date = date,
                                type = mappedType,
                                subId = subId,
                                carrierName = carrierName,
                                deliveryStatus = mappedDeliveryStatus,
                                read = read,
                                smsc = smsc
                            )
                        )
                    }
                }
                if (entities.isNotEmpty()) {
                    val inserted = dao.insertMessagesIgnore(entities)
                    syncedCount = inserted.count { it != -1L }
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        syncedCount
    }
}
