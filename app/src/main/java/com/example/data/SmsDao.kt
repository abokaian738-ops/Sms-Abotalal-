package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {

    @Query("SELECT * FROM sms_messages ORDER BY date DESC")
    fun getAllMessagesFlow(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId ORDER BY date ASC")
    fun getMessagesForThread(threadId: Long): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms_messages WHERE address = :address ORDER BY date ASC")
    fun getMessagesForAddress(address: String): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): SmsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SmsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<SmsEntity>): List<Long>

    @Update
    suspend fun updateMessage(message: SmsEntity)

    @Query("UPDATE sms_messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: Long, status: Int)

    @Query("UPDATE sms_messages SET read = 1 WHERE threadId = :threadId")
    suspend fun markThreadAsRead(threadId: Long)

    @Query("UPDATE sms_messages SET read = 1 WHERE address = :address")
    suspend fun markAddressAsRead(address: String)

    @Query("DELETE FROM sms_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("DELETE FROM sms_messages WHERE address = :address")
    suspend fun deleteAddress(address: String)

    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("SELECT COUNT(*) FROM sms_messages WHERE read = 0 AND type = 1")
    fun getUnreadCountFlow(): Flow<Int>

    // Blocked numbers
    @Query("SELECT * FROM blocked_numbers ORDER BY dateAdded DESC")
    fun getBlockedNumbers(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE phoneNumber = :phoneNumber)")
    suspend fun isNumberBlocked(phoneNumber: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockNumber(blocked: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun unblockNumber(phoneNumber: String)
}
