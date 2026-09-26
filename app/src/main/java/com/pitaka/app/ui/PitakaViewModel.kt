package com.pitaka.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pitaka.app.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.YearMonth
import java.time.ZoneId

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

    private fun launchOperation(block: suspend () -> Unit, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    _operationError.value = null
                    onSuccess?.invoke()
                }
                .onFailure { _operationError.value = it.message ?: "Unable to complete the operation." }
        }
    }

    fun recordTransfer(
        fromPitakaId: Long,
        toPitakaId: Long,
        name: String,
        amount: Double,
        secondaryAmount: Double? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        launchOperation(
            { repository.recordTransfer(fromPitakaId, toPitakaId, name, amount, secondaryAmount) },
            onSuccess
        )
    }

    private val repository = PitakaRepository(AppDatabase.getInstance(application))

    init {
        // Catch up on any recurring income/expenses due since the app was last opened.
        launchOperation({ repository.applyDueRecurringRules() })
        startMonthBoundaryWatcher()
    }

    // ---- Core data ----

    private fun <T> Flow<T>.recoverForUi(fallback: T): Flow<T> = catch { emit(fallback) }

    val pitakas: Flow<List<Pitaka>> = repository.observePitakas().recoverForUi(emptyList())
    val rootPitakas: Flow<List<Pitaka>> = repository.observeRootPitakas().recoverForUi(emptyList())
    fun childrenOfPitaka(id: Long): Flow<List<Pitaka>> = repository.observeChildren(id)

    fun effectivePitakaBalances(pitakaId: Long, all: List<Pitaka>): Map<String, Double> {
        val byParent = all.groupBy { it.parentPitakaId }
        val byId = all.associateBy { it.id }

        // Existing databases can contain a hierarchy created by an older build. Never let
        // malformed cyclic parent data recurse forever and crash the first screen.
        fun collect(id: Long, visiting: Set<Long>): Map<String, Double> {
            if (id in visiting) return emptyMap()
            val node = byId[id] ?: return emptyMap()
            val children = byParent[id].orEmpty()
            if (children.isEmpty()) {
                return CurrencyBalances.parse(node.currencyBalances)
            }

            val nextVisiting = visiting + id
            val result = mutableMapOf<String, Double>()
            children.forEach { child ->
                collect(child.id, nextVisiting).forEach { (code, amount) ->
                    result[code] = (result[code] ?: 0.0) + amount
                }
            }
            return result
        }

        return collect(pitakaId, emptySet())
    }

    val goals: Flow<List<GoalWithProgress>> = repository.observeGoals().recoverForUi(emptyList())
    val expenseFunnels: Flow<List<ExpenseFunnel>> = repository.observeExpenseFunnels().recoverForUi(emptyList())

    val financialEntries: Flow<List<LedgerEntry>> = repository.observeFinancialEntries().recoverForUi(emptyList())

    // ---- Currency ----

    val currencySettings: Flow<CurrencySettings?> = repository.observeCurrencySettings().recoverForUi(CurrencySettings())
    val exchangeRates: Flow<List<ExchangeRate>> = repository.observeExchangeRates().recoverForUi(emptyList())

    fun setBaseCurrency(code: String) {
        launchOperation({ repository.setBaseCurrency(code) })
    }

    fun setExchangeRate(code: String, rateToBase: Double) {
        launchOperation({ repository.setExchangeRate(code, rateToBase) })
    }

    fun deleteExchangeRate(code: String) {
        launchOperation({ repository.deleteExchangeRate(code) })
    }

    /** Liquid total (all Pitakas), converted to the base/display currency. */
    val allEntries: Flow<List<LedgerEntry>> = repository.observeAllEntries().recoverForUi(emptyList())

    val totalLiquid: Flow<Double> = combine(pitakas, exchangeRates, currencySettings) { list, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        list.filter { it.parentPitakaId == null }.sumOf { root ->
            effectivePitakaBalances(root.id, list).entries.mapNotNull { (code, amount) ->
                convert(amount, code, base, rates)
            }.sum()
        }
    }

    val totalSavingsProgress: Flow<Double> = combine(goals, exchangeRates, currencySettings) { list, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        list.filter { it.type == GoalType.SAVINGS }.sumOf { CurrencyBalances.parse(it.currencyBalances).entries.mapNotNull { (code, amount) -> convert(amount, code, base, rates) }.sum() }
    }
    val totalInvestmentProgress: Flow<Double> = combine(goals, exchangeRates, currencySettings) { list, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        list.filter { it.type == GoalType.INVESTMENT }.sumOf { CurrencyBalances.parse(it.currencyBalances).entries.mapNotNull { (code, amount) -> convert(amount, code, base, rates) }.sum() }
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
            val converted = convert(kotlin.math.abs(entry.amount), entry.currency, base, rates) ?: 0.0
            when (entry.type) {
                LedgerType.INCOME -> converted
                LedgerType.EXPENSE -> -converted
                LedgerType.ADJUSTMENT -> if (entry.amount >= 0) converted else -converted
                else -> 0.0
            }
        }
        current - deltaAfter
    }

    private fun monthKey(date: Long): String = runCatching {
        java.time.Instant.ofEpochMilli(date)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString()
            .substring(0, 7)
    }.getOrDefault("0000-00")

    val hasMissingConversionRates: Flow<Boolean> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.any { entry ->
            entry.type != LedgerType.TRANSFER &&
                entry.currency.uppercase() != base.uppercase() &&
                rates.none { it.code.equals(entry.currency, ignoreCase = true) }
        }
    }

    private fun convert(amount: Double, from: String, to: String, rates: List<ExchangeRate>): Double? {
        val source = from.trim().uppercase()
        val target = to.trim().uppercase()
        if (!amount.isFinite()) return null
        if (source == target) return amount
        // All current callers convert into the configured base currency. The base currency
        // is the reference unit, so its rate is exactly 1 and does not need a stored row.
        val fromRate = rates.find { it.code.equals(source, ignoreCase = true) }?.rateToBase ?: return null
        if (!fromRate.isFinite() || fromRate <= 0.0) return null
        return MoneyMath.multiply(amount, fromRate)
    }

    fun convertCurrency(amount: Double, from: String, to: String, rates: List<ExchangeRate>): Double =
        convert(amount, from, to, rates) ?: 0.0

    // ---- Monthly expense budgets ----

    private val _currentMonthKey = MutableStateFlow(YearMonth.now().toString())
    val currentMonthKeyFlow: StateFlow<String> = _currentMonthKey.asStateFlow()

    val currentMonthKey: String
        get() = _currentMonthKey.value

    private fun startMonthBoundaryWatcher() {
        viewModelScope.launch {
            while (true) {
                _currentMonthKey.value = YearMonth.now().toString()
                delay(60_000)
            }
        }
    }

    val currentMonthBudget: Flow<MonthlyBudget?> =
        currentMonthKeyFlow.flatMapLatest { month -> repository.observeEffectiveBudget(month) }
    val allBudgets: Flow<List<MonthlyBudget>> = repository.observeAllBudgets()

    val currentMonthExpenseTotal: Flow<Double> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.asSequence().filter { it.type == LedgerType.EXPENSE && monthKey(it.date) == YearMonth.now().toString() }
            .mapNotNull { convert(it.amount, it.currency, base, rates) }.sum()
    }

    suspend fun getExactBudgetForMonth(month: String): MonthlyBudget? = repository.getExactBudgetForMonth(month)

    fun setMonthlyExpenseLimit(month: String, limit: Double?) {
        launchOperation({ repository.setMonthlyExpenseLimit(month, limit) })
    }

    // ---- Statistics ----

    val monthlyExpenses: Flow<List<MonthlyAmount>> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.filter { it.type == LedgerType.EXPENSE }.groupBy { monthKey(it.date) }.toSortedMap()
            .map { (month, rows) -> MonthlyAmount(month, rows.mapNotNull { convert(it.amount, it.currency, base, rates) }.sum()) }
    }
    val monthlyIncome: Flow<List<MonthlyAmount>> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.filter { it.type == LedgerType.INCOME }.groupBy { monthKey(it.date) }.toSortedMap()
            .map { (month, rows) -> MonthlyAmount(month, rows.mapNotNull { convert(it.amount, it.currency, base, rates) }.sum()) }
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
    val expenseCategories: Flow<List<String>> = repository.observeExpenseCategories().recoverForUi(emptyList())
    val availableMonths: Flow<List<String>> = repository.observeAvailableMonths().recoverForUi(emptyList())

    fun expenseBreakdownForMonth(month: String): Flow<List<CategorySpend>> = expenseBreakdownConverted(month)

    private fun expenseBreakdownConverted(month: String?): Flow<List<CategorySpend>> = combine(allEntries, exchangeRates, currencySettings) { entries, rates, settings ->
        val base = settings?.baseCurrency ?: "PHP"
        entries.asSequence().filter { it.type == LedgerType.EXPENSE && (month == null || monthKey(it.date) == month) }
            .groupBy { it.category?.trim().takeUnless { x -> x.isNullOrEmpty() } ?: "Uncategorized Expense" }
            .map { (category, rows) -> CategorySpend(category, rows.mapNotNull { convert(it.amount, it.currency, base, rates) }.sum()) }
            .sortedByDescending { it.total }
    }

    fun entriesForPitaka(id: Long): Flow<List<LedgerEntry>> = repository.observeEntriesForPitaka(id)
    fun entriesForGoal(id: Long): Flow<List<LedgerEntry>> = repository.observeEntriesForGoal(id)
    val allExpenses: Flow<List<LedgerEntry>> = repository.observeAllExpenses().recoverForUi(emptyList())

    suspend fun getPitaka(id: Long): Pitaka? = repository.getPitaka(id)
    suspend fun getGoal(id: Long): Goal? = repository.getGoal(id)
    suspend fun getAllEntriesOnce(): List<LedgerEntry> = repository.getAllEntriesOnce()

    // ---- Recurring rules ----

    val recurringRules: Flow<List<RecurringRule>> = repository.observeRecurringRules()

    fun createRecurringRule(type: LedgerType, name: String, amount: Double, category: String?, pitakaId: Long, dayOfMonth: Int, currency: String? = null) {
        launchOperation({ repository.createRecurringRule(type, name, amount, category, pitakaId, dayOfMonth, currency) })
    }

    fun setRecurringRuleActive(rule: RecurringRule, active: Boolean) {
        launchOperation({ repository.setRecurringRuleActive(rule, active) })
    }

    fun deleteRecurringRule(rule: RecurringRule) {
        launchOperation({ repository.deleteRecurringRule(rule) })
    }

    // ---- Actions ----

    fun createPitaka(name: String, startingBalance: Double, currency: String, colorHex: String?, parentPitakaId: Long? = null, cardStyle: String = "solid", onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.createPitaka(name, startingBalance, currency, colorHex, parentPitakaId, cardStyle) }, onSuccess)
    }
    fun setPitakaParent(pitakaId: Long, parentPitakaId: Long?) {
        launchOperation({ repository.setPitakaParent(pitakaId, parentPitakaId) })
    }

    fun updatePitakaMeta(pitakaId: Long, name: String, currency: String, colorHex: String?, cardStyle: String = "solid", onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.updatePitakaMeta(pitakaId, name, currency, colorHex, cardStyle) }, onSuccess)
    }

    fun archivePitaka(pitakaId: Long, onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.archivePitaka(pitakaId) }, onSuccess)
    }

    fun deletePitaka(pitaka: Pitaka, onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.deletePitakaCascade(pitaka) }, onSuccess)
    }

    fun adjustPitakaBalanceManually(pitakaId: Long, newBalance: Double, note: String, currency: String? = null) {
        launchOperation({ repository.adjustPitakaBalanceManually(pitakaId, newBalance, note, currency) })
    }

    fun createGoal(name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?, cardStyle: String = "solid", currency: String = "PHP", onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.createGoal(name, type, targetAmount, targetDate, colorHex, cardStyle, currency) }, onSuccess)
    }

    fun updateGoal(goalId: Long, name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?, cardStyle: String = "solid", currency: String? = null, onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.updateGoal(goalId, name, type, targetAmount, targetDate, colorHex, cardStyle, currency) }, onSuccess)
    }

    fun archiveGoal(goalId: Long, onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.archiveGoal(goalId) }, onSuccess)
    }

    fun deleteGoal(goal: Goal, onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.deleteGoal(goal) }, onSuccess)
    }

    fun recordIncome(pitakaId: Long, name: String, amount: Double) {
        launchOperation({ repository.recordIncome(pitakaId, name, amount) })
    }

    fun recordExpense(pitakaId: Long, name: String, amount: Double, category: String?, funnelId: Long? = null, currency: String? = null, funnelAmount: Double? = null, funnelCurrency: String? = null, date: Long = System.currentTimeMillis(), onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.recordExpense(pitakaId, name, amount, category, funnelId, currency, funnelAmount, funnelCurrency, date) }, onSuccess)
    }


    fun recordGoalContribution(
        sourcePitakaId: Long,
        goalId: Long,
        name: String,
        amount: Double,
        currency: String? = null,
        goalAmount: Double? = null,
        goalCurrency: String? = null,
        date: Long = System.currentTimeMillis(),
        onSuccess: (() -> Unit)? = null
    ) {
        launchOperation(
            { repository.recordGoalContribution(sourcePitakaId, goalId, name, amount, currency, goalAmount, goalCurrency, date) },
            onSuccess
        )
    }

    fun createExpenseFunnel(name: String, limit: Double, validFrom: Long?, validUntil: Long?, colorHex: String?, cardStyle: String = "solid", currency: String = "PHP", onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.createExpenseFunnel(name, limit, validFrom, validUntil, colorHex, cardStyle, currency) }, onSuccess)
    }

    fun deleteExpenseFunnel(funnel: ExpenseFunnel) {
        launchOperation({ repository.deleteExpenseFunnel(funnel) })
    }

    fun updateExpenseFunnel(funnel: ExpenseFunnel, onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.updateExpenseFunnel(funnel) }, onSuccess)
    }

    fun deleteEntry(entry: LedgerEntry, onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.deleteEntry(entry) }, onSuccess)
    }

    fun updateEntry(entry: LedgerEntry, newName: String, newAmount: Double, newCategory: String?, newPitakaId: Long? = entry.pitakaId, onSuccess: (() -> Unit)? = null) {
        launchOperation({ repository.updateEntry(entry, newName, newAmount, newCategory, newPitakaId) }, onSuccess)
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
