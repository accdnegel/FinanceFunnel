package com.pitaka.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PitakaRepositoryAccountingIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PitakaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PitakaRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun expenseCannotExceedFunnelLimit() = runBlocking {
        val pitakaId = repository.createPitaka("Wallet", 1000.0, "PHP", null)
        val funnelId = repository.createExpenseFunnel("Food", 100.0, null, null, null)

        repository.recordExpense(pitakaId, "Lunch", 100.0, "Food", funnelId)

        // Editing the existing 100 PHP expense must be allowed when its replacement
        // remains within the same 100 PHP funnel limit.
        val entry = db.ledgerDao().observeAllEntries().first().single()
        repository.updateEntry(entry, "Lunch corrected", 100.0, "Food", pitakaId)

        // The limit should also allow a smaller replacement after the old allocation
        // has already been reversed inside the edit transaction.
        repository.updateEntry(entry.copy(name = "Lunch corrected"), "Lunch smaller", 90.0, "Food", pitakaId)

        var failed = false
        try {
            repository.recordExpense(pitakaId, "Dinner", 1.0, "Food", funnelId)
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertEquals(true, failed)
        assertEquals("PHP=100", db.expenseFunnelDao().get(funnelId)!!.currencyBalances)
    }

    @Test
    fun goalContributionCannotExceedTarget() = runBlocking {
        val pitakaId = repository.createPitaka("Savings Source", 1000.0, "PHP", null)
        val goalId = repository.createGoal(
            name = "Emergency",
            type = GoalType.SAVINGS,
            targetAmount = 100.0,
            targetDate = LocalDate.now().plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            colorHex = null
        )

        repository.recordGoalContribution(pitakaId, goalId, "Contribution", 100.0)

        var failed = false
        try {
            repository.recordGoalContribution(pitakaId, goalId, "Overflow", 1.0)
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertEquals(true, failed)
        assertEquals("PHP=100", db.goalDao().getGoal(goalId)!!.currencyBalances)
    }

    @Test
    fun expenseOutsideFunnelValidityPeriodIsRejected() = runBlocking {
        val pitakaId = repository.createPitaka("Wallet", 1000.0, "PHP", null)
        val funnelId = repository.createExpenseFunnel(
            name = "Travel",
            limit = 500.0,
            validFrom = LocalDate.now().plusDays(1),
            validUntil = LocalDate.now().plusDays(10),
            currency = "PHP"
        )

        var failed = false
        try {
            repository.recordExpense(pitakaId, "Today", 10.0, "Travel", funnelId, date = System.currentTimeMillis())
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertEquals(true, failed)
    }

    @Test
    fun goalContributionCannotExceedTargetAtCreation() = runBlocking {
        val pitakaId = repository.createPitaka("Savings", 1000.0, "PHP", null)
        val goalId = repository.createGoal("Emergency", GoalType.SAVINGS, 100.0, LocalDate.now().plusMonths(1).toEpochDay(), null)

        var failed = false
        try {
            repository.recordGoalContribution(pitakaId, goalId, "Overflow", 101.0)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertEquals(true, failed)
    }

    @Test
    fun archivedPitakaRejectsNewMoneyMovement() = runBlocking {
        val pitakaId = repository.createPitaka("Archived", 500.0, "PHP", null)
        repository.archivePitaka(pitakaId)

        var incomeFailed = false
        try {
            repository.recordIncome(pitakaId, "Salary", 100.0)
        } catch (_: IllegalArgumentException) {
            incomeFailed = true
        }

        var expenseFailed = false
        try {
            repository.recordExpense(pitakaId, "Lunch", 10.0, null)
        } catch (_: IllegalArgumentException) {
            expenseFailed = true
        }

        assertEquals(true, incomeFailed)
        assertEquals(true, expenseFailed)
    }

    @Test
    fun incomeApplyAndDeleteRestorePitakaBalance() = runBlocking {
        val pitakaId = repository.createPitaka("Income", 0.0, "PHP", null)
        repository.recordIncome(pitakaId, "Salary", 1000.0)

        assertEquals(1000.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)

        val entry = db.ledgerDao().getAllEntriesOnce().single()
        repository.deleteEntry(entry)

        assertEquals(0.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
    }

    @Test
    fun expenseAndFunnelApplyAndDeleteRestoreBothBalances() = runBlocking {
        val pitakaId = repository.createPitaka("Wallet", 1000.0, "PHP", null)
        val funnelId = repository.createExpenseFunnel("Food", 500.0, null, null, null)

        repository.recordExpense(
            pitakaId = pitakaId,
            name = "Lunch",
            amount = 125.0,
            category = "Food",
            funnelId = funnelId
        )

        assertEquals(875.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)

        val funnel = db.expenseFunnelDao().get(funnelId)!!
        assertEquals("PHP=125", funnel.currencyBalances)

        val entry = db.ledgerDao().getAllEntriesOnce().single()
        repository.deleteEntry(entry)

        assertEquals(1000.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
        assertEquals("PHP=0", db.expenseFunnelDao().get(funnelId)!!.currencyBalances)
    }

    @Test
    fun goalContributionApplyAndDeleteRestoreSourceAndGoal() = runBlocking {
        val pitakaId = repository.createPitaka("Savings Source", 1000.0, "PHP", null)
        val goalId = repository.createGoal(
            name = "Emergency",
            type = GoalType.SAVINGS,
            targetAmount = 5000.0,
            targetDate = LocalDate.now().plusMonths(1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            colorHex = null
        )

        repository.recordGoalContribution(
            sourcePitakaId = pitakaId,
            goalId = goalId,
            name = "Emergency contribution",
            amount = 200.0
        )

        assertEquals(800.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
        assertEquals("PHP=200", db.goalDao().getGoal(goalId)!!.currencyBalances)

        val entry = db.ledgerDao().getAllEntriesOnce().single()
        repository.deleteEntry(entry)

        assertEquals(1000.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
        assertEquals("PHP=0", db.goalDao().getGoal(goalId)!!.currencyBalances)
    }

    @Test
    fun expenseEditScalesFunnelAllocationAndRestoresOnDelete() = runBlocking {
        val pitakaId = repository.createPitaka("Wallet", 1000.0, "PHP", null)
        val funnelId = repository.createExpenseFunnel("Travel", 500.0, null, null, null)

        repository.recordExpense(pitakaId, "Taxi", 100.0, "Transport", funnelId)
        val entry = db.ledgerDao().getAllEntriesOnce().single()

        repository.updateEntry(entry, "Taxi", 150.0, "Transport")

        assertEquals(850.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
        assertEquals("PHP=150", db.expenseFunnelDao().get(funnelId)!!.currencyBalances)

        val updated = db.ledgerDao().getAllEntriesOnce().single()
        repository.deleteEntry(updated)

        assertEquals(1000.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
        assertEquals("PHP=0", db.expenseFunnelDao().get(funnelId)!!.currencyBalances)
    }

    @Test
    fun goalContributionEditScalesGoalAllocationAndRestoresOnDelete() = runBlocking {
        val pitakaId = repository.createPitaka("Savings Source", 1000.0, "PHP", null)
        val goalId = repository.createGoal(
            name = "Emergency",
            type = GoalType.SAVINGS,
            targetAmount = 5000.0,
            targetDate = LocalDate.now().plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            colorHex = null
        )

        repository.recordGoalContribution(pitakaId, goalId, "Contribution", 200.0)
        val entry = db.ledgerDao().getAllEntriesOnce().single()

        repository.updateEntry(entry, "Contribution", 300.0, null)

        assertEquals(700.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
        assertEquals("PHP=300", db.goalDao().getGoal(goalId)!!.currencyBalances)

        val updated = db.ledgerDao().getAllEntriesOnce().single()
        repository.deleteEntry(updated)

        assertEquals(1000.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
        assertEquals("PHP=0", db.goalDao().getGoal(goalId)!!.currencyBalances)
    }

    @Test
    fun crossCurrencyTransferEditScalesDestinationAndReversesCleanly() = runBlocking {
        db.currencyDao().upsertRate(ExchangeRate(code = "USD", rateToBase = 58.0))
        val sourceId = repository.createPitaka("USD Source", 100.0, "USD", null)
        val destinationId = repository.createPitaka("PHP Destination", 0.0, "PHP", null)

        repository.recordTransfer(sourceId, destinationId, "Convert", 10.0, secondaryAmount = 580.0)
        val entry = db.ledgerDao().getAllEntriesOnce().single()

        repository.updateEntry(entry, "Convert", 15.0, null)

        assertEquals("USD=85", db.pitakaDao().getPitaka(sourceId)!!.currencyBalances)
        assertEquals("PHP=870", db.pitakaDao().getPitaka(destinationId)!!.currencyBalances)
        assertEquals(870.0, db.ledgerDao().getAllEntriesOnce().single().secondaryAmount!!, 1e-9)

        repository.deleteEntry(db.ledgerDao().getAllEntriesOnce().single())

        assertEquals("USD=100", db.pitakaDao().getPitaka(sourceId)!!.currencyBalances)
        assertEquals("PHP=0", db.pitakaDao().getPitaka(destinationId)!!.currencyBalances)
    }

    @Test
    fun manualAdjustmentApplyAndDeleteRestoresBalance() = runBlocking {
        val pitakaId = repository.createPitaka("Cash", 500.0, "PHP", null)

        repository.adjustPitakaBalanceManually(
            pitakaId = pitakaId,
            newBalance = 725.0,
            note = "Cash count reconciliation"
        )

        assertEquals(725.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)

        val entry = db.ledgerDao().getAllEntriesOnce().single()
        assertEquals(LedgerType.ADJUSTMENT, entry.type)

        repository.deleteEntry(entry)

        assertEquals(500.0, db.pitakaDao().getPitaka(pitakaId)!!.currentAmount, 1e-9)
    }

    @Test
    fun sameCurrencyTransferApplyAndDeleteRestoresBothPitakas() = runBlocking {
        val sourceId = repository.createPitaka("Source", 1000.0, "PHP", null)
        val destinationId = repository.createPitaka("Destination", 100.0, "PHP", null)

        repository.recordTransfer(
            fromPitakaId = sourceId,
            toPitakaId = destinationId,
            name = "Move funds",
            amount = 250.0
        )

        assertEquals(750.0, db.pitakaDao().getPitaka(sourceId)!!.currentAmount, 1e-9)
        assertEquals(350.0, db.pitakaDao().getPitaka(destinationId)!!.currentAmount, 1e-9)

        val entry = db.ledgerDao().getAllEntriesOnce().single()
        repository.deleteEntry(entry)

        assertEquals(1000.0, db.pitakaDao().getPitaka(sourceId)!!.currentAmount, 1e-9)
        assertEquals(100.0, db.pitakaDao().getPitaka(destinationId)!!.currentAmount, 1e-9)
    }

    @Test
    fun crossCurrencyTransferApplyAndDeleteRestoresBothCurrencyBalances() = runBlocking {
        db.currencyDao().upsertRate(ExchangeRate(code = "USD", rateToBase = 58.0))
        val sourceId = repository.createPitaka("USD Source", 100.0, "USD", null)
        val destinationId = repository.createPitaka("PHP Destination", 0.0, "PHP", null)

        repository.recordTransfer(
            fromPitakaId = sourceId,
            toPitakaId = destinationId,
            name = "Convert funds",
            amount = 10.0,
            secondaryAmount = 580.0
        )

        val source = db.pitakaDao().getPitaka(sourceId)!!
        val destination = db.pitakaDao().getPitaka(destinationId)!!
        assertEquals("USD=90", source.currencyBalances)
        assertEquals("PHP=580", destination.currencyBalances)

        val entry = db.ledgerDao().getAllEntriesOnce().single()
        assertNotNull(entry.secondaryAmount)
        assertEquals("PHP", entry.secondaryCurrency)

        repository.deleteEntry(entry)

        assertEquals("USD=100", db.pitakaDao().getPitaka(sourceId)!!.currencyBalances)
        assertEquals("PHP=0", db.pitakaDao().getPitaka(destinationId)!!.currencyBalances)
    }
}
