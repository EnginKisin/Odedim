package com.example.odedim

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Room
import androidx.room.RoomDatabase
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "payments")
data class StoredPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: String,
    val dueEpochDay: Long,
    val repeatType: String,
    val monthlyDay: Int,
    val minuteOfDay: Int?,
    val historyJson: String,
    @ColumnInfo(defaultValue = "0") val periodPaid: Boolean = false,
    @ColumnInfo(defaultValue = "0") val undoAvailable: Boolean = false,
    @ColumnInfo(defaultValue = "'TRY'") val currencyCode: String = "TRY"
) {
    fun toPayment(): Payment {
        val records = JSONArray(historyJson)
        return Payment(
            id, name, amount, LocalDate.ofEpochDay(dueEpochDay),
            RepeatRule(RepeatType.valueOf(repeatType), monthlyDay),
            List(minOf(records.length(), Payment.HISTORY_LIMIT)) { offset ->
                val index = maxOf(0, records.length() - Payment.HISTORY_LIMIT) + offset
                val record = records.getJSONObject(index)
                val recordedAmount = record.optString("amount").takeIf { it.isNotEmpty() }
                val recordedCurrency = record.optString("currency").takeIf { it.isNotEmpty() }?.let {
                    runCatching { PaymentCurrency.valueOf(it) }.getOrNull()
                }
                PaymentRecord(
                    LocalDate.ofEpochDay(record.getLong("paid")),
                    LocalDate.ofEpochDay(record.getLong("due")),
                    recordedAmount.takeIf { recordedCurrency != null },
                    recordedCurrency.takeIf { recordedAmount != null }
                )
            },
            minuteOfDay?.let { LocalTime.of(it / 60, it % 60) },
            periodPaid, undoAvailable, PaymentCurrency.valueOf(currencyCode)
        )
    }

    companion object {
        fun from(payment: Payment): StoredPayment {
            val records = JSONArray()
            payment.history.takeLast(Payment.HISTORY_LIMIT).forEach { record ->
                val value = JSONObject().put("paid", record.paidOn.toEpochDay())
                    .put("due", record.dueDate.toEpochDay())
                if (record.hasAmountSnapshot) {
                    value.put("amount", record.amount).put("currency", record.currency?.name)
                }
                records.put(value)
            }
            return StoredPayment(
                payment.id, payment.name, payment.amount, payment.nextDueDate.toEpochDay(),
                payment.repeatRule.type.name, payment.repeatRule.monthlyDay,
                payment.paymentTime?.let { it.hour * 60 + it.minute }, records.toString(),
                payment.periodPaid, payment.undoAvailable, payment.currency.name
            )
        }
    }
}

@Entity(tableName = "settings")
data class StoredSettings(
    @PrimaryKey val id: Int = 1,
    val salaryDay: Int = 5,
    val notificationsEnabled: Boolean = true,
    val legacyImported: Boolean = false
)

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY id")
    fun observePayments(): Flow<List<StoredPayment>>

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<StoredSettings?>

    @Query("SELECT * FROM payments ORDER BY id")
    suspend fun allPayments(): List<StoredPayment>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun payment(id: Long): StoredPayment?

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePayment(id: Long)

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): StoredSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePayment(payment: StoredPayment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: StoredSettings)

    @Query("SELECT * FROM notification_history ORDER BY id DESC")
    fun observeNotifications(): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notification_history ORDER BY id DESC")
    suspend fun allNotifications(): List<NotificationRecord>

    @Query("SELECT EXISTS(SELECT 1 FROM notification_history WHERE paymentId = :paymentId AND kind = :kind AND dueAt = :dueAt)")
    suspend fun notificationExists(paymentId: Long, kind: String, dueAt: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(record: NotificationRecord): Long

    @Query("DELETE FROM notification_history WHERE id NOT IN (SELECT id FROM notification_history ORDER BY id DESC LIMIT :limit)")
    suspend fun trimNotifications(limit: Int)

    @Transaction
    suspend fun recordNotification(record: NotificationRecord) {
        insertNotification(record)
        trimNotifications(NotificationRecord.HISTORY_LIMIT)
    }

    @Query("UPDATE notification_history SET isRead = 1 WHERE id <= :throughId AND isRead = 0")
    suspend fun markNotificationsRead(throughId: Long)

    @Query("DELETE FROM notification_history")
    suspend fun clearNotifications()
}

@Database(entities = [StoredPayment::class, StoredSettings::class, NotificationRecord::class],
    version = 4, exportSchema = true)
abstract class PaymentDatabase : RoomDatabase() {
    abstract fun dao(): PaymentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN periodPaid INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payments ADD COLUMN undoAvailable INTEGER NOT NULL DEFAULT 0")
                val updates = mutableListOf<Pair<Long, String>>()
                db.query("SELECT id, historyJson FROM payments").use { cursor ->
                    while (cursor.moveToNext()) {
                        val history = JSONArray(cursor.getString(1))
                        val trimmed = JSONArray()
                        for (index in maxOf(0, history.length() - Payment.HISTORY_LIMIT) until history.length()) {
                            trimmed.put(history.getJSONObject(index))
                        }
                        updates += cursor.getLong(0) to trimmed.toString()
                    }
                }
                updates.forEach { (id, history) ->
                    val paid = if (history == "[]") 0 else 1
                    db.execSQL(
                        "UPDATE payments SET historyJson = ?, periodPaid = ?, undoAvailable = ? WHERE id = ?",
                        arrayOf<Any>(history, paid, paid, id)
                    )
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'TRY'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS notification_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    paymentId INTEGER NOT NULL, kind TEXT NOT NULL, dueAt INTEGER NOT NULL,
                    title TEXT NOT NULL, message TEXT NOT NULL, sentAt INTEGER NOT NULL,
                    isRead INTEGER NOT NULL DEFAULT 0
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_history_paymentId_kind_dueAt ON notification_history (paymentId, kind, dueAt)")
            }
        }

        fun create(context: Context): PaymentDatabase =
            Room.databaseBuilder(context.applicationContext, PaymentDatabase::class.java, "odedim.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
