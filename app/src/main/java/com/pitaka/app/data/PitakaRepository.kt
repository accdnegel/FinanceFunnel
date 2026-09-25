package com.pitaka.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        require(startingBalance.isFinite() && startingBalance >= 0) { "Starting balance must be a non-negative finite number." }
        require(name.trim().isNotBlank()) { "Pitaka name cannot be blank." }
        val code = currency.trim().uppercase().ifBlank { "PHP" }
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }

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
        db.withTransaction {
            val p = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            require(parentPitakaId == null || parentPitakaId != pitakaId) { "A Pitaka cannot be its own parent." }

            if (parentPitakaId != null) {
                val parent = pitakaDao.getPitaka(parentPitakaId) ?: error("Parent Pitaka not found.")
                // A parent is a logical container. Re-parenting is therefore allowed only
                // when it does not turn a financially active Pitaka into a child of itself
                // through an ancestor cycle.
                var cursor: Long? = parent.id
                while (cursor != null) {
                    require(cursor != pitakaId) { "This parent selection would create a hierarchy cycle." }
                    cursor = pitakaDao.getPitaka(cursor)?.parentPitakaId
                }
            }
            pitakaDao.updatePitaka(p.copy(parentPitakaId = parentPitakaId))
        }
    }

    /** Metadata-only edit (name/currency/color) — never touches the balance. */
    suspend fun updatePitakaMeta(pitakaId: Long, name: String, currency: String, colorHex: String?, cardStyle: String = "solid") {
        db.withTransaction {
            val existing = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            val code = currency.trim().uppercase()
            require(name.trim().isNotBlank()) { "Pitaka name cannot be blank." }
            require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
            val balances = CurrencyBalances.parse(existing.currencyBalances)
            require(code == existing.currency.uppercase() || (balances[code] ?: 0.0) == 0.0) {
                "Cannot change the primary currency while that currency has a non-zero balance. Move or reconcile the balance first."
            }
            pitakaDao.updatePitaka(existing.copy(
                name = name.trim(),
                currency = code,
                colorHex = colorHex,
                cardStyle = cardStyle,
                currencyBalances = if (balances.isEmpty() && existing.currentAmount != 0.0)
                    CurrencyBalances.encode(mapOf(code to existing.currentAmount))
                else existing.currencyBalances
            ))
        }
    }

    /** Archives a Pitaka without deleting its ledger history or balances. */
    suspend fun archivePitaka(pitakaId: Long) {
        db.withTransaction {
            val existing = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            require(existing.archivedAt == null) { "Pitaka is already archived." }
            require(pitakaDao.countChildren(pitakaId) == 0) { "Reassign or archive child Pitakas before archiving this parent." }
            pitakaDao.updatePitaka(existing.copy(archivedAt = System.currentTimeMillis()))
        }
    }

    suspend fun restorePitaka(pitakaId: Long) {
        db.withTransaction {
            val existing = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            require(existing.archivedAt != null) { "Pitaka is not archived." }
            existing.parentPitakaId?.let { parentId ->
                val parent = pitakaDao.getPitaka(parentId)
                require(parent == null || parent.archivedAt == null) { "Restore the parent Pitaka first." }
            }
            pitakaDao.updatePitaka(existing.copy(archivedAt = null))
        }
    }

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
            require(newBalance.isFinite() && newBalance >= 0) { "Adjusted balance must be a non-negative finite number." }
            val pitaka = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            val code = currency?.trim()?.uppercase()?.ifBlank { null } ?: pitaka.currency.uppercase()
            require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
            val current = CurrencyBalances.parse(pitaka.currencyBalances)[code] ?: 0.0
            val delta = newBalance - current
            require(delta.isFinite()) { "Adjustment amount must be finite." }
            if (delta == 0.0) return@withTransaction
            val snapshot = historicalConversionSnapshot(code, delta)
            val entry = LedgerEntry(
                type = LedgerType.ADJUSTMENT,
                amount = delta,
                currency = code,
                name = note.trim().ifBlank { "Manual adjustment" },
                pitakaId = pitakaId,
                conversionRateToBaseAtTransaction = snapshot.first,
                amountInBaseAtTransaction = snapshot.second,
                baseCurrencyAtTransaction = snapshot.third
            )
            ledgerDao.insertEntry(entry)
            applyEffect(entry)
        }
    }

    // ---- Goals ----

    fun observeGoals(): Flow<List<GoalWithProgress>> = goalDao.observeGoalsWithProgress()

    fun observeTotalProgressForType(type: GoalType): Flow<Double> = goalDao.observeTotalProgressForType(type)

    suspend fun getGoal(id: Long): Goal? = goalDao.getGoal(id)

    suspend fun createGoal(
        name: String,
        type: GoalType,
        targetAmount: Double,
        targetDate: Long,
        colorHex: String?,
        cardStyle: String = "solid",
        currency: String = "PHP"
    ): Long {
        val code = currency.trim().uppercase().ifBlank { "PHP" }
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
        require(targetAmount > 0 && targetAmount.isFinite()) { "Goal target must be a positive finite number." }
        return goalDao.insertGoal(
            Goal(
                name = name.trim().ifBlank { error("Goal name cannot be blank.") },
                type = type,
                targetAmount = targetAmount,
                currency = code,
                targetDate = targetDate,
                colorHex = colorHex,
                cardStyle = cardStyle,
                currencyBalances = CurrencyBalances.encode(mapOf(code to 0.0))
            )
        )
    }

    suspend fun updateGoal(
        goalId: Long,
        name: String,
        type: GoalType,
        targetAmount: Double,
        targetDate: Long,
        colorHex: String?,
        cardStyle: String = "solid",
        currency: String? = null
    ) {
        val existing = goalDao.getGoal(goalId) ?: return
        val code = (currency?.trim()?.uppercase()?.ifBlank { null } ?: existing.currency.uppercase())
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
        require(targetAmount > 0 && targetAmount.isFinite()) { "Goal target must be a positive finite number." }
        val balances = CurrencyBalances.parse(existing.currencyBalances)
        require(code == existing.currency || (balances[code] ?: 0.0) == 0.0) {
            "Cannot change the goal currency while that currency has a non-zero balance. Move or reconcile the balance first."
        }
        goalDao.updateGoal(
            existing.copy(
                name = name.trim().ifBlank { error("Goal name cannot be blank.") },
                type = type,
                targetAmount = targetAmount,
                currency = code,
                targetDate = targetDate,
                colorHex = colorHex,
                cardStyle = cardStyle,
                currencyBalances = if (balances.isEmpty()) CurrencyBalances.encode(mapOf(code to 0.0)) else existing.currencyBalances
            )
        )
    }

    /**
     * Deletes a Goal but deliberately leaves its GOAL_CONTRIBUTION ledger rows alone — that
     * money genuinely left its source Pitaka and should stay reflected in that Pitaka's
     * history; only the goal-progress tracking for it goes away.
     */
    suspend fun archiveGoal(goalId: Long) {
        val goal = goalDao.getGoal(goalId) ?: error("Goal not found.")
        require(goal.archivedAt == null) { "Goal is already archived." }
        goalDao.updateGoal(goal.copy(archivedAt = System.currentTimeMillis()))
    }

    suspend fun restoreGoal(goalId: Long) {
        val goal = goalDao.getGoal(goalId) ?: error("Goal not found.")
        require(goal.archivedAt != null) { "Goal is not archived." }
        goalDao.updateGoal(goal.copy(archivedAt = null))
    }

    suspend fun deleteGoal(goal: Goal) {
        require(goal.archivedAt != null) { "Archive the Goal before permanent deletion." }
        require(goalDao.countContributions(goal.id) == 0) { "This Goal has contribution history. It cannot be permanently deleted." }
        goalDao.deleteGoal(goal)
    }

    // ---- Expense funnels ----
    fun observeExpenseFunnels(): Flow<List<ExpenseFunnel>> = funnelDao.observeAll()
    suspend fun getSystemUnclassifiedFunnel(): ExpenseFunnel {
        return funnelDao.getByName("Unclassified Expense") ?: funnelDao.insertAndReturn(ExpenseFunnel(name = "Unclassified Expense", limit = 0.0, currency = "PHP", currencyBalances = "PHP=0", isSystem = true)).let { funnelDao.get(it)!! }
    }
    fun observeFunnelSpent(funnelId: Long): Flow<Double> = funnelDao.observeSpent(funnelId)

    fun observeGoalProgressByCurrency(goalId: Long): Flow<Map<String, Double>> =
        ledgerDao.observeAllEntries().map { entries ->
            entries.asSequence()
                .filter { it.type == LedgerType.GOAL_CONTRIBUTION && it.goalId == goalId }
                .groupBy { (it.goalCurrency ?: it.currency).uppercase() }
                .mapValues { (_, rows) -> rows.sumOf { it.goalAmount ?: it.amount } }
        }

    fun observeFunnelSpentByCurrency(funnelId: Long): Flow<Map<String, Double>> =
        ledgerDao.observeAllEntries().map { entries ->
            entries.asSequence()
                .filter { it.type == LedgerType.EXPENSE && it.funnelId == funnelId }
                .groupBy { (it.funnelCurrency ?: it.currency).uppercase() }
                .mapValues { (_, rows) -> rows.sumOf { it.funnelAmount ?: it.amount } }
        }
    suspend fun createExpenseFunnel(
        name: String,
        limit: Double,
        validFrom: Long?,
        validUntil: Long?,
        colorHex: String?,
        cardStyle: String = "solid",
        currency: String = "PHP"
    ): Long {
        val code = currency.trim().uppercase().ifBlank { "PHP" }
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
        require(limit >= 0 && limit.isFinite()) { "Funnel limit must be a non-negative finite number." }
        require(validFrom == null || validUntil == null || validFrom <= validUntil) { "Funnel start date must not be after its end date." }
        return funnelDao.insert(
            ExpenseFunnel(
                name = name.trim().ifBlank { error("Funnel name cannot be blank.") },
                limit = limit,
                currency = code,
                currencyBalances = CurrencyBalances.encode(mapOf(code to 0.0)),
                validFrom = validFrom,
                validUntil = validUntil,
                colorHex = colorHex,
                cardStyle = cardStyle
            )
        )
    }
    suspend fun updateExpenseFunnel(funnel: ExpenseFunnel) {
        db.withTransaction {
            val existing = funnelDao.get(funnel.id) ?: error("Expense funnel not found.")
            require(!existing.isSystem) { "System expense funnels cannot be edited." }
            val name = funnel.name.trim()
            val code = funnel.currency.trim().uppercase()
            require(name.isNotBlank()) { "Funnel name cannot be blank." }
            require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
            require(funnel.limit >= 0 && funnel.limit.isFinite()) { "Funnel limit must be a non-negative finite number." }
            require(funnel.validFrom == null || funnel.validUntil == null || funnel.validFrom <= funnel.validUntil) {
                "Funnel start date must not be after its end date."
            }
            val balances = CurrencyBalances.parse(existing.currencyBalances)
            require(code == existing.currency.uppercase() || (balances[code] ?: 0.0) == 0.0) {
                "Cannot change the funnel currency while that currency has a non-zero balance. Move or reconcile the balance first."
            }
            funnelDao.update(existing.copy(
                name = name,
                currency = code,
                limit = funnel.limit,
                validFrom = funnel.validFrom,
                validUntil = funnel.validUntil,
                colorHex = funnel.colorHex,
                cardStyle = funnel.cardStyle
            ))
        }
    }
    suspend fun archiveExpenseFunnel(funnelId: Long) {
        val funnel = funnelDao.get(funnelId) ?: error("Expense funnel not found.")
        require(!funnel.isSystem) { "System expense funnels cannot be archived." }
        require(funnel.archivedAt == null) { "Expense funnel is already archived." }
        funnelDao.update(funnel.copy(archivedAt = System.currentTimeMillis()))
    }

    suspend fun restoreExpenseFunnel(funnelId: Long) {
        val funnel = funnelDao.get(funnelId) ?: error("Expense funnel not found.")
        require(funnel.archivedAt != null) { "Expense funnel is not archived." }
        funnelDao.update(funnel.copy(archivedAt = null))
    }

    suspend fun deleteExpenseFunnel(funnel: ExpenseFunnel) {
        require(!funnel.isSystem) { "System expense funnels cannot be deleted." }
        require(funnel.archivedAt != null) { "Archive the expense funnel before permanent deletion." }
        require(funnelDao.countExpenses(funnel.id) == 0) { "This funnel has expense history. It cannot be permanently deleted." }
        funnelDao.delete(funnel)
    }

    // ---- Ledger reads ----

    fun observeEntriesForPitaka(pitakaId: Long): Flow<List<LedgerEntry>> = ledgerDao.observeEntriesForPitaka(pitakaId)

    fun observeEntriesForGoal(goalId: Long): Flow<List<LedgerEntry>> = ledgerDao.observeEntriesForGoal(goalId)

    fun observeAllExpenses(): Flow<List<LedgerEntry>> = ledgerDao.observeAllExpenses()

    fun observeFinancialEntries(): Flow<List<LedgerEntry>> = ledgerDao.observeAllEntries()

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
            require(newName.trim().isNotBlank()) { "Transaction name cannot be blank." }
            require(newAmount.isFinite()) { "Transaction amount must be finite." }
            require(newAmount > 0 || oldEntry.type == LedgerType.ADJUSTMENT) { "Transaction amount must be positive." }

            // Reverse first, then validate against the resulting account state. Room rolls
            // the entire transaction back if any validation fails.
            reverseEffect(oldEntry)

            if (oldEntry.type == LedgerType.INCOME || oldEntry.type == LedgerType.EXPENSE || oldEntry.type == LedgerType.GOAL_CONTRIBUTION) {
                val target = newPitakaId?.let { pitakaDao.getPitaka(it) }
                require(target != null) { "Target Pitaka not found." }
                if (oldEntry.type == LedgerType.EXPENSE) {
                    require(target!!.currency.equals(oldEntry.currency, ignoreCase = true)) {
                        "Changing an expense to a Pitaka with a different currency requires a currency-aware edit."
                    }
                }
                val available = CurrencyBalances.parse(target.currencyBalances)[oldEntry.currency.uppercase()] ?: 0.0
                if (oldEntry.type != LedgerType.INCOME) {
                    require(available >= newAmount) {
                        "Insufficient " + oldEntry.currency.uppercase() + " balance in " + target.name + "."
                    }
                }
                if (oldEntry.type == LedgerType.GOAL_CONTRIBUTION) {
                    val goal = oldEntry.goalId?.let { goalDao.getGoal(it) }
                    require(goal != null) { "Goal not found." }
                }
            }

            val normalizedCategory = if (oldEntry.type == LedgerType.EXPENSE) canonicalExpenseCategory(newCategory) else null
            val ratio = AccountingMath.editRatio(oldEntry.amount, newAmount)

            // Allocation amounts are part of the ledger event, not independent balances.
            // When the transaction amount changes, preserve an explicitly converted
            // funnel/goal allocation by scaling it with the same transaction ratio.
            val updatedFunnelAmount = oldEntry.funnelAmount?.let { oldAllocation ->
                require(oldAllocation.isFinite()) { "Existing funnel allocation is invalid." }
                AccountingMath.scaleAllocation(oldAllocation, ratio)
            }
            val updatedGoalAmount = oldEntry.goalAmount?.let { oldAllocation ->
                require(oldAllocation.isFinite()) { "Existing goal allocation is invalid." }
                AccountingMath.scaleAllocation(oldAllocation, ratio)
            }
            // A transfer has two monetary legs. When the source amount is edited,
            // preserve the original exchange relationship by scaling the destination
            // amount by the same ratio. This is essential for cross-currency edits;
            // otherwise reversal removes the old destination amount but re-application
            // silently restores the stale amount.
            val updatedSecondaryAmount = if (oldEntry.type == LedgerType.TRANSFER) {
                oldEntry.secondaryAmount?.let { destinationAmount ->
                    require(destinationAmount.isFinite()) { "Existing transfer destination amount is invalid." }
                    AccountingMath.scaleAllocation(destinationAmount, ratio)
                }
            } else {
                oldEntry.secondaryAmount
            }

            // Historical base-currency snapshots belong to the ledger event. When an
            // amount is edited, keep the transaction-time rate/base currency but
            // recompute the stored base amount from the new transaction amount.
            val updatedAmountInBase = oldEntry.conversionRateToBaseAtTransaction?.let { rate ->
                require(rate.isFinite() && rate > 0.0) { "Existing historical conversion rate is invalid." }
                MoneyMath.multiply(newAmount, rate)
            }
            val updatedSecondaryAmountInBase = oldEntry.secondaryAmountInBaseAtTransaction?.let { oldBaseAmount ->
                require(oldBaseAmount.isFinite()) { "Existing historical destination base amount is invalid." }
                val oldSecondary = oldEntry.secondaryAmount
                if (oldSecondary != null && oldSecondary != 0.0 && oldEntry.secondaryConversionRateToBaseAtTransaction != null) {
                    val secondaryRate = oldEntry.secondaryConversionRateToBaseAtTransaction
                    require(secondaryRate.isFinite() && secondaryRate > 0.0) { "Existing destination historical conversion rate is invalid." }
                    require(updatedSecondaryAmount != null) { "Historical destination snapshot has no destination amount." }
                    MoneyMath.multiply(updatedSecondaryAmount, secondaryRate)
                } else {
                    AccountingMath.scaleAllocation(oldBaseAmount, ratio)
                }
            }

            if (oldEntry.type == LedgerType.TRANSFER) {
                val fromId = requireNotNull(oldEntry.fromPitakaId) { "Transfer has no source Pitaka." }
                val toId = requireNotNull(oldEntry.toPitakaId) { "Transfer has no destination Pitaka." }
                require(fromId != toId) { "Transfer source and destination must differ." }
                require(pitakaDao.getPitaka(fromId) != null) { "Transfer source Pitaka not found." }
                require(pitakaDao.getPitaka(toId) != null) { "Transfer destination Pitaka not found." }
                if (oldEntry.secondaryCurrency != null && !oldEntry.secondaryCurrency.equals(oldEntry.currency, true)) {
                    requireNotNull(updatedSecondaryAmount) { "Cross-currency transfer has no destination amount." }
                }
            }

            if (oldEntry.type == LedgerType.EXPENSE) {
                val funnelId = requireNotNull(oldEntry.funnelId) { "Expense has no funnel." }
                require(funnelDao.get(funnelId) != null) { "Expense funnel not found." }
                requireNotNull(updatedFunnelAmount) { "Expense has no funnel allocation." }
            }
            if (oldEntry.type == LedgerType.GOAL_CONTRIBUTION) {
                val goalId = requireNotNull(oldEntry.goalId) { "Goal contribution has no Goal." }
                require(goalDao.getGoal(goalId) != null) { "Goal for contribution not found." }
                requireNotNull(updatedGoalAmount) { "Goal contribution has no goal allocation." }
            }

            val updated = oldEntry.copy(
                name = newName.trim(),
                amount = newAmount,
                category = normalizedCategory,
                pitakaId = newPitakaId,
                funnelAmount = updatedFunnelAmount,
                goalAmount = updatedGoalAmount,
                secondaryAmount = updatedSecondaryAmount,
                amountInBaseAtTransaction = updatedAmountInBase,
                secondaryAmountInBaseAtTransaction = updatedSecondaryAmountInBase
            )
            ledgerDao.updateEntry(updated)
            applyEffect(updated)
        }
    }

    // ---- Monthly expense budgets ----

    fun observeEffectiveBudget(month: String): Flow<MonthlyBudget?> = budgetDao.observeEffectiveBudget(month)

    fun observeAllBudgets(): Flow<List<MonthlyBudget>> = budgetDao.observeAllBudgets()

    suspend fun getExactBudgetForMonth(month: String): MonthlyBudget? = budgetDao.getExactForMonth(month)

    suspend fun setMonthlyExpenseLimit(month: String, limit: Double?) {
        require(month.matches(Regex("\\d{4}-\\d{2}"))) { "Budget month must use yyyy-MM format." }
        require(limit == null || (limit.isFinite() && limit >= 0.0)) { "Monthly expense limit must be a non-negative finite number." }
        if (limit == null) {
            budgetDao.clearForMonth(month)
        } else {
            budgetDao.upsert(MonthlyBudget(month = month, limit = limit))
        }
    }

    // ---- Currency ----

    fun observeCurrencySettings(): Flow<CurrencySettings?> = currencyDao.observeSettings()

    suspend fun setBaseCurrency(code: String) {
        val normalized = code.trim().uppercase()
        require(normalized.isNotBlank()) { "Base currency cannot be blank." }
        require(normalized.length == 3 && normalized.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
        currencyDao.upsertSettings(CurrencySettings(baseCurrency = normalized))
    }

    fun observeExchangeRates(): Flow<List<ExchangeRate>> = currencyDao.observeRates()

    suspend fun setExchangeRate(code: String, rateToBase: Double) {
        val normalized = code.trim().uppercase()
        require(normalized.length == 3 && normalized.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
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
            require(rule.type == LedgerType.INCOME || rule.type == LedgerType.EXPENSE) {
                "Recurring rules only support income and expense transactions."
            }
            require(rule.name.trim().isNotBlank()) { "Recurring rule name cannot be blank." }
            require(rule.amount > 0 && rule.amount.isFinite()) { "Recurring rule amount must be a positive finite number." }
            require(rule.dayOfMonth in 1..31) { "Recurring rule day must be between 1 and 31." }
            val effectiveDay = rule.dayOfMonth.coerceAtMost(today.lengthOfMonth())
            if (today.dayOfMonth < effectiveDay) continue

            db.withTransaction {
                val scheduledDate = today.withDayOfMonth(effectiveDay)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                val currency = rule.currency.trim().uppercase()
                require(currency.length == 3 && currency.all { it in 'A'..'Z' }) { "Recurring rule has an invalid currency." }
                pitakaDao.getPitaka(rule.pitakaId) ?: error("Pitaka for recurring rule not found.")
                when (rule.type) {
                    LedgerType.INCOME -> recordIncomeInternal(rule.pitakaId, rule.name + " (recurring)", rule.amount, date = scheduledDate, currency = currency)
                    LedgerType.EXPENSE -> recordExpenseInternal(rule.pitakaId, rule.name + " (recurring)", rule.amount, rule.category, null, currency, date = scheduledDate)
                    else -> error("Unsupported recurring transaction type.")
                }
                recurringDao.update(rule.copy(lastAppliedMonth = currentMonth))
            }
        }
    }

    // ---- Money-movement operations (all atomic) ----

    suspend fun recordIncome(pitakaId: Long, name: String, amount: Double, date: Long = System.currentTimeMillis()) {
        require(name.trim().isNotBlank()) { "Income name cannot be blank." }
        require(amount > 0 && amount.isFinite()) { "Income amount must be a positive finite number" }
        db.withTransaction {
            val pitaka = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            val currency = pitaka.currency.uppercase()
            recordIncomeInternal(pitakaId, name, amount, date, currency)
        }
    }

    private suspend fun recordIncomeInternal(pitakaId: Long, name: String, amount: Double, date: Long = System.currentTimeMillis(), currency: String? = null) {
        val pitakaCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: pitakaDao.getPitaka(pitakaId)?.currency ?: "PHP"
        val snapshot = historicalConversionSnapshot(pitakaCurrency, amount)
        val entry = LedgerEntry(type = LedgerType.INCOME, amount = amount, currency = pitakaCurrency, name = name, pitakaId = pitakaId, date = date, conversionRateToBaseAtTransaction = snapshot.first, amountInBaseAtTransaction = snapshot.second, baseCurrencyAtTransaction = snapshot.third)
        ledgerDao.insertEntry(entry)
        applyEffect(entry)
    }

    suspend fun recordExpense(pitakaId: Long, name: String, amount: Double, category: String?, funnelId: Long? = null, currency: String? = null, funnelAmount: Double? = null, funnelCurrency: String? = null, date: Long = System.currentTimeMillis()) {
        require(amount > 0 && amount.isFinite()) { "Expense amount must be a positive finite number" }
        require(funnelAmount == null || (funnelAmount > 0 && funnelAmount.isFinite())) {
            "Funnel allocation must be a positive finite number."
        }
        funnelCurrency?.trim()?.uppercase()?.let { code ->
            require(code.length == 3 && code.all { it in 'A'..'Z' }) {
                "Funnel currency code must be exactly 3 letters."
            }
        }
        db.withTransaction {
            val source = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            val txCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
            require((CurrencyBalances.parse(source.currencyBalances)[txCurrency] ?: 0.0) >= amount) { "Insufficient ${txCurrency} balance in ${source.name}." }
            val resolvedFunnel = funnelId ?: getSystemUnclassifiedFunnel().id
            require(funnelDao.get(resolvedFunnel) != null) { "Expense Funnel not found." }
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
        val snapshot = historicalConversionSnapshot(txCurrency, amount)
        val entry = LedgerEntry(
            type = LedgerType.EXPENSE, amount = amount, currency = txCurrency, name = name, category = category, pitakaId = pitakaId, funnelId = funnelId, funnelAmount = funnelAmount ?: amount, funnelCurrency = funnelCurrency ?: txCurrency, date = date,
            conversionRateToBaseAtTransaction = snapshot.first,
            amountInBaseAtTransaction = snapshot.second,
            baseCurrencyAtTransaction = snapshot.third
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
            require((CurrencyBalances.parse(source.currencyBalances)[sourceCurrency] ?: 0.0) >= amount) { "Insufficient " + sourceCurrency + " balance in " + source.name + "." }
            require(name.trim().isNotBlank()) { "Transfer name cannot be blank." }
            val destinationCurrency = destination.currency.uppercase()
            if (sourceCurrency != destinationCurrency) {
                require(secondaryAmount != null && secondaryAmount > 0 && secondaryAmount.isFinite()) {
                    "A positive destination amount is required for a cross-currency transfer."
                }
                val baseCurrency = currencyDao.observeSettings().first()?.baseCurrency?.uppercase() ?: "PHP"
                val rates = currencyDao.getRatesOnce()
                fun hasUsableRate(code: String): Boolean =
                    code.equals(baseCurrency, true) ||
                        rates.any { it.code.equals(code, true) && it.rateToBase.isFinite() && it.rateToBase > 0.0 }
                require(hasUsableRate(sourceCurrency) && hasUsableRate(destinationCurrency)) {
                    "Usable exchange rates for ${sourceCurrency} and ${destinationCurrency} are required for a cross-currency transfer."
                }
            }
            val destinationAmount = if (destinationCurrency == sourceCurrency) amount else (secondaryAmount ?: amount)
            val entry = LedgerEntry(
                type = LedgerType.TRANSFER,
                amount = amount,
                currency = sourceCurrency,
                name = name,
                fromPitakaId = fromPitakaId,
                toPitakaId = toPitakaId,
                secondaryAmount = if (destinationCurrency == sourceCurrency) null else (secondaryAmount ?: amount),
                secondaryCurrency = destinationCurrency,
                date = date,
                conversionRateToBaseAtTransaction = historicalConversionSnapshot(sourceCurrency, amount).first,
                amountInBaseAtTransaction = historicalConversionSnapshot(sourceCurrency, amount).second,
                baseCurrencyAtTransaction = historicalConversionSnapshot(sourceCurrency, amount).third,
                secondaryConversionRateToBaseAtTransaction = historicalConversionSnapshot(destinationCurrency, destinationAmount).first,
                secondaryAmountInBaseAtTransaction = historicalConversionSnapshot(destinationCurrency, destinationAmount).second
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
        goalAmount: Double? = null,
        goalCurrency: String? = null,
        date: Long = System.currentTimeMillis()
    ) {
        require(amount > 0 && amount.isFinite()) { "Contribution amount must be a positive finite number" }
        db.withTransaction {
            val source = pitakaDao.getPitaka(sourcePitakaId) ?: error("Source Pitaka not found.")
            val goal = goalDao.getGoal(goalId) ?: error("Goal not found.")
            val txCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
            require((CurrencyBalances.parse(source.currencyBalances)[txCurrency] ?: 0.0) >= amount) { "Insufficient ${txCurrency} balance in ${source.name}." }
            val appliedGoalCurrency = goalCurrency?.trim()?.uppercase()?.ifBlank { null } ?: txCurrency
            val appliedGoalAmount = goalAmount ?: amount
            require(appliedGoalCurrency.length == 3 && appliedGoalCurrency.all { it in 'A'..'Z' }) { "Goal currency must be exactly 3 letters." }
            require(appliedGoalAmount > 0 && appliedGoalAmount.isFinite()) { "Goal allocation must be a positive finite number." }
            val snapshot = historicalConversionSnapshot(txCurrency, amount)
            val entry = LedgerEntry(
                type = LedgerType.GOAL_CONTRIBUTION,
                amount = amount,
                currency = txCurrency,
                name = name,
                pitakaId = sourcePitakaId,
                goalId = goalId,
                goalAmount = appliedGoalAmount,
                goalCurrency = appliedGoalCurrency,
                date = date,
                conversionRateToBaseAtTransaction = snapshot.first,
                amountInBaseAtTransaction = snapshot.second,
                baseCurrencyAtTransaction = snapshot.third
            )
            ledgerDao.insertEntry(entry)
            applyEffect(entry)
        }
    }

    // ---- Balance effect helpers ----
    // applyEffect() is linear in `amount`, so reverseEffect() can just negate amount(s) and
    // re-apply the same formula — this correctly undoes any entry type, including edits.

    private suspend fun historicalConversionSnapshot(currency: String, amount: Double): Triple<Double, Double, String> {
        val code = currency.trim().uppercase()
        val base = currencyDao.observeSettings().first()?.baseCurrency?.trim()?.uppercase()?.ifBlank { "PHP" } ?: "PHP"
        val rate = if (code == base) 1.0 else currencyDao.getRatesOnce()
            .firstOrNull { it.code.equals(code, true) }?.rateToBase
        require(rate != null && rate.isFinite() && rate > 0.0) {
            "A usable exchange rate for $code is required to record a historical base-currency snapshot."
        }
        val baseAmount = MoneyMath.multiply(amount, rate)
        require(baseAmount.isFinite()) { "Historical base-currency amount must be finite." }
        return Triple(rate, baseAmount, base)
    }

    private suspend fun currencyForRecurring(pitakaId: Long): String =
        pitakaDao.getPitaka(pitakaId)?.currency?.uppercase() ?: "PHP"

    private suspend fun canonicalExpenseCategory(category: String?): String {
        val cleaned = category?.trim().orEmpty()
        if (cleaned.isEmpty()) return "Uncategorized Expense"
        return ledgerDao.findCanonicalExpenseCategory(cleaned) ?: cleaned
    }

    private suspend fun applyEffect(entry: LedgerEntry) {
        require(entry.amount.isFinite()) { "Ledger amount must be finite." }

        when (entry.type) {
            LedgerType.INCOME -> {
                val pitakaId = requireNotNull(entry.pitakaId) { "Income ledger entry has no Pitaka." }
                require(pitakaDao.getPitaka(pitakaId) != null) { "Pitaka for income ledger entry not found." }
                adjustBalance(pitakaId, entry.amount, entry.currency)
            }
            LedgerType.EXPENSE -> {
                val pitakaId = requireNotNull(entry.pitakaId) { "Expense ledger entry has no Pitaka." }
                require(pitakaDao.getPitaka(pitakaId) != null) { "Pitaka for expense ledger entry not found." }
                adjustBalance(pitakaId, -entry.amount, entry.currency)

                val funnelId = requireNotNull(entry.funnelId) { "Expense ledger entry has no funnel." }
                val funnel = requireNotNull(funnelDao.get(funnelId)) { "Expense funnel for ledger entry not found." }
                val funnelCurrency = entry.funnelCurrency ?: entry.currency
                val funnelAmount = entry.funnelAmount ?: entry.amount
                require(funnelAmount.isFinite()) { "Funnel allocation must be finite." }
                funnelDao.update(funnel.copy(
                    currencyBalances = CurrencyBalances.add(funnel.currencyBalances, funnelCurrency, funnelAmount)
                ))
            }
            LedgerType.GOAL_CONTRIBUTION -> {
                val pitakaId = requireNotNull(entry.pitakaId) { "Goal contribution has no source Pitaka." }
                require(pitakaDao.getPitaka(pitakaId) != null) { "Pitaka for goal contribution not found." }
                adjustBalance(pitakaId, -entry.amount, entry.currency)

                val goalId = requireNotNull(entry.goalId) { "Goal contribution has no Goal." }
                val goal = requireNotNull(goalDao.getGoal(goalId)) { "Goal for ledger contribution not found." }
                val goalCurrency = entry.goalCurrency ?: entry.currency
                val goalAmount = entry.goalAmount ?: entry.amount
                require(goalAmount.isFinite()) { "Goal allocation must be finite." }
                goalDao.updateGoal(goal.copy(
                    currencyBalances = CurrencyBalances.add(goal.currencyBalances, goalCurrency, goalAmount)
                ))
            }
            LedgerType.ADJUSTMENT -> {
                val pitakaId = requireNotNull(entry.pitakaId) { "Adjustment has no Pitaka." }
                require(pitakaDao.getPitaka(pitakaId) != null) { "Pitaka for adjustment not found." }
                adjustBalance(pitakaId, entry.amount, entry.currency)
            }
            LedgerType.TRANSFER -> {
                val fromId = requireNotNull(entry.fromPitakaId) { "Transfer has no source Pitaka." }
                val toId = requireNotNull(entry.toPitakaId) { "Transfer has no destination Pitaka." }
                require(fromId != toId) { "Transfer source and destination must differ." }
                require(pitakaDao.getPitaka(fromId) != null) { "Source Pitaka for transfer not found." }
                require(pitakaDao.getPitaka(toId) != null) { "Destination Pitaka for transfer not found." }
                val destinationCurrency = entry.secondaryCurrency ?: pitakaDao.getPitaka(toId)!!.currency
                val destinationAmount = entry.secondaryAmount ?: entry.amount
                require(destinationAmount.isFinite()) { "Transfer destination amount must be finite." }
                adjustBalance(fromId, -entry.amount, entry.currency)
                adjustBalance(toId, destinationAmount, destinationCurrency)
            }
        }
    }

    private suspend fun reverseEffect(entry: LedgerEntry) {
        applyEffect(entry.copy(
            amount = AccountingMath.reverse(entry.amount),
            secondaryAmount = entry.secondaryAmount?.let(AccountingMath::reverse),
            funnelAmount = entry.funnelAmount?.let(AccountingMath::reverse),
            goalAmount = entry.goalAmount?.let(AccountingMath::reverse)
        ))
    }

    private suspend fun adjustBalance(pitakaId: Long, delta: Double, currency: String = "PHP") {
        val pitaka = pitakaDao.getPitaka(pitakaId) ?: return
        val balances = CurrencyBalances.add(pitaka.currencyBalances, currency, delta)
        val primaryDelta = if (pitaka.currency.equals(currency, ignoreCase = true)) delta else 0.0
        pitakaDao.updatePitaka(
            pitaka.copy(
                currentAmount = MoneyMath.add(pitaka.currentAmount, primaryDelta),
                currencyBalances = balances,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }
}
