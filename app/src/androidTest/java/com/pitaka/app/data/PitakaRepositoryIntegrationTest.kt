package com.pitaka.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PitakaRepositoryIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: PitakaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PitakaRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingFunnelPreservesCrossCurrencyAllocationInGeneralExpenses() = runBlocking {
        repository.setBaseCurrency("USD")
        val sourceId = repository.createPitaka("USD wallet", 100.0, "USD", null)
        val funnelId = repository.createExpenseFunnel("Travel", 1_000.0, null, null, null, currency = "PHP")

        repository.recordExpense(
            pitakaId = sourceId,
            name = "Airport transfer",
            amount = 10.0,
            category = "Transport",
            funnelId = funnelId,
            currency = "USD",
            funnelAmount = 580.0,
            funnelCurrency = "PHP"
        )

        repository.deleteExpenseFunnel(requireNotNull(database.expenseFunnelDao().get(funnelId)))

        val general = repository.getGeneralExpensesFunnel()
        val reassigned = database.ledgerDao().getAllEntriesOnce().single { it.type == LedgerType.EXPENSE }
        assertEquals(general.id, reassigned.funnelId)
        assertEquals(580.0, reassigned.funnelAmount!!, 1e-9)
        assertEquals("PHP", reassigned.funnelCurrency)
        assertEquals(580.0, CurrencyBalances.parse(general.currencyBalances)["PHP"]!!, 1e-9)
    }

    @Test
    fun deletingGoalRefundsMissingContributionSourceToSelectedPitaka() = runBlocking {
        repository.setBaseCurrency("PHP")
        val originalSourceId = repository.createPitaka("Old wallet", 100.0, "PHP", null)
        val refundId = repository.createPitaka("Refund wallet", 0.0, "PHP", null)
        val goalId = repository.createGoal("Emergency fund", GoalType.SAVINGS, 100.0, null, null)
        repository.recordGoalContribution(originalSourceId, goalId, "Contribution", 40.0)

        database.pitakaDao().deletePitaka(requireNotNull(database.pitakaDao().getPitaka(originalSourceId)))
        repository.deleteGoal(requireNotNull(repository.getGoal(goalId)), refundId)

        val refund = requireNotNull(database.pitakaDao().getPitaka(refundId))
        assertEquals(40.0, CurrencyBalances.parse(refund.currencyBalances)["PHP"]!!, 1e-9)
        assertNull(repository.getGoal(goalId))
        assertEquals(0, database.ledgerDao().getEntriesForGoalOnce(goalId).size)
    }

    @Test
    fun reclassifyingExistingPitakaPreservesParentBalancesAndLedgerHistory() = runBlocking {
        val parentId = repository.createPitaka("Accounts", 100.0, "PHP", null)
        val childId = repository.createPitaka("Travel wallet", 25.0, "USD", null)

        repository.updatePitakaMeta(
            pitakaId = childId,
            name = "Travel wallet",
            currency = "USD",
            colorHex = null,
            parentPitakaId = parentId
        )

        val parent = requireNotNull(database.pitakaDao().getPitaka(parentId))
        val child = requireNotNull(database.pitakaDao().getPitaka(childId))
        val childBalances = CurrencyBalances.parse(child.currencyBalances)
        assertEquals(parentId, child.parentPitakaId)
        assertEquals(0.0, parent.currentAmount, 1e-9)
        assertEquals(25.0, childBalances["USD"]!!, 1e-9)
        assertEquals(100.0, childBalances["PHP"]!!, 1e-9)
        assertEquals(2, database.ledgerDao().getEntriesForPitakaOnce(childId).size)
        assertEquals(0, database.ledgerDao().getEntriesForPitakaOnce(parentId).size)
    }
}