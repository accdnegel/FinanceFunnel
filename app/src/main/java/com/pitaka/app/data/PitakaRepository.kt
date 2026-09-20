package com.pitaka.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class PitakaRepository(private val db: AppDatabase) {

    private val pitakaDao = db.pitakaDao()
    private val goalDao = db.goalDao()
    private val ledgerDao = db.ledgerDao()
    private val budgetDao = db.monthlyBudgetDao()
    private val currencyDao = db.currencyDao()
    private val recurringDao = db.recurringRuleDao()
    private val funnelDao = db.expenseFunnelDao()

    // ---- Pitakas ----

    fun observePitakas(): Flow<List<Pitaka>> = pitakaDao.observePitakas()
    fun observeRootPitakas(): Flow<List<Pitaka>> = pitakaDao.observeRootPitakas()
    fun observeChildren(parentId: Long): Flow<List<Pitaka>> = pitakaDao.observeChildren(parentId)

    suspend fun getPitaka(id: Long): Pitaka? = pitakaDao.getPitaka(id)

    suspend fun createPitaka(name: String, startingBalance: Double, currency: String, colorHex: String?, parentPitakaId: Long? = null, cardStyle: String = "solid"): Long {
        return pitakaDao.insertPitaka(
            Pitaka(name = name, currentAmount = startingBalance, currency = currency.uppercase(), currencyBalances = CurrencyBalances.encode(mapOf(currency.uppercase() to startingBalance)), colorHex = colorHex, parentPitakaId = parentPitakaId, cardStyle = cardStyle)
        )
    }

    suspend fun setPitakaParent(pitakaId: Long, parentPitakaId: Long?) {
        val p = pitakaDao.getPitaka(pitakaId) ?: return
        require(parentPitakaId == null || parentPitakaId != pitakaId) { "A Pitaka cannot be its own parent." }
        pitakaDao.updatePitaka(p.copy(parentPitakaId = parentPitakaId))
    }

    suspend fun addSubPitaka(parentId: Long, name: String, startingBalance: Double, currency: String, colorHex: String?, cardStyle: String = "solid"): Long =
        createPitaka(name, startingBalance, currency, colorHex, parentId, cardStyle)

    /** Metadata-only edit (name/currency/color) — never touches the balance. */
    suspend fun updatePitakaMeta(pitakaId: Long, name: String, currency: String, colorHex: String?, cardStyle: String = "solid") {
        val existing = pitakaDao.getPitaka(pitakaId) ?: return
        pitakaDao.updatePitaka(existing.copy(name = name, currency = currency.uppercase(), colorHex = colorHex, cardStyle = cardStyle, currencyBalances = if (existing.currencyBalances.isBlank()) CurrencyBalances.encode(mapOf(currency.uppercase() to existing.currentAmount)) else existing.currencyBalances))
    }

    /** Deletes a Pitaka and every ledger row that touches it (income/expense/transfers/contributions). */
    suspend fun deletePitakaCascade(pitaka: Pitaka) {
        db.withTransaction {
            ledgerDao.deleteEntriesForPitaka(pitaka.id)
            pitakaDao.deletePitaka(pitaka)
        }
    }

    /**
     * Manually overrides a Pitaka's balance, bypassing the normal logs. Records an ADJUSTMENT
     * ledger entry for the delta so there's still an audit trail. The UI is responsible for
     * warning the user before calling this.
     */
    suspend fun adjustPitakaBalanceManually(pitakaId: Long, newBalance: Double, note: String) {
        db.withTransaction {
            val pitaka = pitakaDao.getPitaka(pitakaId) ?: return@withTransaction
            val delta = newBalance - pitaka.currentAmount
            if (delta == 0.0) return@withTransaction
            val entry = LedgerEntry(
                type = LedgerType.ADJUSTMENT,
                amount = delta,
                name = note.ifBlank { "Manual adjustment" },
                pitakaId = pitakaId
            )
            ledgerDao.insertEntry(entry)
            applyEffect(entry)
        }
    }

    // ---- Goals ----

    fun observeGoals(): Flow<List<GoalWithProgress>> = goalDao.observeGoalsWithProgress()

    fun observeTotalProgressForType(type: GoalType): Flow<Double> = goalDao.observeTotalProgressForType(type)

    suspend fun getGoal(id: Long): Goal? = goalDao.getGoal(id)

    suspend fun createGoal(name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?): Long {
        return goalDao.insertGoal(
            Goal(name = name, type = type, targetAmount = targetAmount, targetDate = targetDate, colorHex = colorHex, currencyBalances = "${"PHP"}=0")
        )
    }

    suspend fun updateGoal(goalId: Long, name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?) {
        val existing = goalDao.getGoal(goalId) ?: return
        goalDao.updateGoal(
            existing.copy(name = name, type = type, targetAmount = targetAmount, targetDate = targetDate, colorHex = colorHex)
        )
    }

    /**
     * Deletes a Goal but deliberately leaves its GOAL_CONTRIBUTION ledger rows alone — that
     * money genuinely left its source Pitaka and should stay reflected in that Pitaka's
     * history; only the goal-progress tracking for it goes away.
     */
    suspend fun deleteGoal(goal: Goal) = goalDao.deleteGoal(goal)

    // ---- Expense funnels ----
    fun observeExpenseFunnels(): Flow<List<ExpenseFunnel>> = funnelDao.observeAll()
    suspend fun getSystemUnclassifiedFunnel(): ExpenseFunnel {
        return funnelDao.getByName("Unclassified Expense") ?: funnelDao.insertAndReturn(ExpenseFunnel(name = "Unclassified Expense", limit = 0.0, currency = "PHP", currencyBalances = "PHP=0", isSystem = true)).let { funnelDao.get(it)!! }
    }
    fun observeFunnelSpent(funnelId: Long): Flow<Double> = funnelDao.observeSpent(funnelId)
    suspend fun createExpenseFunnel(name: String, limit: Double, validFrom: Long?, validUntil: Long?, colorHex: String?, cardStyle: String = "solid"): Long =
        funnelDao.insert(ExpenseFunnel(name = name, limit = limit, validFrom = validFrom, validUntil = validUntil, colorHex = colorHex, cardStyle = cardStyle, currencyBalances = "PHP=0"))
    suspend fun updateExpenseFunnel(funnel: ExpenseFunnel) = funnelDao.update(funnel)
    suspend fun deleteExpenseFunnel(funnel: ExpenseFunnel) = funnelDao.delete(funnel)

    // ---- Ledger reads ----

    fun observeEntriesForPitaka(pitakaId: Long): Flow<List<LedgerEntry>> = ledgerDao.observeEntriesForPitaka(pitakaId)

    fun observeEntriesForGoal(goalId: Long): Flow<List<LedgerEntry>> = ledgerDao.observeEntriesForGoal(goalId)

    fun observeAllExpenses(): Flow<List<LedgerEntry>> = ledgerDao.observeAllExpenses()

    fun observeMonthlyExpenses(): Flow<List<MonthlyAmount>> = ledgerDao.observeMonthlyExpenses()

    fun observeMonthlyIncome(): Flow<List<MonthlyAmount>> = ledgerDao.observeMonthlyIncome()

    fun observeExpenseBreakdown(): Flow<List<CategorySpend>> = ledgerDao.observeExpenseBreakdown()

    fun observeExpenseBreakdownForMonth(month: String): Flow<List<CategorySpend>> = ledgerDao.observeExpenseBreakdownForMonth(month)

    fun observeExpenseCategories(): Flow<List<String>> = ledgerDao.observeExpenseCategories()

    fun observeAvailableMonths(): Flow<List<String>> = ledgerDao.observeAvailableMonths()

    fun observeExpenseTotalForMonth(month: String): Flow<Double> = ledgerDao.observeExpenseTotalForMonth(month)
    fun observeExpensesForCategory(category: String): Flow<List<LedgerEntry>> = ledgerDao.observeExpensesForCategory(category)
    fun observeExpensesForFunnel(funnelId: Long): Flow<List<LedgerEntry>> = ledgerDao.observeExpensesForFunnel(funnelId)
    fun observeExpensesForMonth(month: String): Flow<List<LedgerEntry>> = ledgerDao.observeExpensesForMonth(month)

    suspend fun getAllEntriesOnce(): List<LedgerEntry> = ledgerDao.getAllEntriesOnce()

    suspend fun deleteEntry(entry: LedgerEntry) {
        db.withTransaction {
            reverseEffect(entry)
            ledgerDao.deleteEntry(entry)
        }
    }

    /** Edits name/amount/category in place, reversing the old balance effect and applying the new one. */
    suspend fun updateEntry(oldEntry: LedgerEntry, newName: String, newAmount: Double, newCategory: String?, newPitakaId: Long? = oldEntry.pitakaId) {
        db.withTransaction {
            reverseEffect(oldEntry)
            val updated = oldEntry.copy(name = newName, amount = newAmount, category = newCategory?.trim()?.takeIf { it.isNotEmpty() } ?: if (oldEntry.type == LedgerType.EXPENSE) "Uncategorized Expense" else null, pitakaId = newPitakaId)
            ledgerDao.updateEntry(updated)
            applyEffect(updated)
        }
    }

    // ---- Monthly expense budgets ----

    fun observeEffectiveBudget(month: String): Flow<MonthlyBudget?> = budgetDao.observeEffectiveBudget(month)

    fun observeAllBudgets(): Flow<List<MonthlyBudget>> = budgetDao.observeAllBudgets()

    suspend fun getExactBudgetForMonth(month: String): MonthlyBudget? = budgetDao.getExactForMonth(month)

    suspend fun setMonthlyExpenseLimit(month: String, limit: Double?) {
        if (limit == null) {
            budgetDao.clearForMonth(month)
        } else {
            budgetDao.upsert(MonthlyBudget(month = month, limit = limit))
        }
    }

    // ---- Currency ----

    fun observeCurrencySettings(): Flow<CurrencySettings?> = currencyDao.observeSettings()

    suspend fun setBaseCurrency(code: String) {
        currencyDao.upsertSettings(CurrencySettings(baseCurrency = code))
    }

    fun observeExchangeRates(): Flow<List<ExchangeRate>> = currencyDao.observeRates()

    suspend fun setExchangeRate(code: String, rateToBase: Double) {
        currencyDao.upsertRate(ExchangeRate(code = code, rateToBase = rateToBase))
    }

    suspend fun deleteExchangeRate(code: String) = currencyDao.deleteRate(code)

    // ---- Recurring rules ----

    fun observeRecurringRules(): Flow<List<RecurringRule>> = recurringDao.observeAll()

    suspend fun createRecurringRule(
        type: LedgerType,
        name: String,
        amount: Double,
        category: String?,
        pitakaId: Long,
        dayOfMonth: Int
    ) {
        recurringDao.insert(
            RecurringRule(
                type = type, name = name, amount = amount, category = category,
                pitakaId = pitakaId, dayOfMonth = dayOfMonth.coerceIn(1, 31)
            )
        )
    }

    suspend fun setRecurringRuleActive(rule: RecurringRule, active: Boolean) {
        recurringDao.update(rule.copy(active = active))
    }

    suspend fun deleteRecurringRule(rule: RecurringRule) = recurringDao.delete(rule)

    /**
     * Called on app start. Posts any active recurring rule whose day-of-month has arrived
     * and hasn't already been applied this month. No background scheduling — this is a
     * "catch up next time you open the app" model, which keeps the app fully offline and
     * dependency-light.
     */
    suspend fun applyDueRecurringRules(today: LocalDate = LocalDate.now()) {
        val currentMonth = YearMonth.from(today).toString()
        val rules = recurringDao.getActiveRulesOnce()
        for (rule in rules) {
            if (rule.lastAppliedMonth == currentMonth) continue
            val effectiveDay = rule.dayOfMonth.coerceAtMost(today.lengthOfMonth())
            if (today.dayOfMonth < effectiveDay) continue

            db.withTransaction {
                when (rule.type) {
                    LedgerType.INCOME -> recordIncomeInternal(rule.pitakaId, "${rule.name} (recurring)", rule.amount)
                    LedgerType.EXPENSE -> recordExpenseInternal(rule.pitakaId, "${rule.name} (recurring)", rule.amount, rule.category, null, null)
                    else -> {} // recurring rules only support INCOME/EXPENSE
                }
                recurringDao.update(rule.copy(lastAppliedMonth = currentMonth))
            }
        }
    }

    // ---- Money-movement operations (all atomic) ----

    suspend fun recordIncome(pitakaId: Long, name: String, amount: Double, date: Long = System.currentTimeMillis()) {
        require(amount > 0) { "Income amount must be positive" }
        db.withTransaction { recordIncomeInternal(pitakaId, name, amount, date) }
    }

    private suspend fun recordIncomeInternal(pitakaId: Long, name: String, amount: Double, date: Long = System.currentTimeMillis()) {
        val pitakaCurrency = pitakaDao.getPitaka(pitakaId)?.currency ?: "PHP"
        val entry = LedgerEntry(type = LedgerType.INCOME, amount = amount, currency = pitakaCurrency, name = name, pitakaId = pitakaId, date = date)
        ledgerDao.insertEntry(entry)
        applyEffect(entry)
    }

    suspend fun recordExpense(pitakaId: Long, name: String, amount: Double, category: String?, funnelId: Long? = null, currency: String? = null, date: Long = System.currentTimeMillis()) {
        require(amount > 0) { "Expense amount must be positive" }
        db.withTransaction {
            val resolvedFunnel = funnelId ?: getSystemUnclassifiedFunnel().id
            recordExpenseInternal(pitakaId, name, amount, category?.trim()?.takeIf { it.isNotEmpty() } ?: "Uncategorized Expense", resolvedFunnel, currency ?: "PHP", date)
        }
    }

    private suspend fun recordExpenseInternal(
        pitakaId: Long, name: String, amount: Double, category: String?, funnelId: Long? = null, currency: String? = null, date: Long = System.currentTimeMillis()
    ) {
        val entry = LedgerEntry(
            type = LedgerType.EXPENSE, amount = amount, currency = currency ?: (pitakaDao.getPitaka(pitakaId)?.currency ?: "PHP"), name = name, category = category, pitakaId = pitakaId, funnelId = funnelId, date = date
        )
        ledgerDao.insertEntry(entry)
        applyEffect(entry)
    }

    /**
     * `amount` is removed from fromPitaka in its own currency. `secondaryAmount`, if provided
     * (for cross-currency transfers), is what's added to toPitaka in ITS currency; otherwise
     * the same `amount` is used for both sides.
     */
    suspend fun recordTransfer(
        fromPitakaId: Long,
        toPitakaId: Long,
        name: String,
        amount: Double,
        secondaryAmount: Double? = null,
        date: Long = System.currentTimeMillis()
    ) {
        db.withTransaction {
            val sourceCurrency = pitakaDao.getPitaka(fromPitakaId)?.currency ?: "PHP"
            val entry = LedgerEntry(
                type = LedgerType.TRANSFER,
                amount = amount,
                currency = sourceCurrency,
                name = name,
                fromPitakaId = fromPitakaId,
                toPitakaId = toPitakaId,
                secondaryAmount = secondaryAmount,
                date = date
            )
            ledgerDao.insertEntry(entry)
            applyEffect(entry)
        }
    }

    suspend fun recordGoalContribution(
        sourcePitakaId: Long,
        goalId: Long,
        name: String,
        amount: Double,
        currency: String? = null,
        date: Long = System.currentTimeMillis()
    ) {
        require(amount > 0) { "Contribution amount must be positive" }
        db.withTransaction {
            val entry = LedgerEntry(
                type = LedgerType.GOAL_CONTRIBUTION, amount = amount, currency = currency ?: (pitakaDao.getPitaka(sourcePitakaId)?.currency ?: "PHP"), name = name, pitakaId = sourcePitakaId, goalId = goalId, date = date
            )
            ledgerDao.insertEntry(entry)
            applyEffect(entry)
        }
    }

    // ---- Balance effect helpers ----
    // applyEffect() is linear in `amount`, so reverseEffect() can just negate amount(s) and
    // re-apply the same formula — this correctly undoes any entry type, including edits.

    private suspend fun applyEffect(entry: LedgerEntry) {
        when (entry.type) {
            LedgerType.INCOME -> entry.pitakaId?.let { adjustBalance(it, entry.amount, entry.currency) }
            LedgerType.EXPENSE -> {
                entry.pitakaId?.let { adjustBalance(it, -entry.amount, entry.currency) }
                entry.funnelId?.let { id -> funnelDao.get(id)?.let { funnelDao.update(it.copy(currencyBalances = CurrencyBalances.add(it.currencyBalances, entry.currency, entry.amount))) } }
            }
            LedgerType.GOAL_CONTRIBUTION -> {
                entry.pitakaId?.let { adjustBalance(it, -entry.amount, entry.currency) }
                entry.goalId?.let { id -> goalDao.getGoal(id)?.let { goalDao.updateGoal(it.copy(currencyBalances = CurrencyBalances.add(it.currencyBalances, entry.currency, entry.amount))) } }
            }
            LedgerType.ADJUSTMENT -> entry.pitakaId?.let { adjustBalance(it, entry.amount, entry.currency) }
            LedgerType.TRANSFER -> {
                entry.fromPitakaId?.let { adjustBalance(it, -entry.amount, entry.currency) }
                entry.toPitakaId?.let { id ->
                    val destinationCurrency = pitakaDao.getPitaka(id)?.currency ?: entry.currency
                    adjustBalance(id, entry.secondaryAmount ?: entry.amount, destinationCurrency)
                }
            }
        }
    }

    private suspend fun reverseEffect(entry: LedgerEntry) {
        applyEffect(
            entry.copy(
                amount = -entry.amount,
                secondaryAmount = entry.secondaryAmount?.let { -it }
            )
        )
    }

    private suspend fun adjustBalance(pitakaId: Long, delta: Double, currency: String = "PHP") {
        val pitaka = pitakaDao.getPitaka(pitakaId) ?: return
        val balances = CurrencyBalances.add(pitaka.currencyBalances, currency, delta)
        val primaryDelta = if (pitaka.currency.equals(currency, ignoreCase = true)) delta else 0.0
        pitakaDao.updatePitaka(
            pitaka.copy(
                currentAmount = pitaka.currentAmount + primaryDelta,
                currencyBalances = balances,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }
}
