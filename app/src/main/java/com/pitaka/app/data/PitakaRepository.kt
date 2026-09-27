package com.pitaka.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PitakaRepository(private val db: AppDatabase) {

    private val pitakaDao = db.pitakaDao()
    private val goalDao = db.goalDao()
    private val ledgerDao = db.ledgerDao()
    private val budgetDao = db.monthlyBudgetDao()
    private val currencyDao = db.currencyDao()
    private val funnelDao = db.expenseFunnelDao()

    // ---- Pitakas ----

    fun observePitakas(): Flow<List<Pitaka>> = pitakaDao.observePitakas()
    fun observeAllPitakas(): Flow<List<Pitaka>> = pitakaDao.observeAllPitakas()
    fun observeRootPitakas(): Flow<List<Pitaka>> = pitakaDao.observeRootPitakas()
    fun observeChildren(parentId: Long): Flow<List<Pitaka>> = pitakaDao.observeChildren(parentId)

    suspend fun getPitaka(id: Long): Pitaka? = pitakaDao.getPitaka(id)

    private suspend fun requireTransactionLeaf(pitakaId: Long, role: String = "Pitaka"): Pitaka {
        val pitaka = pitakaDao.getPitaka(pitakaId) ?: error("$role not found.")
        require(pitaka.archivedAt == null) { "$role is archived." }
        require(pitakaDao.countChildren(pitakaId) == 0) {
            "$role is a parent container. Select one of its sub-Pitakas instead."
        }
        return pitaka
    }

    suspend fun addSubPitaka(parentId: Long, name: String, startingBalance: Double, currency: String, colorHex: String?, cardStyle: String = "solid"): Long =
        createPitaka(name, startingBalance, currency, colorHex, parentId, cardStyle)

    suspend fun createPitaka(name: String, startingBalance: Double, currency: String, colorHex: String?, parentPitakaId: Long? = null, cardStyle: String = "solid"): Long {
        require(startingBalance.isFinite() && startingBalance >= 0) { "Starting balance must be a non-negative finite number." }
        require(name.trim().isNotBlank()) { "Pitaka name cannot be blank." }
        val code = currency.trim().uppercase().ifBlank { "PHP" }
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }

        return db.withTransaction {
            require(pitakaDao.countByNormalizedName(name) == 0) { "A Pitaka with this name already exists." }
            val parent = parentPitakaId?.let { pitakaDao.getPitaka(it) }
            require(parentPitakaId == null || parent != null) { "Parent Pitaka not found." }

            // A lone Pitaka becoming a parent must not lose its existing money. Its first
            // child inherits every existing currency balance, then receives the child's
            // starting amount. The parent becomes a pure container.
            if (parent != null) {
                require(parent.archivedAt == null) { "Cannot create a child Pitaka under an archived parent." }
                require(parent.parentPitakaId == null) { "A sub-Pitaka cannot contain another sub-Pitaka." }
            }
            if (parent != null && pitakaDao.countChildren(parent.id) == 0) {
                val inherited = CurrencyBalances.parse(parent.currencyBalances)
                if (inherited.isEmpty() && parent.currentAmount != 0.0) {
                    inherited[parent.currency.uppercase()] = parent.currentAmount
                }
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
                ledgerDao.reassignPitakaReferences(parent.id, childId)
                pitakaDao.updatePitaka(
                    parent.copy(
                        currentAmount = 0.0,
                        currencyBalances = CurrencyBalances.encode(emptyMap()),
                        lastUpdated = System.currentTimeMillis()
                    )
                )
                recordOpeningBalance(childId, startingBalance, code)
                childId
            } else {
                val pitakaId = pitakaDao.insertPitaka(
                    Pitaka(
                        name = name,
                        currentAmount = 0.0,
                        currency = code,
                        currencyBalances = CurrencyBalances.encode(mapOf(code to 0.0)),
                        colorHex = colorHex,
                        parentPitakaId = parentPitakaId,
                        cardStyle = cardStyle
                    )
                )
                recordOpeningBalance(pitakaId, startingBalance, code)
                pitakaId
            }
        }
    }

    private suspend fun recordOpeningBalance(pitakaId: Long, amount: Double, currency: String) {
        if (amount == 0.0) return
        val entry = LedgerEntry(
            type = LedgerType.OPENING_BALANCE,
            amount = amount,
            currency = currency,
            name = "Opening balance",
            pitakaId = pitakaId
        )
        ledgerDao.insertEntry(entry)
        applyEffect(entry)
    }

    suspend fun setPitakaParent(pitakaId: Long, parentPitakaId: Long?) {
        db.withTransaction {
            val p = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            reparentPitaka(p, parentPitakaId)
        }
    }

    /** Atomically updates metadata and hierarchy, preserving balances during first-child conversion. */
    suspend fun updatePitakaMeta(pitakaId: Long, name: String, currency: String, colorHex: String?, cardStyle: String = "solid", parentPitakaId: Long? = null) {
        db.withTransaction {
            val existing = pitakaDao.getPitaka(pitakaId) ?: error("Pitaka not found.")
            require(existing.archivedAt == null) { "Cannot edit an archived Pitaka." }
            val code = currency.trim().uppercase()
            require(name.trim().isNotBlank()) { "Pitaka name cannot be blank." }
            require(pitakaDao.countByNormalizedName(name, pitakaId) == 0) { "A Pitaka with this name already exists." }
            require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
            val reparented = reparentPitaka(existing, parentPitakaId)
            val balances = CurrencyBalances.parse(reparented.currencyBalances)
            pitakaDao.updatePitaka(reparented.copy(
                name = name.trim(),
                currency = code,
                currentAmount = balances[code] ?: 0.0,
                colorHex = colorHex,
                cardStyle = cardStyle,
                currencyBalances = if (balances.isEmpty() && reparented.currentAmount != 0.0)
                    CurrencyBalances.encode(mapOf(code to reparented.currentAmount))
                else reparented.currencyBalances
            ))
        }
    }

    private suspend fun reparentPitaka(pitaka: Pitaka, parentPitakaId: Long?): Pitaka {
        require(pitaka.archivedAt == null) { "Cannot change the hierarchy of an archived Pitaka." }
        require(parentPitakaId == null || parentPitakaId != pitaka.id) { "A Pitaka cannot be its own parent." }
        if (parentPitakaId == pitaka.parentPitakaId) return pitaka

        val parent = parentPitakaId?.let { pitakaDao.getPitaka(it) }
        require(parentPitakaId == null || parent != null) { "Parent Pitaka not found." }
        if (parent != null) {
            require(parent.archivedAt == null) { "Cannot assign an archived Pitaka as parent." }
            require(parent.parentPitakaId == null) { "A sub-Pitaka cannot contain another sub-Pitaka." }
            require(pitakaDao.countChildren(pitaka.id) == 0) {
                "A parent Pitaka cannot become a sub-Pitaka while it still has children."
            }
        }

        var updated = pitaka.copy(parentPitakaId = parentPitakaId)
        if (parent != null && pitakaDao.countChildren(parent.id) == 0) {
            val mergedBalances = CurrencyBalances.parse(updated.currencyBalances)
            CurrencyBalances.parse(parent.currencyBalances).forEach { (currency, amount) ->
                mergedBalances[currency] = MoneyMath.add(mergedBalances[currency] ?: 0.0, amount)
            }
            if (mergedBalances.isEmpty() && parent.currentAmount != 0.0) {
                mergedBalances[parent.currency.uppercase()] = parent.currentAmount
            }
            updated = updated.copy(
                currentAmount = mergedBalances[updated.currency.uppercase()] ?: 0.0,
                currencyBalances = CurrencyBalances.encode(mergedBalances),
                lastUpdated = System.currentTimeMillis()
            )
            ledgerDao.reassignPitakaReferences(parent.id, pitaka.id)
            pitakaDao.updatePitaka(parent.copy(
                currentAmount = 0.0,
                currencyBalances = CurrencyBalances.encode(emptyMap()),
                lastUpdated = System.currentTimeMillis()
            ))
        }
        pitakaDao.updatePitaka(updated)
        return updated
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
            val children = pitakaDao.countChildren(pitaka.id)
            if (children > 0) {
                require(ledgerDao.countEntriesForPitaka(pitaka.id) == 0) {
                    "Move this parent's direct transaction history to a sub-Pitaka before deleting it."
                }
                require(CurrencyBalances.parse(pitaka.currencyBalances).values.all { kotlin.math.abs(it) < 1e-9 }) {
                    "Move this parent's direct balances to a sub-Pitaka before deleting it."
                }
                pitakaDao.detachChildren(pitaka.id)
            } else {
                val entries = ledgerDao.getEntriesForPitakaOnce(pitaka.id)
                entries.forEach { entry ->
                    reverseEffect(entry)
                    ledgerDao.deleteEntry(entry)
                }
                requireNonNegativeBalances(entries, excludedPitakaId = pitaka.id)
            }
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
            val pitaka = requireTransactionLeaf(pitakaId)
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
    fun observeAllGoals(): Flow<List<GoalWithProgress>> = goalDao.observeAllGoalsWithProgress()

    fun observeTotalProgressForType(type: GoalType): Flow<Double> = goalDao.observeTotalProgressForType(type)

    suspend fun getGoal(id: Long): Goal? = goalDao.getGoal(id)

    suspend fun createGoal(
        name: String,
        type: GoalType,
        targetAmount: Double,
        targetDate: Long?,
        colorHex: String?,
        cardStyle: String = "solid",
        currency: String = "PHP",
        targets: Map<String, Double> = mapOf(currency to targetAmount)
    ): Long {
        val code = currency.trim().uppercase().ifBlank { "PHP" }
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
        require(targetAmount > 0 && targetAmount.isFinite()) { "Goal target must be a positive finite number." }
        val normalizedTargets = targets.mapKeys { it.key.trim().uppercase() }
        require(normalizedTargets.isNotEmpty() && normalizedTargets.all { (targetCode, amount) ->
            targetCode.length == 3 && targetCode.all { it in 'A'..'Z' } && amount > 0 && amount.isFinite()
        }) { "Every Goal target must have a valid currency and positive amount." }
        require(goalDao.countByNormalizedName(name) == 0) { "A Goal with this name already exists." }
        return goalDao.insertGoal(
            Goal(
                name = name.trim().ifBlank { error("Goal name cannot be blank.") },
                type = type,
                targetAmount = targetAmount,
                currency = code,
                targetBalances = CurrencyBalances.encode(normalizedTargets),
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
        targetDate: Long?,
        colorHex: String?,
        cardStyle: String = "solid",
        currency: String? = null,
        targets: Map<String, Double>? = null
    ) {
        val existing = goalDao.getGoal(goalId) ?: return
        require(existing.archivedAt == null) { "Cannot edit an archived Goal." }
        val code = (currency?.trim()?.uppercase()?.ifBlank { null } ?: existing.currency.uppercase())
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
        require(targetAmount > 0 && targetAmount.isFinite()) { "Goal target must be a positive finite number." }
        require(goalDao.countByNormalizedName(name, goalId) == 0) { "A Goal with this name already exists." }
        val normalizedTargets = targets?.mapKeys { it.key.trim().uppercase() }
            ?: CurrencyBalances.parse(existing.targetBalances)
        require(normalizedTargets.isNotEmpty() && normalizedTargets.all { (targetCode, amount) ->
            targetCode.length == 3 && targetCode.all { it in 'A'..'Z' } && amount > 0 && amount.isFinite()
        }) { "Every Goal target must have a valid currency and positive amount." }
        val balances = CurrencyBalances.parse(existing.currencyBalances)
        goalDao.updateGoal(
            existing.copy(
                name = name.trim().ifBlank { error("Goal name cannot be blank.") },
                type = type,
                targetAmount = targetAmount,
                targetBalances = CurrencyBalances.encode(normalizedTargets),
                currency = code,
                targetDate = targetDate,
                colorHex = colorHex,
                cardStyle = cardStyle,
                currencyBalances = if (balances.isEmpty()) CurrencyBalances.encode(mapOf(code to 0.0)) else existing.currencyBalances
            )
        )
    }

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

    suspend fun deleteGoal(goal: Goal, refundPitakaId: Long? = null) {
        db.withTransaction {
            val entries = ledgerDao.getEntriesForGoalOnce(goal.id)
            val hasMissingContributionSource = entries.any { entry ->
                entry.type == LedgerType.GOAL_CONTRIBUTION &&
                    entry.pitakaId?.let { pitakaDao.getPitaka(it) } == null
            }
            val refundPitaka = if (hasMissingContributionSource) {
                requireNotNull(refundPitakaId) { "Select a Pitaka for contributions whose original source no longer exists." }
                requireTransactionLeaf(refundPitakaId, "Refund Pitaka")
            } else null
            entries.forEach { entry ->
                val reversibleEntry = if (
                    entry.type == LedgerType.GOAL_CONTRIBUTION &&
                    entry.pitakaId?.let { pitakaDao.getPitaka(it) } == null
                ) entry.copy(pitakaId = refundPitaka!!.id) else entry
                reverseEffect(reversibleEntry)
                ledgerDao.deleteEntry(entry)
            }
            requireNonNegativeBalances(entries)
            goalDao.deleteGoal(goal)
        }
    }

    // ---- Expense funnels ----
    fun observeExpenseFunnels(): Flow<List<ExpenseFunnel>> = funnelDao.observeAll()
    fun observeAllExpenseFunnels(): Flow<List<ExpenseFunnel>> = funnelDao.observeAllIncludingArchived()
    suspend fun getGeneralExpensesFunnel(): ExpenseFunnel {
        return funnelDao.getByName("General Expenses")
            ?: funnelDao.insertAndReturn(ExpenseFunnel(name = "General Expenses", limit = 0.0, currency = "PHP", currencyBalances = "PHP=0", isSystem = true)).let { funnelDao.get(it)!! }
    }
    fun observeFunnelSpent(funnelId: Long): Flow<Double> = funnelDao.observeSpent(funnelId)

    fun observeGoalProgressByCurrency(goalId: Long): Flow<Map<String, Double>> =
        ledgerDao.observeAllEntries().map { entries ->
            entries.asSequence()
                .filter { it.type in setOf(LedgerType.GOAL_CONTRIBUTION, LedgerType.GOAL_WITHDRAWAL, LedgerType.GOAL_EXPENSE) && it.goalId == goalId }
                .groupBy { (it.goalCurrency ?: it.currency).uppercase() }
                .mapValues { (_, rows) -> rows.sumOf {
                    val amount = it.goalAmount ?: it.amount
                    if (it.type == LedgerType.GOAL_CONTRIBUTION) amount else -amount
                } }
        }

    fun observeFunnelSpentByCurrency(funnelId: Long): Flow<Map<String, Double>> =
        ledgerDao.observeAllEntries().map { entries ->
            entries.asSequence()
                .filter { it.type in setOf(LedgerType.EXPENSE, LedgerType.GOAL_EXPENSE) && it.funnelId == funnelId }
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
        require(funnelDao.countByNormalizedName(name) == 0) { "An Expense Funnel with this name already exists." }
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
            require(existing.archivedAt == null) { "Cannot edit an archived Expense Funnel." }
            val name = funnel.name.trim()
            val code = funnel.currency.trim().uppercase()
            require(name.isNotBlank()) { "Funnel name cannot be blank." }
            require(funnelDao.countByNormalizedName(name, funnel.id) == 0) { "An Expense Funnel with this name already exists." }
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
        db.withTransaction {
            require(!funnel.isSystem) { "System expense funnels cannot be deleted." }
            val general = getGeneralExpensesFunnel()
            val reassignedBalances = ledgerDao.getExpensesForFunnelOnce(funnel.id).fold(
                CurrencyBalances.parse(general.currencyBalances) as Map<String, Double>
            ) { balances, entry ->
                CurrencyBalances.parse(CurrencyBalances.add(
                    CurrencyBalances.encode(balances),
                    entry.funnelCurrency ?: entry.currency,
                    entry.funnelAmount ?: entry.amount
                ))
            }
            funnelDao.update(general.copy(currencyBalances = CurrencyBalances.encode(reassignedBalances)))
            ledgerDao.reassignExpenseFunnel(funnel.id, general.id)
            funnelDao.delete(funnel)
        }
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
            requireNonNegativeBalances(listOf(entry))
            ledgerDao.deleteEntry(entry)
        }
    }

    suspend fun replaceEntry(oldEntry: LedgerEntry, replacement: LedgerEntry) {
        require(oldEntry.id == replacement.id) { "A transaction replacement must keep the original ID." }
        require(oldEntry.type == replacement.type) { "Transaction type cannot be changed. Delete and recreate the record instead." }
        require(oldEntry.type != LedgerType.OPENING_BALANCE) { "Opening balances are corrected with an adjustment." }
        require(replacement.name.trim().isNotBlank()) { "Transaction name cannot be blank." }
        require(replacement.amount.isFinite()) { "Transaction amount must be finite." }
        require(replacement.amount > 0 || replacement.type == LedgerType.ADJUSTMENT) { "Transaction amount must be positive." }

        db.withTransaction {
            reverseEffect(oldEntry)
            val normalized = replacement.copy(
                name = replacement.name.trim(),
                currency = replacement.currency.trim().uppercase(),
                secondaryCurrency = replacement.secondaryCurrency?.trim()?.uppercase(),
                funnelCurrency = replacement.funnelCurrency?.trim()?.uppercase(),
                goalCurrency = replacement.goalCurrency?.trim()?.uppercase(),
                category = if (replacement.type in setOf(LedgerType.EXPENSE, LedgerType.GOAL_EXPENSE)) {
                    canonicalExpenseCategory(replacement.category)
                } else replacement.category
            )
            require(normalized.currency.length == 3 && normalized.currency.all { it in 'A'..'Z' }) {
                "Transaction currency must be exactly 3 letters."
            }

            when (normalized.type) {
                LedgerType.INCOME -> requireTransactionLeaf(requireNotNull(normalized.pitakaId))
                LedgerType.EXPENSE -> {
                    val source = requireTransactionLeaf(requireNotNull(normalized.pitakaId), "Source Pitaka")
                    require((CurrencyBalances.parse(source.currencyBalances)[normalized.currency] ?: 0.0) >= normalized.amount) {
                        "Insufficient ${normalized.currency} balance in ${source.name}."
                    }
                    val funnel = funnelDao.get(requireNotNull(normalized.funnelId)) ?: error("Expense Funnel not found.")
                    require(funnel.archivedAt == null || funnel.isSystem) { "Cannot assign an expense to an archived Funnel." }
                    require(funnel.validFrom == null || normalized.date >= funnel.validFrom) { "Expense date is before the Funnel validity period." }
                    require(funnel.validUntil == null || normalized.date <= funnel.validUntil) { "Expense date is after the Funnel validity period." }
                    require(normalized.funnelAmount?.let { it > 0 && it.isFinite() } == true) { "Funnel amount must be positive." }
                }
                LedgerType.TRANSFER -> {
                    val source = requireTransactionLeaf(requireNotNull(normalized.fromPitakaId), "Source Pitaka")
                    requireTransactionLeaf(requireNotNull(normalized.toPitakaId), "Destination Pitaka")
                    require(normalized.fromPitakaId != normalized.toPitakaId) { "Transfer source and destination must differ." }
                    require((CurrencyBalances.parse(source.currencyBalances)[normalized.currency] ?: 0.0) >= normalized.amount) {
                        "Insufficient ${normalized.currency} balance in ${source.name}."
                    }
                    require(normalized.secondaryAmount?.let { it > 0 && it.isFinite() } != false) { "Destination amount must be positive." }
                }
                LedgerType.GOAL_CONTRIBUTION -> {
                    val source = requireTransactionLeaf(requireNotNull(normalized.pitakaId), "Source Pitaka")
                    require((CurrencyBalances.parse(source.currencyBalances)[normalized.currency] ?: 0.0) >= normalized.amount) {
                        "Insufficient ${normalized.currency} balance in ${source.name}."
                    }
                    val goal = goalDao.getGoal(requireNotNull(normalized.goalId)) ?: error("Goal not found.")
                    require(goal.archivedAt == null) { "Cannot contribute to an archived Goal." }
                    require(normalized.goalAmount?.let { it > 0 && it.isFinite() } == true) { "Goal amount must be positive." }
                }
                LedgerType.GOAL_WITHDRAWAL -> {
                    requireTransactionLeaf(requireNotNull(normalized.pitakaId), "Destination Pitaka")
                    val goal = goalDao.getGoal(requireNotNull(normalized.goalId)) ?: error("Goal not found.")
                    require(goal.archivedAt == null) { "Cannot withdraw from an archived Goal." }
                    val goalCode = normalized.goalCurrency ?: normalized.currency
                    val goalAmount = normalized.goalAmount ?: normalized.amount
                    require((CurrencyBalances.parse(goal.currencyBalances)[goalCode] ?: 0.0) >= goalAmount) {
                        "Insufficient $goalCode balance in ${goal.name}."
                    }
                }
                LedgerType.GOAL_EXPENSE -> {
                    val goal = goalDao.getGoal(requireNotNull(normalized.goalId)) ?: error("Goal not found.")
                    require(goal.archivedAt == null) { "Cannot spend from an archived Goal." }
                    val goalCode = normalized.goalCurrency ?: normalized.currency
                    val goalAmount = normalized.goalAmount ?: normalized.amount
                    require((CurrencyBalances.parse(goal.currencyBalances)[goalCode] ?: 0.0) >= goalAmount) {
                        "Insufficient $goalCode balance in ${goal.name}."
                    }
                    require(funnelDao.get(requireNotNull(normalized.funnelId)) != null) { "Expense Funnel not found." }
                }
                LedgerType.ADJUSTMENT -> {
                    val pitaka = requireTransactionLeaf(requireNotNull(normalized.pitakaId))
                    val available = CurrencyBalances.parse(pitaka.currencyBalances)[normalized.currency] ?: 0.0
                    require(available + normalized.amount >= 0.0) { "Adjustment would make the Pitaka balance negative." }
                }
                LedgerType.OPENING_BALANCE -> error("Opening balances cannot be edited.")
            }

            val primarySnapshot = historicalConversionSnapshot(normalized.currency, normalized.amount)
            val secondarySnapshot = normalized.secondaryAmount?.let { amount ->
                historicalConversionSnapshot(normalized.secondaryCurrency ?: normalized.currency, amount)
            }
            val withSnapshots = normalized.copy(
                conversionRateToBaseAtTransaction = primarySnapshot.first,
                amountInBaseAtTransaction = primarySnapshot.second,
                baseCurrencyAtTransaction = primarySnapshot.third,
                secondaryConversionRateToBaseAtTransaction = secondarySnapshot?.first,
                secondaryAmountInBaseAtTransaction = secondarySnapshot?.second
            )
            ledgerDao.updateEntry(withSnapshots)
            applyEffect(withSnapshots)
            requireNonNegativeBalances(listOf(oldEntry, withSnapshots))
        }
    }

    private suspend fun requireNonNegativeBalances(entries: List<LedgerEntry>, excludedPitakaId: Long? = null) {
        entries.flatMap { listOfNotNull(it.pitakaId, it.fromPitakaId, it.toPitakaId) }
            .distinct()
            .filter { it != excludedPitakaId }
            .forEach { pitakaId ->
                val pitaka = pitakaDao.getPitaka(pitakaId) ?: return@forEach
                require(CurrencyBalances.parse(pitaka.currencyBalances).values.all { it >= -1e-9 }) {
                    "This change would make ${pitaka.name} negative. Restore sufficient funds before continuing."
                }
            }
        entries.mapNotNull { it.goalId }.distinct().forEach { goalId ->
            val goal = goalDao.getGoal(goalId) ?: return@forEach
            require(CurrencyBalances.parse(goal.currencyBalances).values.all { it >= -1e-9 }) {
                "This change would make ${goal.name} negative. Reverse later withdrawals or expenses first."
            }
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

    // ---- Money-movement operations (all atomic) ----

    suspend fun recordIncome(pitakaId: Long, name: String, amount: Double, date: Long = System.currentTimeMillis(), currency: String? = null) {
        require(name.trim().isNotBlank()) { "Income name cannot be blank." }
        require(amount > 0 && amount.isFinite()) { "Income amount must be a positive finite number" }
        db.withTransaction {
            val pitaka = requireTransactionLeaf(pitakaId)
            val code = currency?.trim()?.uppercase()?.ifBlank { null } ?: pitaka.currency.uppercase()
            require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Income currency must be exactly 3 letters." }
            recordIncomeInternal(pitakaId, name, amount, date, code)
        }
    }

    private suspend fun recordIncomeInternal(pitakaId: Long, name: String, amount: Double, date: Long = System.currentTimeMillis(), currency: String? = null) {
        val pitaka = requireTransactionLeaf(pitakaId)
        val pitakaCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: pitaka.currency.uppercase()
        require(pitakaCurrency.length == 3 && pitakaCurrency.all { it in 'A'..'Z' }) {
            "Income currency must be exactly 3 letters."
        }
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
            val source = requireTransactionLeaf(pitakaId, "Source Pitaka")
            val txCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
            require((CurrencyBalances.parse(source.currencyBalances)[txCurrency] ?: 0.0) >= amount) { "Insufficient ${txCurrency} balance in ${source.name}." }
            val resolvedFunnel = funnelId ?: getGeneralExpensesFunnel().id
            val funnel = funnelDao.get(resolvedFunnel)
            require(funnel != null) { "Expense Funnel not found." }
            require(funnel.archivedAt == null || funnel.isSystem) {
                "Cannot record an expense against an archived Expense Funnel."
            }
            val normalizedFunnelCurrency = funnelCurrency?.trim()?.uppercase()?.ifBlank { null }
            val appliedFunnelCurrency = normalizedFunnelCurrency ?: txCurrency
            val appliedFunnelAmount = funnelAmount ?: amount
            if (!funnel.isSystem && !appliedFunnelCurrency.equals(funnel.currency, ignoreCase = true) && normalizedFunnelCurrency == null) {
                require(false) {
                    "A funnel currency must be selected explicitly when the expense currency differs from the funnel currency."
                }
            }
            if (!funnel.isSystem) {
                require(funnel.validFrom == null || date >= funnel.validFrom) {
                    "Expense date is before the funnel validity period."
                }
                require(funnel.validUntil == null || date <= funnel.validUntil) {
                    "Expense date is after the funnel validity period."
                }
            }
            recordExpenseInternal(pitakaId, name, amount, canonicalExpenseCategory(category), resolvedFunnel, txCurrency, appliedFunnelAmount, appliedFunnelCurrency, date)
        }
    }

    private suspend fun recordExpenseInternal(
        pitakaId: Long, name: String, amount: Double, category: String?, funnelId: Long? = null, currency: String? = null, funnelAmount: Double? = null, funnelCurrency: String? = null, date: Long = System.currentTimeMillis()
    ) {
        val source = requireTransactionLeaf(pitakaId, "Source Pitaka")
        val txCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
        require(txCurrency.length == 3 && txCurrency.all { it in 'A'..'Z' }) {
            "Expense currency must be exactly 3 letters."
        }
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
        date: Long = System.currentTimeMillis(),
        sourceCurrency: String? = null,
        destinationCurrency: String? = null
    ) {
        require(amount > 0 && amount.isFinite()) { "Transfer amount must be a positive finite number" }
        db.withTransaction {
            val source = requireTransactionLeaf(fromPitakaId, "Source Pitaka")
            val destination = requireTransactionLeaf(toPitakaId, "Destination Pitaka")
            require(fromPitakaId != toPitakaId) { "Source and destination must be different." }
            val sourceCode = sourceCurrency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
            val destinationCode = destinationCurrency?.trim()?.uppercase()?.ifBlank { null } ?: destination.currency.uppercase()
            require(sourceCode.length == 3 && sourceCode.all { it in 'A'..'Z' }) { "Source currency code must be exactly 3 letters." }
            require(destinationCode.length == 3 && destinationCode.all { it in 'A'..'Z' }) { "Destination currency code must be exactly 3 letters." }
            require((CurrencyBalances.parse(source.currencyBalances)[sourceCode] ?: 0.0) >= amount) { "Insufficient " + sourceCode + " balance in " + source.name + "." }
            require(name.trim().isNotBlank()) { "Transfer name cannot be blank." }
            if (sourceCode != destinationCode) {
                require(secondaryAmount != null && secondaryAmount > 0 && secondaryAmount.isFinite()) {
                    "A positive destination amount is required for a cross-currency transfer."
                }
                val baseCurrency = currencyDao.observeSettings().first()?.baseCurrency?.uppercase() ?: "PHP"
                val rates = currencyDao.getRatesOnce()
                fun hasUsableRate(code: String): Boolean =
                    code.equals(baseCurrency, true) ||
                        rates.any { it.code.equals(code, true) && it.rateToBase.isFinite() && it.rateToBase > 0.0 }
                require(hasUsableRate(sourceCode) && hasUsableRate(destinationCode)) {
                    "Usable exchange rates for " + sourceCode + " and " + destinationCode + " are required for a cross-currency transfer."
                }
            }
            val destinationAmount = if (destinationCode == sourceCode) amount else (secondaryAmount ?: amount)
            val sourceSnapshot = historicalConversionSnapshot(sourceCode, amount)
            val destinationSnapshot = historicalConversionSnapshot(destinationCode, destinationAmount)
            val entry = LedgerEntry(
                type = LedgerType.TRANSFER,
                amount = amount,
                currency = sourceCode,
                name = name,
                fromPitakaId = fromPitakaId,
                toPitakaId = toPitakaId,
                // Currency codes are normalized before comparison, so equivalent casing
                // cannot accidentally create a redundant secondary amount.
                secondaryAmount = if (destinationCode == sourceCode) null else destinationAmount,
                secondaryCurrency = destinationCode,
                date = date,
                conversionRateToBaseAtTransaction = sourceSnapshot.first,
                amountInBaseAtTransaction = sourceSnapshot.second,
                baseCurrencyAtTransaction = sourceSnapshot.third,
                secondaryConversionRateToBaseAtTransaction = destinationSnapshot.first,
                secondaryAmountInBaseAtTransaction = destinationSnapshot.second
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
            val source = requireTransactionLeaf(sourcePitakaId, "Source Pitaka")
            val goal = goalDao.getGoal(goalId) ?: error("Goal not found.")
            require(goal.archivedAt == null) { "Cannot contribute to an archived Goal." }
            val txCurrency = currency?.trim()?.uppercase()?.ifBlank { null } ?: source.currency.uppercase()
            require((CurrencyBalances.parse(source.currencyBalances)[txCurrency] ?: 0.0) >= amount) { "Insufficient ${txCurrency} balance in ${source.name}." }
            val normalizedGoalCurrency = goalCurrency?.trim()?.uppercase()?.ifBlank { null }
            val appliedGoalCurrency = normalizedGoalCurrency ?: txCurrency
            val appliedGoalAmount = goalAmount ?: amount
            require(appliedGoalCurrency.length == 3 && appliedGoalCurrency.all { it in 'A'..'Z' }) { "Goal currency must be exactly 3 letters." }
            require(appliedGoalAmount > 0 && appliedGoalAmount.isFinite()) { "Goal allocation must be a positive finite number." }
            if (!appliedGoalCurrency.equals(goal.currency, ignoreCase = true) && normalizedGoalCurrency == null) {
                require(false) {
                    "A goal currency must be selected explicitly when the contribution currency differs from the Goal currency."
                }
            }
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

    suspend fun withdrawFromGoal(
        goalId: Long,
        destinationPitakaId: Long,
        name: String,
        goalAmount: Double,
        goalCurrency: String,
        destinationAmount: Double = goalAmount,
        destinationCurrency: String = goalCurrency,
        date: Long = System.currentTimeMillis()
    ) {
        require(name.trim().isNotBlank()) { "Withdrawal name cannot be blank." }
        require(goalAmount > 0 && goalAmount.isFinite()) { "Withdrawal amount must be positive." }
        require(destinationAmount > 0 && destinationAmount.isFinite()) { "Destination amount must be positive." }
        db.withTransaction {
            val goal = goalDao.getGoal(goalId) ?: error("Goal not found.")
            require(goal.archivedAt == null) { "Cannot withdraw from an archived Goal." }
            val goalCode = goalCurrency.trim().uppercase()
            val destinationCode = destinationCurrency.trim().uppercase()
            require(goalCode.length == 3 && goalCode.all { it in 'A'..'Z' }) { "Goal currency must be exactly 3 letters." }
            require(destinationCode.length == 3 && destinationCode.all { it in 'A'..'Z' }) { "Destination currency must be exactly 3 letters." }
            val available = CurrencyBalances.parse(goal.currencyBalances)[goalCode] ?: 0.0
            require(available >= goalAmount) { "Insufficient $goalCode balance in ${goal.name}." }
            requireTransactionLeaf(destinationPitakaId, "Destination Pitaka")
            val goalSnapshot = historicalConversionSnapshot(goalCode, goalAmount)
            val destinationSnapshot = historicalConversionSnapshot(destinationCode, destinationAmount)
            val entry = LedgerEntry(
                type = LedgerType.GOAL_WITHDRAWAL,
                amount = goalAmount,
                currency = goalCode,
                name = name.trim(),
                pitakaId = destinationPitakaId,
                goalId = goalId,
                goalAmount = goalAmount,
                goalCurrency = goalCode,
                secondaryAmount = destinationAmount,
                secondaryCurrency = destinationCode,
                date = date,
                conversionRateToBaseAtTransaction = goalSnapshot.first,
                amountInBaseAtTransaction = goalSnapshot.second,
                baseCurrencyAtTransaction = goalSnapshot.third,
                secondaryConversionRateToBaseAtTransaction = destinationSnapshot.first,
                secondaryAmountInBaseAtTransaction = destinationSnapshot.second
            )
            ledgerDao.insertEntry(entry)
            applyEffect(entry)
        }
    }

    suspend fun spendFromGoal(
        goalId: Long,
        name: String,
        amount: Double,
        currency: String,
        category: String?,
        date: Long = System.currentTimeMillis()
    ) {
        require(name.trim().isNotBlank()) { "Expense name cannot be blank." }
        require(amount > 0 && amount.isFinite()) { "Expense amount must be positive." }
        db.withTransaction {
            val goal = goalDao.getGoal(goalId) ?: error("Goal not found.")
            require(goal.archivedAt == null) { "Cannot spend from an archived Goal." }
            val code = currency.trim().uppercase()
            val available = CurrencyBalances.parse(goal.currencyBalances)[code] ?: 0.0
            require(available >= amount) { "Insufficient $code balance in ${goal.name}." }
            val general = getGeneralExpensesFunnel()
            val snapshot = historicalConversionSnapshot(code, amount)
            val entry = LedgerEntry(
                type = LedgerType.GOAL_EXPENSE,
                amount = amount,
                currency = code,
                name = name.trim(),
                category = canonicalExpenseCategory(category),
                goalId = goalId,
                goalAmount = amount,
                goalCurrency = code,
                funnelId = general.id,
                funnelAmount = amount,
                funnelCurrency = code,
                date = date,
                conversionRateToBaseAtTransaction = snapshot.first,
                amountInBaseAtTransaction = snapshot.second,
                baseCurrencyAtTransaction = snapshot.third
            )
            ledgerDao.insertEntry(entry)
            applyEffect(entry)
            val updated = goalDao.getGoal(goalId) ?: return@withTransaction
            if (CurrencyBalances.parse(updated.currencyBalances).values.all { kotlin.math.abs(it) < 1e-9 }) {
                goalDao.updateGoal(updated.copy(archivedAt = System.currentTimeMillis()))
            }
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

    private suspend fun canonicalExpenseCategory(category: String?): String {
        val cleaned = category?.trim().orEmpty()
        if (cleaned.isEmpty()) return "Uncategorized"
        return ledgerDao.findCanonicalExpenseCategory(cleaned) ?: cleaned
    }

    private suspend fun applyEffect(entry: LedgerEntry) {
        require(entry.amount.isFinite()) { "Ledger amount must be finite." }

        when (entry.type) {
            LedgerType.OPENING_BALANCE, LedgerType.INCOME -> {
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
            LedgerType.GOAL_WITHDRAWAL -> {
                val goalId = requireNotNull(entry.goalId) { "Goal withdrawal has no Goal." }
                val goal = requireNotNull(goalDao.getGoal(goalId)) { "Goal for withdrawal not found." }
                val goalCurrency = entry.goalCurrency ?: entry.currency
                val goalAmount = entry.goalAmount ?: entry.amount
                goalDao.updateGoal(goal.copy(
                    currencyBalances = CurrencyBalances.add(goal.currencyBalances, goalCurrency, -goalAmount)
                ))

                val destinationId = requireNotNull(entry.pitakaId) { "Goal withdrawal has no destination Pitaka." }
                require(pitakaDao.getPitaka(destinationId) != null) { "Destination Pitaka for withdrawal not found." }
                adjustBalance(
                    destinationId,
                    entry.secondaryAmount ?: entry.amount,
                    entry.secondaryCurrency ?: entry.currency
                )
            }
            LedgerType.GOAL_EXPENSE -> {
                val goalId = requireNotNull(entry.goalId) { "Goal expense has no Goal." }
                val goal = requireNotNull(goalDao.getGoal(goalId)) { "Goal for expense not found." }
                val goalCurrency = entry.goalCurrency ?: entry.currency
                val goalAmount = entry.goalAmount ?: entry.amount
                goalDao.updateGoal(goal.copy(
                    currencyBalances = CurrencyBalances.add(goal.currencyBalances, goalCurrency, -goalAmount)
                ))

                val funnelId = requireNotNull(entry.funnelId) { "Goal expense has no Expense Funnel." }
                val funnel = requireNotNull(funnelDao.get(funnelId)) { "Expense Funnel for goal expense not found." }
                funnelDao.update(funnel.copy(
                    currencyBalances = CurrencyBalances.add(
                        funnel.currencyBalances,
                        entry.funnelCurrency ?: entry.currency,
                        entry.funnelAmount ?: entry.amount
                    )
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
