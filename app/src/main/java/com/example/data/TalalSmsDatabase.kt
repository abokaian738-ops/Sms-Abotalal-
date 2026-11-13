package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [SmsEntity::class, BlockedNumberEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TalalSmsDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao

    companion object {
        @Volatile
        private var INSTANCE: TalalSmsDatabase? = null

        fun getInstance(context: Context): TalalSmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TalalSmsDatabase::class.java,
                    "talal_sms_database"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial welcome messages
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).smsDao()
                            val now = System.currentTimeMillis()
                            val seed = listOf(
                                SmsEntity(
                                    threadId = 1,
                                    address = "TALAL SMS",
                                    body = "مرحباً بك في تطبيق TALAL SMS! التطبيق يدعم تشغيل جميع شرائح الاتصال (Dual SIM) وتقارير التسليم ومركز الرسائل SMSC.",
                                    date = now - 3600000 * 2,
                                    type = SmsEntity.TYPE_INBOX,
                                    subId = 1,
                                    carrierName = "شريحة 1",
                                    deliveryStatus = 0,
                                    read = true,
                                    smsc = "+966500556677"
                                ),
                                SmsEntity(
                                    threadId = 1,
                                    address = "TALAL SMS",
                                    body = "يمكنك تعيين TALAL SMS كتطبيق افتراضي للرسائل في هاتفك والتمتع بالاستقرار التام والعمل في الخلفية.",
                                    date = now - 1800000,
                                    type = SmsEntity.TYPE_INBOX,
                                    subId = 1,
                                    carrierName = "شريحة 1",
                                    deliveryStatus = 0,
                                    read = false,
                                    smsc = "+966500556677"
                                ),
                                SmsEntity(
                                    threadId = 2,
                                    address = "+966551234567",
                                    body = "السلام عليكم، هل وصلت الرسالة؟",
                                    date = now - 7200000,
                                    type = SmsEntity.TYPE_INBOX,
                                    subId = 2,
                                    carrierName = "شريحة 2",
                                    deliveryStatus = 0,
                                    read = true,
                                    smsc = "+966550000000"
                                ),
                                SmsEntity(
                                    threadId = 2,
                                    address = "+966551234567",
                                    body = "وعليكم السلام، نعم وصلت مع تقرير التسليم المعتمد من شبكة الجوال.",
                                    date = now - 7000000,
                                    type = SmsEntity.TYPE_SENT,
                                    subId = 2,
                                    carrierName = "شريحة 2",
                                    deliveryStatus = 2, // DELIVERED
                                    read = true,
                                    smsc = "+966550000000"
                                )
                            )
                            dao.insertMessages(seed)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
