package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_messages",
    indices = [
        Index("threadId"),
        Index("address"),
        Index("date")
    ]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long = System.currentTimeMillis(),
    val type: Int, // 1 = INBOX, 2 = SENT, 3 = DRAFT, 4 = OUTBOX, 5 = FAILED
    val subId: Int = -1, // Subscription / SIM card ID
    val carrierName: String = "",
    val deliveryStatus: Int = 0, // 0 = None, 1 = Pending, 2 = Delivered, 3 = Failed, 4 = Sent
    val read: Boolean = false,
    val smsc: String? = null
) {
    companion object {
        const val TYPE_INBOX = 1
        const val TYPE_SENT = 2
        const val TYPE_DRAFT = 3
        const val TYPE_OUTBOX = 4
        const val TYPE_FAILED = 5
    }
}

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val reason: String = "حظر يدوي",
    val dateAdded: Long = System.currentTimeMillis()
)
