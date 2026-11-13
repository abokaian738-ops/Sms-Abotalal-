package com.example.model

data class ConversationSummary(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val lastDeliveryStatus: Int,
    val carrierName: String,
    val subId: Int
)

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val date: Long,
    val type: Int,
    val subId: Int,
    val carrierName: String,
    val deliveryStatus: DeliveryStatus,
    val isRead: Boolean,
    val smsc: String?
)
