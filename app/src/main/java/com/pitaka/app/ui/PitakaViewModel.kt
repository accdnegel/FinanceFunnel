package com.pitaka.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pitaka.app.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.YearMonth

/** A month's income vs. expense — the only two things that actually change total net worth. */
data class MonthlyNetChange(
    val month: String,
    val income: Double,
    val expense: Double,
    val net: Double
)

class PitakaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PitakaRepository(AppDatabase.getInstance(application))

    init {
        // Catch up on any recurring income/expenses due since the app was last opened.
        viewModelScope.launch { repository.applyDueRecurringRules() }
    }

    // ---- Core data ----

    val pitakas: Flow<List<Pitaka>> = repository.observePitakas()

    val goals: Flow<List<GoalWithProgress>> = repository.observeGoals()

    // ---- Currency ----

    val currencySettings: Flow<CurrencySettings?> = repository.observeCurrencySettings()
    val exchangeRates: Flow<List<ExchangeRate>> = repository.observeExchangeRates()

    fun setBaseCurrency(code: String) {
        viewModelScope.launch { repository.setBaseCurrency(code) }
    }

    fun setExchangeRate(code: String, rateToBase: Double) {
        viewModelScope.launch { repository.setExchangeRate(code, rateToBase) }
    }

    fun deleteExchangeRate(code: String) {
        viewModelScope.launch { repository.deleteExchangeRate(code) }
    }

    /** Liquid total (all Pitakas), converted to the base/display currency. */
    val totalLiquid: Flow<Double> = combine(pitakas, exchangeRates, currencySettings) { list, rates, settings ->
        val base = settings?.baseCurrency ?: "USD"
        list.sumOf { p -> convert(p.currentAmount, p.currency, base, rates) }
    }

    val totalSavingsProgress: Flow<Double> = repository.observeTotalProgressForType(GoalType.SAVINGS)
    val totalInvestmentProgress: Flow<Double> = repository.observeTotalProgressForType(GoalType.INVESTMENT)

    // NOTE: Goal progress is currently tracked as a raw number without its own currency —
    // if you fund one Goal from Pitakas of different currencies, its progress figure mixes
    // currencies as-is. Flagged as a known v1 simplification.
    val totalNetWorth: Flow<Double> = combine(
        totalLiquid, totalSavingsProgress, totalInvestmentProgress
    ) { liquid, savings, investments -> liquid + savings + investments }

    private fun convert(amount: Double, from: String, to: String, rates: List<ExchangeRate>): Double {
        if (from == to) return amount
        val fromRate = rates.find { it.code == from }?.rateToBase ?: 1.0
        val toRate = rates.find { it.code == to }?.rateToBase ?: 1.0
        // Both rates are "1 unit of code = rateToBase units of base currency".
        return amount * fromRate / toRate
    }

    // ---- Monthly expense budgets ----

    val currentMonthKey: String = YearMonth.now().toString()

    val currentMonthBudget: Flow<MonthlyBudget?> = repository.observeEffectiveBudget(currentMonthKey)
    val allBudgets: Flow<List<MonthlyBudget>> = repository.observeAllBudgets()

    val currentMonthExpenseTotal: Flow<Double> = repository.observeExpenseTotalForMonth(currentMonthKey)

    suspend fun getExactBudgetForMonth(month: String): MonthlyBudget? = repository.getExactBudgetForMonth(month)

    fun setMonthlyExpenseLimit(month: String, limit: Double?) {
        viewModelScope.launch { repository.setMonthlyExpenseLimit(month, limit) }
    }

    // ---- Statistics ----

    val monthlyExpenses: Flow<List<MonthlyAmount>> = repository.observeMonthlyExpenses()
    val monthlyIncome: Flow<List<MonthlyAmount>> = repository.observeMonthlyIncome()

    val monthlyNetChange: Flow<List<MonthlyNetChange>> = combine(
        monthlyIncome, monthlyExpenses
    ) { income, expenses ->
        val months = (income.map { it.month } + expenses.map { it.month }).toSortedSet()
        months.map { month ->
            val inc = income.find { it.month == month }?.total ?: 0.0
            val exp = expenses.find { it.month == month }?.total ?: 0.0
            MonthlyNetChange(month, inc, exp, inc - exp)
        }
    }

    val expenseBreakdown: Flow<List<CategorySpend>> = repository.observeExpenseBreakdown()
    val expenseCategories: Flow<List<String>> = repository.observeExpenseCategories()
    val availableMonths: Flow<List<String>> = repository.observeAvailableMonths()

    fun expenseBreakdownForMonth(month: String): Flow<List<CategorySpend>> =
        repository.observeExpenseBreakdownForMonth(month)

    fun entriesForPitaka(id: Long): Flow<List<LedgerEntry>> = repository.observeEntriesForPitaka(id)
    fun entriesForGoal(id: Long): Flow<List<LedgerEntry>> = repository.observeEntriesForGoal(id)
    val allExpenses: Flow<List<LedgerEntry>> = repository.observeAllExpenses()

    suspend fun getPitaka(id: Long): Pitaka? = repository.getPitaka(id)
    suspend fun getGoal(id: Long): Goal? = repository.getGoal(id)
    suspend fun getAllEntriesOnce(): List<LedgerEntry> = repository.getAllEntriesOnce()

    // ---- Recurring rules ----

    val recurringRules: Flow<List<RecurringRule>> = repository.observeRecurringRules()

    fun createRecurringRule(type: LedgerType, name: String, amount: Double, category: String?, pitakaId: Long, dayOfMonth: Int) {
        viewModelScope.launch { repository.createRecurringRule(type, name, amount, category, pitakaId, dayOfMonth) }
    }

    fun setRecurringRuleActive(rule: RecurringRule, active: Boolean) {
        viewModelScope.launch { repository.setRecurringRuleActive(rule, active) }
    }

    fun deleteRecurringRule(rule: RecurringRule) {
        viewModelScope.launch { repository.deleteRecurringRule(rule) }
    }

    // ---- Actions ----

    fun createPitaka(name: String, startingBalance: Double, currency: String, colorHex: String?) {
        viewModelScope.launch { repository.createPitaka(name, startingBalance, currency, colorHex) }
    }

    fun updatePitakaMeta(pitakaId: Long, name: String, currency: String, colorHex: String?) {
        viewModelScope.launch { repository.updatePitakaMeta(pitakaId, name, currency, colorHex) }
    }

    fun deletePitaka(pitaka: Pitaka) {
        viewModelScope.launch { repository.deletePitakaCascade(pitaka) }
    }

    fun adjustPitakaBalanceManually(pitakaId: Long, newBalance: Double, note: String) {
        viewModelScope.launch { repository.adjustPitakaBalanceManually(pitakaId, newBalance, note) }
    }

    fun createGoal(name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?) {
        viewModelScope.launch { repository.createGoal(name, type, targetAmount, targetDate, colorHex) }
    }

    fun updateGoal(goalId: Long, name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?) {
        viewModelScope.launch { repository.updateGoal(goalId, name, type, targetAmount, targetDate, colorHex) }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch { repository.deleteGoal(goal) }
    }

    fun recordIncome(pitakaId: Long, name: String, amount: Double) {
        viewModelScope.launch { repository.recordIncome(pitakaId, name, amount) }
    }

    fun recordExpense(pitakaId: Long, name: String, amount: Double, category: String?) {
        viewModelScope.launch {
            repository.recordExpense(pitakaId, name, amount, category?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    fun recordTransfer(fromPitakaId: Long, toPitakaId: Long, name: String, amount: Double, secondaryAmount: Double? = null) {
        viewModelScope.launch { repository.recordTransfer(fromPitakaId, toPitakaId, name, amount, secondaryAmount) }
    }

    fun recordGoalContribution(sourcePitakaId: Long, goalId: Long, name: String, amount: Double) {
        viewModelScope.launch { repository.recordGoalContribution(sourcePitakaId, goalId, name, amount) }
    }

    fun deleteEntry(entry: LedgerEntry) {
        viewModelScope.launch { repository.deleteEntry(entry) }
    }

    fun updateEntry(entry: LedgerEntry, newName: String, newAmount: Double, newCategory: String?) {
        viewModelScope.launch { repository.updateEntry(entry, newName, newAmount, newCategory) }
    }
}

class PitakaViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PitakaViewModel(application) as T
    }
}
