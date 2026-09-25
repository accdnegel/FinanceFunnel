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
