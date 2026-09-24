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

    /** Last user-facing repository operation error. Cleared after a successful operation. */
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    fun clearOperationError() { _operationError.value = null }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _operationError.value = null }
                .onFailure { _operationError.value = it.message ?: "Unable to complete the operation." }
        }
    }

    private val repository = PitakaRepository(AppDatabase.getInstance(application))

    init {
        // Catch up on any recurring income/expenses due since the app was last opened.
        viewModelScope.launch { repository.applyDueRecurringRules() }
    }

    // ---- Core data ----

    val pitakas: Flow<List<Pitaka>> = repository.observePitakas()
    val rootPitakas: Flow<List<Pitaka>> = repository.observeRootPitakas()
    fun childrenOfPitaka(id: Long): Flow<List<Pitaka>> = repository.observeChildren(id)

    fun effectivePitakaBalances(pitakaId: Long, all: List<Pitaka>): Map<String, Double> {
        val byParent = all.groupBy { it.parentPitakaId }
        fun collect(id: Long): Map<String, Double> {
            val children = byParent[id].orEmpty()
            if (children.isEmpty()) return CurrencyBalances.parse(all.firstOrNull { it.id == id }?.currencyBalances)
            val result = mutableMapOf<String, Double>()
            children.forEach { child ->
                collect(child.id).forEach { (code, amount) ->
                    result[code] = (result[code] ?: 0.0) + amount
                }
            }
            return result
        }
        return collect(pitakaId)
    }

    val goals: Flow<List<GoalWithProgress>> = repository.observeGoals()
    val expenseFunnels: Flow<List<ExpenseFunnel>> = repository.observeExpenseFunnels()

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
        val base = settings?.baseCurrency ?: "PHP"
        list.filter { it.parentPitakaId == null }.sumOf { root ->
            effectivePitakaBalances(root.id, list).entries.sumOf { (code, amount) ->
                convert(amount, code, base, rates)
            }
        }
    }

    val totalSavingsProgress: Flow<Double> = combine(goals, exchangeRates, currencySettings) { list, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        list.filter { it.type == GoalType.SAVINGS }.sumOf { CurrencyBalances.parse(it.currencyBalances).entries.sumOf { (code, amount) -> convert(amount, code, base, rates) } }
    }
    val totalInvestmentProgress: Flow<Double> = combine(goals, exchangeRates, currencySettings) { list, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        list.filter { it.type == GoalType.INVESTMENT }.sumOf { CurrencyBalances.parse(it.currencyBalances).entries.sumOf { (code, amount) -> convert(amount, code, base, rates) } }
    }
    val totalNetWorth: Flow<Double> = combine(
        totalLiquid, totalSavingsProgress, totalInvestmentProgress
    ) { liquid, savings, investments -> liquid + savings + investments }
    fun netWorthForMonth(month: String): Flow<Double> = combine(totalNetWorth, allEntries, currencySettings, exchangeRates) { current, entries, settings, rates ->
        val base = settings?.baseCurrency ?: "PHP"
        val cutoff = try {
            YearMonth.parse(month).plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { Long.MAX_VALUE }
        val deltaAfter = entries.filter { it.date >= cutoff }.sumOf { entry ->
            val converted = convert(kotlin.math.abs(entry.amount), entry.currency, base, rates)
            when (entry.type) {
                LedgerType.INCOME -> converted
                LedgerType.EXPENSE -> -converted
                LedgerType.ADJUSTMENT -> if (entry.amount >= 0) converted else -converted
                else -> 0.0
            }
        }
        current - deltaAfter
    }

    private fun monthKey(date: Long): String = java.time.Instant.ofEpochMilli(date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString().substring(0, 7)

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

    val currentMonthExpenseTotal: Flow<Double> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.asSequence().filter { it.type == LedgerType.EXPENSE && monthKey(it.date) == currentMonthKey }
            .sumOf { convert(it.amount, it.currency, base, rates) }
    }

    suspend fun getExactBudgetForMonth(month: String): MonthlyBudget? = repository.getExactBudgetForMonth(month)

    fun setMonthlyExpenseLimit(month: String, limit: Double?) {
        viewModelScope.launch { repository.setMonthlyExpenseLimit(month, limit) }
    }

    // ---- Statistics ----

    val monthlyExpenses: Flow<List<MonthlyAmount>> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.filter { it.type == LedgerType.EXPENSE }.groupBy { monthKey(it.date) }.toSortedMap()
            .map { (month, rows) -> MonthlyAmount(month, rows.sumOf { convert(it.amount, it.currency, base, rates) }) }
    }
    val monthlyIncome: Flow<List<MonthlyAmount>> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.filter { it.type == LedgerType.INCOME }.groupBy { monthKey(it.date) }.toSortedMap()
            .map { (month, rows) -> MonthlyAmount(month, rows.sumOf { convert(it.amount, it.currency, base, rates) }) }
    }

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

    val expenseBreakdown: Flow<List<CategorySpend>> = expenseBreakdownConverted(null)
    val expenseCategories: Flow<List<String>> = repository.observeExpenseCategories()
    val availableMonths: Flow<List<String>> = repository.observeAvailableMonths()

    fun expenseBreakdownForMonth(month: String): Flow<List<CategorySpend>> = expenseBreakdownConverted(month)

    private fun expenseBreakdownConverted(month: String?): Flow<List<CategorySpend>> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.asSequence().filter { it.type == LedgerType.EXPENSE && (month == null || monthKey(it.date) == month) }
            .groupBy { it.category?.trim().takeUnless { x -> x.isNullOrEmpty() } ?: "Uncategorized Expense" }
            .map { (category, rows) -> CategorySpend(category, rows.sumOf { convert(it.amount, it.currency, base, rates) }) }
            .sortedByDescending { it.total }
    }

    fun entriesForPitaka(id: Long): Flow<List<LedgerEntry>> = repository.observeEntriesForPitaka(id)
    fun entriesForGoal(id: Long): Flow<List<LedgerEntry>> = repository.observeEntriesForGoal(id)
    val allExpenses: Flow<List<LedgerEntry>> = repository.observeAllExpenses()
    val allEntries: Flow<List<LedgerEntry>> = repository.observeAllEntries()

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
        launchOperation { repository.deleteRecurringRule(rule) }
    }

    // ---- Actions ----

    fun createPitaka(name: String, startingBalance: Double, currency: String, colorHex: String?, parentPitakaId: Long? = null, cardStyle: String = "solid") {
        viewModelScope.launch { repository.createPitaka(name, startingBalance, currency, colorHex, parentPitakaId, cardStyle) }
    }
    fun setPitakaParent(pitakaId: Long, parentPitakaId: Long?) {
        viewModelScope.launch { repository.setPitakaParent(pitakaId, parentPitakaId) }
    }

    fun updatePitakaMeta(pitakaId: Long, name: String, currency: String, colorHex: String?, cardStyle: String = "solid") {
        viewModelScope.launch { repository.updatePitakaMeta(pitakaId, name, currency, colorHex, cardStyle) }
    }

    fun deletePitaka(pitaka: Pitaka) {
        viewModelScope.launch { repository.deletePitakaCascade(pitaka) }
    }

    fun adjustPitakaBalanceManually(pitakaId: Long, newBalance: Double, note: String, currency: String? = null) {
        viewModelScope.launch { repository.adjustPitakaBalanceManually(pitakaId, newBalance, note, currency) }
    }

    fun createGoal(name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?, cardStyle: String = "solid") {
        launchOperation { repository.createGoal(name, type, targetAmount, targetDate, colorHex, cardStyle) }
    }

    fun updateGoal(goalId: Long, name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?, cardStyle: String = "solid") {
        launchOperation { repository.updateGoal(goalId, name, type, targetAmount, targetDate, colorHex, cardStyle) }
    }

    fun deleteGoal(goal: Goal) {
        launchOperation { repository.deleteGoal(goal) }
    }

    fun recordIncome(pitakaId: Long, name: String, amount: Double) {
        launchOperation { repository.recordIncome(pitakaId, name, amount) }
    }

    fun recordExpense(pitakaId: Long, name: String, amount: Double, category: String?, funnelId: Long? = null, currency: String? = null, funnelAmount: Double? = null, funnelCurrency: String? = null, date: Long = System.currentTimeMillis()) {
        launchOperation { repository.recordExpense(pitakaId, name, amount, category, funnelId, currency, funnelAmount, funnelCurrency, date) }
    }

    fun recordTransfer(fromPitakaId: Long, toPitakaId: Long, name: String, amount: Double, secondaryAmount: Double? = null) {
        launchOperation { repository.recordTransfer(fromPitakaId, toPitakaId, name, amount, secondaryAmount) }
    }

    fun recordGoalContribution(sourcePitakaId: Long, goalId: Long, name: String, amount: Double, currency: String? = null) {
        launchOperation { repository.recordGoalContribution(sourcePitakaId, goalId, name, amount, currency) }
    }

    fun createExpenseFunnel(name: String, limit: Double, validFrom: Long?, validUntil: Long?, colorHex: String?, cardStyle: String = "solid") {
        launchOperation { repository.createExpenseFunnel(name, limit, validFrom, validUntil, colorHex, cardStyle) }
    }

    fun deleteExpenseFunnel(funnel: ExpenseFunnel) {
        launchOperation { repository.deleteExpenseFunnel(funnel) }
    }

    fun updateExpenseFunnel(funnel: ExpenseFunnel) {
        launchOperation { repository.updateExpenseFunnel(funnel) }
    }

    fun deleteEntry(entry: LedgerEntry) {
        launchOperation { repository.deleteEntry(entry) }
    }

    fun updateEntry(entry: LedgerEntry, newName: String, newAmount: Double, newCategory: String?, newPitakaId: Long? = entry.pitakaId) {
        launchOperation { repository.updateEntry(entry, newName, newAmount, newCategory, newPitakaId) }
    }
    fun observeExpensesForCategory(category: String): Flow<List<LedgerEntry>> = repository.observeExpensesForCategory(category)
    fun observeExpensesForFunnel(funnelId: Long): Flow<List<LedgerEntry>> = repository.observeExpensesForFunnel(funnelId)
    fun observeExpensesForMonth(month: String): Flow<List<LedgerEntry>> = repository.observeExpensesForMonth(month)
}

class PitakaViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PitakaViewModel(application) as T
    }
}
