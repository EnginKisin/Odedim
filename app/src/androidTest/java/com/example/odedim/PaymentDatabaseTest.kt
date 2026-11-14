package com.example.odedim

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentDatabaseTest {
    private lateinit var db: PaymentDatabase

    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), PaymentDatabase::class.java
        ).build()
    }

    @After fun closeDatabase() = db.close()

    @Test fun paymentHistoryRuleAndMidnightRoundTrip() = runBlocking {
        val payment = Payment(
            42, "Fatura", "850.50", LocalDate.of(2026, 12, 31),
            RepeatRule(RepeatType.MONTHLY_DAY, 31),
            listOf(PaymentRecord(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 30))),
            LocalTime.MIDNIGHT
        )
        db.dao().savePayment(StoredPayment.from(payment))
        assertEquals(payment, db.dao().payment(42)?.toPayment())
    }

    @Test fun disabledNotificationsAndSalaryDayAreStored() = runBlocking {
        val settings = StoredSettings(salaryDay = 31, notificationsEnabled = false, legacyImported = true)
        db.dao().saveSettings(settings)
        assertEquals(settings, db.dao().settings())
    }

    @Test fun newPaymentGetsUniqueIdAndLegacyTimeCanRemainUnset() = runBlocking {
        val payment = Payment(0, "Fatura", "100", LocalDate.of(2026, 12, 5))
        val firstId = db.dao().savePayment(StoredPayment.from(payment))
        val secondId = db.dao().savePayment(StoredPayment.from(payment))
        assertNotEquals(firstId, secondId)
        assertTrue(firstId > 0)
        assertNull(db.dao().payment(firstId)?.toPayment()?.paymentTime)
    }

    @Test fun databaseRetainsPaymentsAndPreferencesAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "odedim-test-" + java.util.UUID.randomUUID() + ".db"
        var persistent = Room.databaseBuilder(context, PaymentDatabase::class.java, name).build()
        try {
            val payment = Payment(7, "Kira", "1200", LocalDate.of(2026, 12, 5),
                paymentTime = LocalTime.of(9, 15), currency = PaymentCurrency.EUR)
            val settings = StoredSettings(notificationsEnabled = false, legacyImported = true)
            persistent.dao().savePayment(StoredPayment.from(payment))
            persistent.dao().saveSettings(settings)
            persistent.close()
            persistent = Room.databaseBuilder(context, PaymentDatabase::class.java, name).build()
            assertEquals(payment, persistent.dao().payment(7)?.toPayment())
            assertEquals(settings, persistent.dao().settings())
        } finally {
            persistent.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun editingAndDeletingPersistWithoutLeavingPaymentHistory() = runBlocking {
        val original = Payment(10, "Eski fatura", "100", LocalDate.of(2026, 11, 20),
            paymentTime = LocalTime.NOON).markPaid(LocalDate.of(2026, 11, 20), 5)
        db.dao().savePayment(StoredPayment.from(original))
        val edited = original.edited("Yeni fatura", "250.50", original.repeatRule,
            original.nextDueDate, LocalTime.of(18, 0))
        db.dao().savePayment(StoredPayment.from(edited))
        assertEquals(edited, db.dao().payment(10)?.toPayment())
        db.dao().deletePayment(10)
        assertNull(db.dao().payment(10))
        assertTrue(db.dao().allPayments().isEmpty())
    }

    @Test fun undoRestrictionSurvivesDatabaseRoundTrip() = runBlocking {
        val original = Payment(10, "Fatura", "100", LocalDate.of(2026, 1, 5),
            RepeatRule(RepeatType.MONTHLY_DAY, 5), paymentTime = LocalTime.NOON)
        val first = original.markPaid(original.nextDueDate, 5)
        val second = first.markPaid(first.nextDueDate, 5)
        val undone = second.undoLastPayment(5)
        db.dao().savePayment(StoredPayment.from(undone))
        val restored = requireNotNull(db.dao().payment(10)).toPayment()
        assertFalse(restored.undoAvailable)
        assertEquals(1, restored.history.size)
        assertEquals(restored, restored.undoLastPayment(5))
    }

    @Test fun storageDiscardsOlderHistoryRatherThanOnlyHidingIt() = runBlocking {
        val records = List(30) { index ->
            PaymentRecord(LocalDate.of(2020, 1, 1).plusMonths(index.toLong()),
                LocalDate.of(2020, 1, 1).plusMonths(index.toLong()))
        }
        val payment = Payment(10, "Fatura", "100", LocalDate.of(2026, 12, 5), history = records)
        db.dao().savePayment(StoredPayment.from(payment))
        val stored = requireNotNull(db.dao().payment(10))
        assertEquals(24, org.json.JSONArray(stored.historyJson).length())
        assertEquals(records.takeLast(24), stored.toPayment().history)
    }

    @Test fun versionOneUpgradeKeepsDataAndTrimsHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "odedim-migration-" + java.util.UUID.randomUUID() + ".db"
        val records = org.json.JSONArray()
        repeat(30) { index ->
            records.put(org.json.JSONObject()
                .put("paid", LocalDate.of(2020, 1, 1).plusMonths(index.toLong()).toEpochDay())
                .put("due", LocalDate.of(2020, 1, 1).plusMonths(index.toLong()).toEpochDay()))
        }
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, amount TEXT NOT NULL, dueEpochDay INTEGER NOT NULL, repeatType TEXT NOT NULL, monthlyDay INTEGER NOT NULL, minuteOfDay INTEGER, historyJson TEXT NOT NULL)")
            old.execSQL("CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, salaryDay INTEGER NOT NULL, notificationsEnabled INTEGER NOT NULL, legacyImported INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES (42, '5f305999f0b8924e23b9016a9f147bfb')")
            old.execSQL("INSERT INTO payments VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(42, "Kira", "1500", LocalDate.of(2026, 12, 5).toEpochDay(),
                    "SALARY_DAY", 5, 18 * 60 + 30, records.toString()))
            old.execSQL("INSERT INTO settings VALUES (1, 31, 0, 1)")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, PaymentDatabase::class.java, name)
            .addMigrations(PaymentDatabase.MIGRATION_1_2, PaymentDatabase.MIGRATION_2_3, PaymentDatabase.MIGRATION_3_4).build()
        try {
            val stored = requireNotNull(upgraded.dao().payment(42))
            assertEquals(PaymentCurrency.TRY, stored.toPayment().currency)
            assertEquals("Kira", stored.name)
            assertEquals("1500", stored.amount)
            assertEquals(LocalTime.of(18, 30), stored.toPayment().paymentTime)
            assertEquals(24, org.json.JSONArray(stored.historyJson).length())
            assertEquals(LocalDate.of(2020, 7, 1), stored.toPayment().history.first().paidOn)
            assertTrue(stored.periodPaid)
            assertTrue(stored.undoAvailable)
            assertEquals(StoredSettings(salaryDay = 31, notificationsEnabled = false, legacyImported = true),
                upgraded.dao().settings())
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun currenciesRoundTripIndependently() = runBlocking {
        PaymentCurrency.entries.forEach { currency ->
            val payment = Payment(currency.ordinal + 1L, "Abonelik", "9.99",
                LocalDate.of(2026, 12, 5), paymentTime = LocalTime.NOON, currency = currency)
            db.dao().savePayment(StoredPayment.from(payment))
            assertEquals(payment, db.dao().payment(payment.id)?.toPayment())
        }
        assertEquals(listOf("TRY", "USD", "EUR"), db.dao().allPayments().map { it.currencyCode })
    }

    @Test fun versionTwoUpgradeDefaultsExistingPaymentsToTryWithoutChangingOtherData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "odedim-currency-migration-" + java.util.UUID.randomUUID() + ".db"
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, amount TEXT NOT NULL, dueEpochDay INTEGER NOT NULL, repeatType TEXT NOT NULL, monthlyDay INTEGER NOT NULL, minuteOfDay INTEGER, historyJson TEXT NOT NULL, periodPaid INTEGER NOT NULL DEFAULT 0, undoAvailable INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, salaryDay INTEGER NOT NULL, notificationsEnabled INTEGER NOT NULL, legacyImported INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES (42, 'fc64ceaec4a0e8c8a0e14fc28ad28471')")
            old.execSQL("INSERT INTO payments VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(5, "Fatura", "850.50", LocalDate.of(2026, 12, 5).toEpochDay(),
                    "MONTHLY_DAY", 5, 720, "[]", 1, 0))
            old.execSQL("INSERT INTO settings VALUES (1, 5, 1, 1)")
            old.version = 2
        }
        val upgraded = Room.databaseBuilder(context, PaymentDatabase::class.java, name)
            .addMigrations(PaymentDatabase.MIGRATION_2_3, PaymentDatabase.MIGRATION_3_4).build()
        try {
            val stored = requireNotNull(upgraded.dao().payment(5))
            assertEquals("TRY", stored.currencyCode)
            val payment = stored.toPayment()
            assertEquals("Fatura", payment.name)
            assertEquals("850.50", payment.amount)
            assertEquals(LocalDate.of(2026, 12, 5), payment.nextDueDate)
            assertEquals(LocalTime.NOON, payment.paymentTime)
            assertTrue(payment.periodPaid)
            assertFalse(payment.undoAvailable)
            assertEquals(StoredSettings(legacyImported = true), upgraded.dao().settings())
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun paymentSnapshotsSurviveStorageAndLaterEdits() = runBlocking {
        val paid = Payment(15, "Abonelik", "9.99", LocalDate.of(2026, 11, 14),
            paymentTime = LocalTime.NOON, currency = PaymentCurrency.USD)
            .markPaid(LocalDate.of(2026, 11, 14), 5)
        val edited = paid.edited("Yeni abonelik", "100.00", paid.repeatRule,
            paid.nextDueDate, LocalTime.of(18, 0), PaymentCurrency.EUR)
        db.dao().savePayment(StoredPayment.from(edited))
        val restored = requireNotNull(db.dao().payment(15)).toPayment()
        assertEquals(edited, restored)
        assertEquals(PaymentCurrency.EUR, restored.currency)
        assertEquals(PaymentCurrency.USD, restored.history.single().currency)
        assertEquals("9.99", restored.history.single().amount)
    }

    @Test fun legacyHistoryDoesNotInheritCurrentAmountOrCurrency() = runBlocking {
        val paid = LocalDate.of(2026, 11, 14).toEpochDay()
        val raw = org.json.JSONArray().put(org.json.JSONObject().put("paid", paid).put("due", paid))
        val payment = Payment(16, "Eski ödeme", "999.00", LocalDate.of(2026, 12, 14),
            currency = PaymentCurrency.EUR)
        db.dao().savePayment(StoredPayment.from(payment).copy(historyJson = raw.toString()))
        val restored = requireNotNull(db.dao().payment(16)).toPayment()
        assertEquals(PaymentCurrency.EUR, restored.currency)
        assertNull(restored.history.single().amount)
        assertNull(restored.history.single().currency)
        assertFalse(restored.history.single().hasAmountSnapshot)
    }


    @Test fun manuallyCompletedLegacyAmountSurvivesRoomAndJsonRoundTrip() = runBlocking {
        val old = PaymentRecord(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5))
        val payment = Payment(18, "Eski ödeme", "999.00", LocalDate.of(2026, 11, 5),
            history = listOf(old), currency = PaymentCurrency.TRY, undoAvailable = false)
        db.dao().savePayment(StoredPayment.from(payment))
        val restored = requireNotNull(db.dao().payment(18)).toPayment()
        val completed = restored.completeHistoryAmount(0, old, "12.34", PaymentCurrency.USD)
        db.dao().savePayment(StoredPayment.from(completed))
        val reloaded = requireNotNull(db.dao().payment(18)).toPayment()
        assertEquals(completed, reloaded)
        assertEquals(PaymentCurrency.TRY, reloaded.currency)
        assertEquals(PaymentCurrency.USD, reloaded.history.single().currency)
        assertEquals("12.34", reloaded.history.single().amount)
        assertFalse(reloaded.undoAvailable)
        assertEquals(payment.nextDueDate, reloaded.nextDueDate)
    }

    @Test fun unpaidFirstDueSurvivesStorageAndCanBePaidAndUndone() = runBlocking {
        val today = LocalDate.of(2026, 11, 14)
        val due = today.plusDays(10)
        val original = createPayment(19, "İlk ödeme", "15.00", RepeatRule(), null,
            LocalTime.of(19, 0), today, 5, PaymentCurrency.EUR, due)
        db.dao().savePayment(StoredPayment.from(original))
        val restored = requireNotNull(db.dao().payment(19)).toPayment()
        assertEquals(original, restored)
        assertTrue(restored.history.isEmpty())
        assertFalse(restored.periodPaid)
        assertFalse(restored.undoAvailable)
        val paid = restored.markPaid(due, 5)
        db.dao().savePayment(StoredPayment.from(paid))
        val reloaded = requireNotNull(db.dao().payment(19)).toPayment()
        assertEquals(due.plusDays(30), reloaded.nextDueDate)
        assertEquals(original, reloaded.undoLastPayment(5))
    }

    @Test fun knownAndUnknownHistoryCanCoexistWithoutBackfilling() = runBlocking {
        val payment = Payment(17, "Fatura", "100.00", LocalDate.of(2026, 12, 14), history = listOf(
            PaymentRecord(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14)),
            PaymentRecord(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14), "50.00", PaymentCurrency.TRY)
        ))
        db.dao().savePayment(StoredPayment.from(payment))
        assertEquals(payment, db.dao().payment(17)?.toPayment())
    }
}
