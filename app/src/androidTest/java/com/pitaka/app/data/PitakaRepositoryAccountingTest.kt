package com.pitaka.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PitakaRepositoryAccountingTest {

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
    fun incomeExpenseEditDeleteRoundTripRestoresBalance() = runBlocking {
        val pitakaId = repository.createPitaka("Wallet", 0.0, "PHP", null)
        repository.recordIncome(pitakaId, "Salary", 10_000.0)

        val income = repository.getAllEntriesOnce().single()
        assertEquals(10_000.0, db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances.let { CurrencyBalances.parse(it)["PHP"] ?: 0.0 }, 0.0)

        val funnelId = repository.createExpenseFunnel("Food", 4_000.0, null, null, null, currency = "PHP")
        repository.recordExpense(pitakaId, "Lunch", 2_000.0, "Food", funnelId)

        val expense = repository.getAllEntriesOnce().first { it.type == LedgerType.EXPENSE }
        assertEquals(2_000.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)

        repository.updateEntry(expense, "Lunch edited", 1_000.0, "Food")
        val editedBalance = CurrencyBalances.parse(db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances)["PHP"] ?: 0.0
        assertEquals(9_000.0, editedBalance, 0.0)

        val funnel = db.expenseFunnelDao().get(funnelId)!!
        assertEquals(1_000.0, CurrencyBalances.parse(funnel.currencyBalances)["PHP"] ?: 0.0, 0.0)

        repository.deleteEntry(expense.copy(
            name = "Lunch edited",
            amount = 1_000.0,
            funnelAmount = 1_000.0,
            funnelCurrency = "PHP"
        ))

        val restored = CurrencyBalances.parse(db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances)["PHP"] ?: 0.0
        assertEquals(10_000.0, restored, 0.0)
        assertEquals(0.0, CurrencyBalances.parse(db.expenseFunnelDao().get(funnelId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)
        assertTrue(repository.getAllEntriesOnce().none { it.type == LedgerType.EXPENSE })
        assertEquals(1, repository.getAllEntriesOnce().count { it == income })
    }

    @Test
    fun goalContributionAndDeletionRestoreSourceAndGoalOnReverse() = runBlocking {
        val pitakaId = repository.createPitaka("Bank", 5_000.0, "PHP", null)
        val goalId = repository.createGoal("Emergency Fund", GoalType.SAVINGS, 10_000.0, 0L, null, currency = "PHP")

        repository.recordGoalContribution(pitakaId, goalId, "Emergency fund", 1_500.0)
        val contribution = repository.getAllEntriesOnce().single()

        assertEquals(3_500.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)
        assertEquals(1_500.0, CurrencyBalances.parse(db.goalDao().getGoal(goalId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)

        repository.deleteEntry(contribution)

        assertEquals(5_000.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)
        assertEquals(0.0, CurrencyBalances.parse(db.goalDao().getGoal(goalId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)
        assertTrue(repository.getAllEntriesOnce().isEmpty())
    }

    @Test
    fun crossCurrencyTransferReverseRestoresBothCurrencyBalances() = runBlocking {
        val sourceId = repository.createPitaka("PHP Bank", 10_000.0, "PHP", null)
        val destinationId = repository.createPitaka("USD Bank", 0.0, "USD", null)

        repository.setBaseCurrency("PHP")
        repository.setExchangeRate("USD", 50.0)
        repository.recordTransfer(sourceId, destinationId, "Savings transfer", 5_000.0, 100.0)

        val transfer = repository.getAllEntriesOnce().single()
        assertEquals(5_000.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(sourceId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)
        assertEquals(100.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(destinationId)!!.currencyBalances)["USD"] ?: 0.0, 0.0)

        repository.deleteEntry(transfer)

        assertEquals(10_000.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(sourceId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)
        assertEquals(0.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(destinationId)!!.currencyBalances)["USD"] ?: 0.0, 0.0)
    }

    @Test
    fun manualAdjustmentChangesOnlySelectedCurrency() = runBlocking {
        val pitakaId = repository.createPitaka("Multi-currency wallet", 1_000.0, "PHP", null)
        repository.recordIncome(pitakaId, "USD top-up", 20.0)

        val pitakaBefore = db.pitakaDao().getPitaka(pitakaId)!!
        assertEquals(1_000.0, CurrencyBalances.parse(pitakaBefore.currencyBalances)["PHP"] ?: 0.0, 0.0)

        repository.adjustPitakaBalanceManually(pitakaId, 50.0, "Reconcile USD", "USD")

        val balances = CurrencyBalances.parse(db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances)
        assertEquals(1_000.0, balances["PHP"] ?: 0.0, 0.0)
        assertEquals(50.0, balances["USD"] ?: 0.0, 0.0)
    }

    @Test
    fun recurringRuleCatchUpPostsOnceAndClampsShortMonths() = runBlocking {
        val pitakaId = repository.createPitaka("Salary", 0.0, "PHP", null)
        repository.createRecurringRule(
            type = LedgerType.INCOME,
            name = "Monthly salary",
            amount = 50_000.0,
            category = null,
            pitakaId = pitakaId,
            dayOfMonth = 31
        )

        repository.applyDueRecurringRules(java.time.LocalDate.of(2026, 2, 28))

        val entries = repository.getAllEntriesOnce()
        assertEquals(1, entries.size)
        assertEquals(LedgerType.INCOME, entries.single().type)
        assertEquals(java.time.LocalDate.of(2026, 2, 28).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), entries.single().date)
        assertEquals(50_000.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances)["PHP"] ?: 0.0, 0.0)

        // Reopening the app in the same month must not post the rule again.
        repository.applyDueRecurringRules(java.time.LocalDate.of(2026, 2, 28))
        assertEquals(1, repository.getAllEntriesOnce().size)
    }

    @Test
    fun recurringExpenseUsesPitakaCurrencyAndCanBeDisabled() = runBlocking {
        val pitakaId = repository.createPitaka("USD Wallet", 100.0, "USD", null)
        repository.createRecurringRule(
            type = LedgerType.EXPENSE,
            name = "Cloud storage",
            amount = 10.0,
            category = "Bills",
            pitakaId = pitakaId,
            dayOfMonth = 15
        )

        repository.applyDueRecurringRules(java.time.LocalDate.of(2026, 9, 15))
        val expense = repository.getAllEntriesOnce().single()
        assertEquals(LedgerType.EXPENSE, expense.type)
        assertEquals("USD", expense.currency)
        assertEquals(90.0, CurrencyBalances.parse(db.pitakaDao().getPitaka(pitakaId)!!.currencyBalances)["USD"] ?: 0.0, 0.0)

        val rule = db.recurringRuleDao().getActiveRulesOnce().single()
        repository.setRecurringRuleActive(rule, false)
        repository.applyDueRecurringRules(java.time.LocalDate.of(2026, 10, 15))
        assertEquals(1, repository.getAllEntriesOnce().size)
    }

    @Test
    fun recurringRuleBeforeDueDateDoesNotPost() = runBlocking {
        val pitakaId = repository.createPitaka("Wallet", 0.0, "PHP", null)
        repository.createRecurringRule(
            type = LedgerType.INCOME,
            name = "Allowance",
            amount = 1_000.0,
            category = null,
            pitakaId = pitakaId,
            dayOfMonth = 20
        )

        repository.applyDueRecurringRules(java.time.LocalDate.of(2026, 9, 19))
        assertTrue(repository.getAllEntriesOnce().isEmpty())

        repository.applyDueRecurringRules(java.time.LocalDate.of(2026, 9, 20))
        assertEquals(1, repository.getAllEntriesOnce().size)
    }
}

