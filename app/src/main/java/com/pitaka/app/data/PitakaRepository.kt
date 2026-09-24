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
        require(startingBalance >= 0) { "Starting balance cannot be negative." }
        val code = currency.trim().uppercase().ifBlank { "PHP" }

        return db.withTransaction {
            val parent = parentPitakaId?.let { pitakaDao.getPitaka(it) }
            require(parentPitakaId == null || parent != null) { "Parent Pitaka not found." }

            // A lone Pitaka becoming a parent must not lose its existing money. Its first
            // child inherits every existing currency balance, then receives the child's
            // starting amount. The parent becomes a pure container.
            if (parent != null && pitakaDao.countChildren(parent.id) == 0) {
                val inherited = CurrencyBalances.parse(parent.currencyBalances)
                if (inherited.isEmpty() && parent.currentAmount != 0.0) {
                    inherited[parent.currency.uppercase()] = parent.currentAmount
                }
                inherited[code] = (inherited[code] ?: 0.0) + startingBalance

                val childId = pitakaDao.insertPitaka(
                    Pitaka(
                        name = name,
                        currentAmount = inherited[code] ?: 0.0,
                        currency = code,
                        currencyBalances = CurrencyBalances.encode(inherited),
                        colorHex = colorHex,
                        parentPitakaId = parent.id,
                        cardStyle = cardStyle
                    )
                )
                pitakaDao.updatePitaka(
                    parent.copy(
                        currentAmount = 0.0,
                        currencyBalances = CurrencyBalances.encode(emptyMap()),
                        lastUpdated = System.currentTimeMillis()
                    )
                )
                childId
            } else {
                pitakaDao.insertPitaka(
                    Pitaka(
                        name = name,
                        currentAmount = startingBalance,
                        currency = code,
                        currencyBalances = CurrencyBalances.encode(mapOf(code to startingBalance)),
                        colorHex = colorHex,
                        parentPitakaId = parentPitakaId,
                        cardStyle = cardStyle
                    )
                )
            }
        }
    }

    suspend fun setPitakaParent(pitakaId: Long, parentPitakaId: Long?) {
        val p = pitakaDao.getPitaka(pitakaId) ?: return
        require(parentPitakaId == null || parentPitakaId != pitakaId) { "A Pitaka cannot be its own parent." }

        if (parentPitakaId != null) {
            require(pitakaDao.getPitaka(parentPitakaId) != null) { "Parent Pitaka not found." }
            // Walk upward from the proposed parent; if we encounter the Pitaka being moved,
            // the change would create a cycle.
            var cursor: Long? = parentPitakaId
            while (cursor != null) {
                if (cursor == pitakaId) require(false) { "This parent selection would create a hierarchy cycle." }
                cursor = pitakaDao.getPitaka(cursor)?.parentPitakaId
            }
        }
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
            require(ledgerDao.countEntriesForPitaka(pitaka.id) == 0) { "This Pitaka has transaction history. Archive it instead of deleting it." }
            require(pitakaDao.countChildren(pitaka.id) == 0) { "Remove or reassign child Pitakas before deleting this parent." }
            pitakaDao.deletePitaka(pitaka)
        }
    }

    /**
     * Manually overrides a Pitaka's balance, bypassing the normal logs. Records an ADJUSTMENT
     * ledger entry for the delta so there's still an audit trail. The UI is responsible for
     * warning the user before calling this.
     */
    suspend fun adjustPitakaBalanceManually(pitakaId: Long, newBalance: Double, note: String, currency: String? = null) {
        db.withTransaction {
            require(newBalance >= 0) { "Adjusted balance cannot be negative." }
            val pitaka = pitakaDao.getPitaka(pitakaId) ?: return@withTransaction
            val code = currency?.trim()?.uppercase()?.ifBlank { null } ?: pitaka.currency.uppercase()
            val current = CurrencyBalances.parse(pitaka.currencyBalances)[code] ?: 0.0
            val delta = newBalance - current
            if (delta == 0.0) return@withTransaction
            val entry = LedgerEntry(
                type = LedgerType.ADJUSTMENT,
                amount = delta,
                currency = code,
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

    suspend fun createGoal(name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?, cardStyle: String = "solid"): Long {
        return goalDao.insertGoal(
            Goal(name = name.trim().ifBlank { error("Goal name cannot be blank.") }, type = type, targetAmount = targetAmount.also { require(it > 0 && it.isFinite()) { "Goal target must be a positive finite number." } }, targetDate = targetDate, colorHex = colorHex, cardStyle = cardStyle, currencyBalances = "PHP=0")
        )
    }

    suspend fun updateGoal(goalId: Long, name: String, type: GoalType, targetAmount: Double, targetDate: Long, colorHex: String?, cardStyle: String = "solid") {
        val existing = goalDao.getGoal(goalId) ?: return
        goalDao.updateGoal(
            existing.copy(name = name.trim().ifBlank { error("Goal name cannot be blank.") }, type = type, targetAmount = targetAmount.also { require(it > 0 && it.isFinite()) { "Goal target must be a positive finite number." } }, targetDate = targetDate, colorHex = colorHex, cardStyle = cardStyle)
        )
    }

    /**
     * Deletes a Goal but deliberately leaves its GOAL_CONTRIBUTION ledger rows alone — that
     * money genuinely left its source Pitaka and should stay reflected in that Pitaka's
     * history; only the goal-progress tracking for it goes away.
     */
    suspend fun deleteGoal(goal: Goal) {
        require(goalDao.countContributions(goal.id) == 0) { "This Goal has contribution history. Archive it instead of deleting it." }
        goalDao.deleteGoal(goal)
    }

    // ---- Expense funnels ----
    fun observeExpenseFunnels(): Flow<List<ExpenseFunnel>> = funnelDao.observeAll()
    suspend fun getSystemUnclassifiedFunnel(): ExpenseFunnel {
        return funnelDao.getByName("Unclassified Expense") ?: funnelDao.insertAndReturn(ExpenseFunnel(name = "Unclassified Expense", limit = 0.0, currency = "PHP", currencyBalances = "PHP=0", isSystem = true)).let { funnelDao.get(it)!! }
    }
    fun observeFunnelSpent(funnelId: Long): Flow<Double> = funnelDao.observeSpent(funnelId)
    suspend fun createExpenseFunnel(name: String, limit: Double, validFrom: Long?, validUntil: Long?, colorHex: String?, cardStyle: String = "solid"): Long =
        funnelDao.insert(ExpenseFunnel(name = name.trim().ifBlank { error("Funnel name cannot be blank.") }, limit = limit.also { require(it >= 0 && it.isFinite()) { "Funnel limit must be a non-negative finite number." } }, validFrom = validFrom, validUntil = validUntil, colorHex = colorHex, cardStyle = cardStyle, currencyBalances = "PHP=0"))
    suspend fun updateExpenseFunnel(funnel: ExpenseFunnel) = funnelDao.update(funnel)
    suspend fun deleteExpenseFunnel(funnel: ExpenseFunnel) {
        require(!funnel.isSystem) { "System expense funnels cannot be deleted." }
        require(funnelDao.countExpenses(funnel.id) == 0) { "This funnel has expense history. Archive it instead of deleting it." }
        funnelDao.delete(funnel)
    }

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
    fun observeAllEntries(): Flow<List<LedgerEntry>> = ledgerDao.observeAllEntries()

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
            val normalizedCategory = if (oldEntry.type == LedgerType.EXPENSE) canonicalExpenseCategory(newCategory) else null
            require(newAmount > 0 || oldEntry.type == LedgerType.ADJUSTMENT) { "Transaction amount must be positive." }
            val ratio = if (oldEntry.amount != 0.0) newAmount / oldEntry.amount else 1.0
            val updated = oldEntry.copy(name = newName, amount = newAmount, category = normalizedCategory, pitakaId = newPitakaId,
                funnelAmount = oldEntry.funnelAmount?.times(ratio), goalAmount = oldEntry.goalAmount?.times(ratio))
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
        val normalized = code.trim().uppercase()
        require(normalized.isNotBlank()) { "Currency code cannot be blank." }
        require(rateToBase > 0 && rateToBase.isFinite()) { "Exchange rate must be a positive finite number." }
        currencyDao.upsertRate(ExchangeRate(code = normalized, rateToBase = rateToBase))
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
        require(type == LedgerType.INCOME || type == LedgerType.EXPENSE) { "Only income and expense can recur." }
        require(name.trim().isNotBlank()) { "Recurring transaction name cannot be blank." }
        require(amount > 0 && amount.isFinite()) { "Recurring amount must be a positive finite number." }
        require(dayOfMonth in 1..31) { "Recurring day must be between 1 and 31." }
        val pitaka = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
        recurringDao.insert(
            RecurringRule(
                type = type, name = name.trim(), amount = amount, currency = pitaka.currency.uppercase(), category = category,
                pitakaId = pitakaId, dayOfMonth = dayOfMonth
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
                    LedgerType.INCOME -> recordIncomeInternal(rule.pitakaId, "${rule.name} (recurring)", rule.amount, currency = rule.currency.uppercase())
                    LedgerType.EXPENSE -> recordExpenseInternal(rule.pitakaId, "${rule.name} (recurring)", rule.amount, rule.category, null, rule.currency)
                    else -> {} // recurring rules only support INCOME/EXPENSE
                }
                recurringDao.update(rule.copy(lastAppliedMonth = currentMonth))
            }
        }
    }

    // ---- Money-movement operations (all atomic) ----

    suspend fun recordIncome(pitakaId: Long, name: String, amount: Double, date: Long = System.currentTimeMillis()) {
        require(amount > 0 && amount.isFinite()) { "Income amount must be a positive finite number" }
        db.withTransaction {
            val pitaka = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            val currency = pitaka.currency.uppercase()
            recordIncomeInternal(pitakaId, name, amount, date, currency)
        }
    }

    private suspend fun recordIncomeInternal(pitakaId: Long, name: String, amount: Double, date: Long = System.currentTimeMillis(), currency: String? = null) {
        val pitakaCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: pitakaDao.getPitaka(pitakaId)?.currency ?: "PHP"
        val entry = LedgerEntry(type = LedgerType.INCOME, amount = amount, currency = pitakaCurrency, name = name, pitakaId = pitakaId, date = date)
        ledgerDao.insertEntry(entry)
        applyEffect(entry)
    }

    suspend fun recordExpense(pitakaId: Long, name: String, amount: Double, category: String?, funnelId: Long? = null, currency: String? = null, funnelAmount: Double? = null, funnelCurrency: String? = null, date: Long = System.currentTimeMillis()) {
        require(amount > 0 && amount.isFinite()) { "Expense amount must be a positive finite number" }
        db.withTransaction {
            val source = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            val txCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
            require((CurrencyBalances.parse(source.currencyBalances)[txCurrency] ?: 0.0) >= amount) { "Insufficient ${txCurrency} balance in ${source.name}." }
            val resolvedFunnel = funnelId ?: getSystemUnclassifiedFunnel().id
            recordExpenseInternal(pitakaId, name, amount, canonicalExpenseCategory(category), resolvedFunnel, txCurrency, funnelAmount, funnelCurrency, date)
        }
    }

    private suspend fun recordExpenseInternal(
        pitakaId: Long, name: String, amount: Double, category: String?, funnelId: Long? = null, currency: String? = null, funnelAmount: Double? = null, funnelCurrency: String? = null, date: Long = System.currentTimeMillis()
    ) {
        val source = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
        val txCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
        require((CurrencyBalances.parse(source.currencyBalances)[txCurrency] ?: 0.0) >= amount) {
            "Insufficient " + txCurrency + " balance in " + source.name + "."
        }
        val entry = LedgerEntry(
            type = LedgerType.EXPENSE, amount = amount, currency = txCurrency, name = name, category = category, pitakaId = pitakaId, funnelId = funnelId, funnelAmount = funnelAmount ?: amount, funnelCurrency = funnelCurrency ?: txCurrency, date = date
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
        require(amount > 0 && amount.isFinite()) { "Transfer amount must be a positive finite number" }
        db.withTransaction {
            val source = pitakaDao.getPitaka(fromPitakaId) ?: error("Source Pitaka not found.")
            val destination = pitakaDao.getPitaka(toPitakaId) ?: error("Destination Pitaka not found.")
            require(fromPitakaId != toPitakaId) { "Source and destination must be different." }
            val sourceCurrency = source.currency.uppercase()
            require((CurrencyBalances.parse(source.currencyBalances)[sourceCurrency] ?: 0.0) >= amount) { "Insufficient ${sourceCurrency} balance in ${source.name}." }
            val destinationCurrency = destination.currency.uppercase()
            if (sourceCurrency != destinationCurrency) {
                require(secondaryAmount != null && secondaryAmount > 0 && secondaryAmount.isFinite()) {
                    "A positive destination amount is required for a cross-currency transfer."
                }
                val rates = currencyDao.getRatesOnce()
                require(rates.any { it.code.equals(sourceCurrency, true) } && rates.any { it.code.equals(destinationCurrency, true) }) {
                    "Exchange rates for ${sourceCurrency} and ${destinationCurrency} are required for a cross-currency transfer."
                }
            }
            val entry = LedgerEntry(
                type = LedgerType.TRANSFER,
                amount = amount,
                currency = sourceCurrency,
                name = name,
                fromPitakaId = fromPitakaId,
                toPitakaId = toPitakaId,
                secondaryAmount = if (destinationCurrency == sourceCurrency) null else (secondaryAmount ?: amount),
                secondaryCurrency = destinationCurrency,
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
        require(amount > 0 && amount.isFinite()) { "Contribution amount must be a positive finite number" }
        db.withTransaction {
            val source = pitakaDao.getPitaka(sourcePitakaId) ?: error("Source Pitaka not found.")
            val goal = goalDao.getGoal(goalId) ?: error("Goal not found.")
            val txCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
            require((CurrencyBalances.parse(source.currencyBalances)[txCurrency] ?: 0.0) >= amount) { "Insufficient ${txCurrency} balance in ${source.name}." }
            val goalCurrency = goal.currency.uppercase()
            require(txCurrency == goalCurrency) { "Contribution currency ${txCurrency} differs from goal currency ${goalCurrency}. Convert it first or select a matching currency." }
            val entry = LedgerEntry(
                type = LedgerType.GOAL_CONTRIBUTION, amount = amount, currency = txCurrency, name = name, pitakaId = sourcePitakaId, goalId = goalId, goalAmount = amount, goalCurrency = goalCurrency, date = date
            )
            ledgerDao.insertEntry(entry)
            applyEffect(entry)
        }
    }

    // ---- Balance effect helpers ----
    // applyEffect() is linear in `amount`, so reverseEffect() can just negate amount(s) and
    // re-apply the same formula — this correctly undoes any entry type, including edits.

    private suspend fun currencyForRecurring(pitakaId: Long): String =
        pitakaDao.getPitaka(pitakaId)?.currency?.uppercase() ?: "PHP"

    private suspend fun canonicalExpenseCategory(category: String?): String {
        val cleaned = category?.trim().orEmpty()
        if (cleaned.isEmpty()) return "Uncategorized Expense"
        return ledgerDao.findCanonicalExpenseCategory(cleaned) ?: cleaned
    }

    private suspend fun applyEffect(entry: LedgerEntry) {
        when (entry.type) {
            LedgerType.INCOME -> entry.pitakaId?.let { adjustBalance(it, entry.amount, entry.currency) }
            LedgerType.EXPENSE -> {
                entry.pitakaId?.let { adjustBalance(it, -entry.amount, entry.currency) }
                entry.funnelId?.let { id -> funnelDao.get(id)?.let { funnelDao.update(it.copy(currencyBalances = CurrencyBalances.add(it.currencyBalances, entry.funnelCurrency ?: entry.currency, entry.funnelAmount ?: entry.amount))) } }
            }
            LedgerType.GOAL_CONTRIBUTION -> {
                entry.pitakaId?.let { adjustBalance(it, -entry.amount, entry.currency) }
                entry.goalId?.let { id -> goalDao.getGoal(id)?.let { goalDao.updateGoal(it.copy(currencyBalances = CurrencyBalances.add(it.currencyBalances, entry.goalCurrency ?: entry.currency, entry.goalAmount ?: entry.amount))) } }
            }
            LedgerType.ADJUSTMENT -> entry.pitakaId?.let { adjustBalance(it, entry.amount, entry.currency) }
            LedgerType.TRANSFER -> {
                entry.fromPitakaId?.let { adjustBalance(it, -entry.amount, entry.currency) }
                entry.toPitakaId?.let { id ->
                    val destinationCurrency = entry.secondaryCurrency ?: pitakaDao.getPitaka(id)?.currency ?: entry.currency
                    adjustBalance(id, entry.secondaryAmount ?: entry.amount, destinationCurrency)
                }
            }
        }
    }

    private suspend fun reverseEffect(entry: LedgerEntry) {
        applyEffect(entry.copy(
            amount = -entry.amount,
            secondaryAmount = entry.secondaryAmount?.let { -it },
            funnelAmount = entry.funnelAmount?.let { -it },
            goalAmount = entry.goalAmount?.let { -it }
        ))
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
