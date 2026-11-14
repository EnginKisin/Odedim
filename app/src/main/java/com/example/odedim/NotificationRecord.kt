package com.example.odedim

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_history",
    indices = [Index(value = ["paymentId", "kind", "dueAt"], unique = true)]
)
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val kind: String,
    val dueAt: Long,
    val title: String,
    val message: String,
    val sentAt: Long,
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false
) {
    companion object {
        const val HISTORY_LIMIT = 10
    }
}
