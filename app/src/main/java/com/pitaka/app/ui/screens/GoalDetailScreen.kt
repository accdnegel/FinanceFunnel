package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.CurrencyBalances
import com.pitaka.app.data.Goal
import com.pitaka.app.data.GoalType
import com.pitaka.app.data.CurrencyRules
import com.pitaka.app.data.displayLines
import com.pitaka.app.data.LedgerEntry
import com.pitaka.app.data.LedgerType
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.PitakaDropdown
import com.pitaka.app.ui.components.CurrencyDropdown
import com.pitaka.app.ui.components.ConfirmDeleteDialog
import com.pitaka.app.ui.components.EditEntryDialog
import com.pitaka.app.ui.components.HealthBar
import com.pitaka.app.ui.components.dateFormat
import com.pitaka.app.ui.theme.healthColor
import com.pitaka.app.ui.theme.parseHexColor
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(viewModel: PitakaViewModel, goalId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    var goal by remember { mutableStateOf<Goal?>(null) }
    val entries by viewModel.entriesForGoal(goalId).collectAsState(initial = emptyList())
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    val allPitakas by viewModel.allPitakasIncludingArchived.collectAsState(initial = emptyList())
    val goals by viewModel.goals.collectAsState(initial = emptyList())
    val funnels by viewModel.expenseFunnels.collectAsState(initial = emptyList())
    val rates by viewModel.exchangeRates.collectAsState(initial = emptyList())
    val leafPitakas = pitakas.filter { candidate -> pitakas.none { it.parentPitakaId == candidate.id } }

    var sourcePitaka by remember { mutableStateOf<Pitaka?>(null) }
    var note by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var maskedBalance by remember { mutableStateOf(false) }
    var currencyMismatch by remember { mutableStateOf(false) }
    var pendingAmount by remember { mutableStateOf(0.0) }
    var pendingSource by remember { mutableStateOf<Pitaka?>(null) }
    var pendingConverted by remember { mutableStateOf<Double?>(null) }
    var selectedGoalCurrency by remember { mutableStateOf("PHP") }
    var selectedSourceCurrency by remember { mutableStateOf("PHP") }
    var goalCurrencyMenuOpen by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showSpend by remember { mutableStateOf(false) }
    var actionAmount by remember { mutableStateOf("") }
    var actionName by remember { mutableStateOf("") }
    var actionCategory by remember { mutableStateOf("") }
    var destinationAmount by remember { mutableStateOf("") }
    var refundPitaka by remember { mutableStateOf<Pitaka?>(null) }

    LaunchedEffect(goalId) { goal = viewModel.getGoal(goalId) }
    LaunchedEffect(leafPitakas) { if (sourcePitaka == null && leafPitakas.isNotEmpty()) { sourcePitaka = leafPitakas.first(); selectedSourceCurrency = leafPitakas.first().currency } }
    LaunchedEffect(leafPitakas) { if (refundPitaka == null) refundPitaka = leafPitakas.firstOrNull() }
    LaunchedEffect(goal) {
        selectedGoalCurrency = goal?.let { CurrencyBalances.parse(it.targetBalances).keys.firstOrNull() ?: it.currency } ?: "PHP"
    }

    val progressBalances = CurrencyBalances.parse(goal?.currencyBalances)
    val targetBalances = CurrencyBalances.parse(goal?.targetBalances)
    val isInvestment = goal?.type == GoalType.INVESTMENT
    val accentColor = parseHexColor(goal?.colorHex) ?: if (isInvestment) Color(0xFF056C3F) else Color(0xFF0278CF)
    val completionRatios = targetBalances.map { (code, target) -> (progressBalances[code] ?: 0.0) / target.coerceAtLeast(0.000001) }
    val ratio = (completionRatios.minOrNull() ?: 0.0).toFloat().coerceIn(0f, 1f)
    val completed = targetBalances.isNotEmpty() && completionRatios.all { it >= 1.0 }
    val missingContributionSource = entries.any { entry ->
        entry.type == LedgerType.GOAL_CONTRIBUTION && allPitakas.none { it.id == entry.pitakaId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(goal?.name ?: "") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit Goal") }
                    if (goal?.archivedAt == null) IconButton(onClick = { viewModel.archiveGoal(goalId, onBack) }) { Icon(Icons.Default.Archive, contentDescription = "Archive Goal") }
                    else IconButton(onClick = { viewModel.restoreGoal(goalId) }) { Icon(Icons.Default.Unarchive, contentDescription = "Restore Goal") }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete Goal") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            goal?.let { g ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(16.dp)
                ) {
                    Text(
                        if (maskedBalance) "••••••" else CurrencyBalances.parse(g.currencyBalances).displayLines().ifBlank { "${g.currency} 0.00" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Targets: ${CurrencyBalances.parse(g.targetBalances).displayLines()}")
                    Text(g.targetDate?.let { "Target date: ${dateFormat.format(Date(it))}" } ?: "No target date", color = Color.Gray)
                    if (completed) Text("Completed", color = accentColor, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    HealthBar(ratio = ratio, color = healthColor(ratio))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Contribute", fontWeight = FontWeight.Bold)
            if (leafPitakas.isEmpty()) {
                Text("Create a Pitaka first so you have somewhere to contribute from.", color = Color.Gray)
            } else {
                PitakaDropdown(
                    label = "From Pitaka",
                    pitakas = leafPitakas,
                    selected = sourcePitaka,
                    onSelected = { sourcePitaka = it; selectedSourceCurrency = it.currency }
                )
                CurrencyDropdown(selectedSourceCurrency, onSelected = { selectedSourceCurrency = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = goalCurrencyMenuOpen, onExpandedChange = { goalCurrencyMenuOpen = !goalCurrencyMenuOpen }) {
                    OutlinedTextField(
                        value = selectedGoalCurrency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Goal target currency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(goalCurrencyMenuOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = goalCurrencyMenuOpen, onDismissRequest = { goalCurrencyMenuOpen = false }) {
                        targetBalances.keys.forEach { code ->
                            DropdownMenuItem(text = { Text(code) }, onClick = { selectedGoalCurrency = code; goalCurrencyMenuOpen = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        val src = sourcePitaka
                        when {
                            src == null -> error = "Pick a Pitaka."
                            amount == null || amount <= 0 -> error = "Enter a valid amount."
                            amount > (CurrencyBalances.parse(src.currencyBalances)[selectedSourceCurrency] ?: 0.0) -> error = "${src.name} has insufficient $selectedSourceCurrency balance."
                            else -> {
                                error = null
                                val goalCurrency = selectedGoalCurrency
                                if (selectedSourceCurrency.equals(goalCurrency, true)) {
                                    viewModel.recordGoalContribution(src.id, goalId, note.ifBlank { "Contribution" }, amount, selectedSourceCurrency, amount, goalCurrency)
                                    note = ""; amountText = ""
                                } else {
                                    try {
                                        pendingConverted = CurrencyRules.convert(amount, selectedSourceCurrency, goalCurrency, rates)
                                        pendingAmount = amount; pendingSource = src; currencyMismatch = true
                                    } catch (e: IllegalArgumentException) {
                                        error = e.message ?: "Configure exchange rates before converting."
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Contribute")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actionAmount = ""; actionName = ""; destinationAmount = ""; showWithdraw = true }, modifier = Modifier.weight(1f)) {
                        Text("Withdraw")
                    }
                    OutlinedButton(onClick = { actionAmount = ""; actionName = ""; actionCategory = ""; showSpend = true }, modifier = Modifier.weight(1f)) {
                        Text("Mark spent")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Contribution History", fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(entries, key = { it.id }) { entry ->
                    var masked by remember(entry.id) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(entry.name, fontWeight = FontWeight.Medium)
                            Text(dateFormat.format(Date(entry.date)), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (masked) "••••••" else "${entry.currency} ${"%,.2f".format(entry.amount)}")
                            IconButton(onClick = { editingEntry = entry }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteEntry(entry) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (currencyMismatch && pendingSource != null) {
        val src = pendingSource!!
        val goalCurrency = goal?.currency ?: "PHP"
        AlertDialog(
            onDismissRequest = { currencyMismatch = false },
            title = { Text("Currency mismatch") },
            text = { Text("$selectedSourceCurrency ${"%,.2f".format(pendingAmount)} is different from this Goal's $goalCurrency target. Confirm the converted amount.") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.recordGoalContribution(src.id, goalId, note.ifBlank { "Contribution" }, pendingAmount, selectedSourceCurrency, pendingConverted, goalCurrency)
                        note = ""; amountText = ""; currencyMismatch = false
                    }) { Text("Convert") }
                }
            },
            dismissButton = { TextButton(onClick = { currencyMismatch = false }) { Text("Cancel") } }
        )
    }

    if (showWithdraw) {
        val destination = sourcePitaka
        AlertDialog(
            onDismissRequest = { showWithdraw = false },
            title = { Text("Withdraw from Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PitakaDropdown("Destination Pitaka", leafPitakas, destination) { sourcePitaka = it }
                    OutlinedTextField(actionName, { actionName = it }, label = { Text("Name") })
                    OutlinedTextField(actionAmount, { actionAmount = it }, label = { Text("Amount ($selectedGoalCurrency)") })
                    if (destination != null && !destination.currency.equals(selectedGoalCurrency, true)) {
                        OutlinedTextField(destinationAmount, { destinationAmount = it }, label = { Text("Destination amount (${destination.currency})") })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = actionAmount.toDoubleOrNull()
                    val destinationValue = if (destination?.currency.equals(selectedGoalCurrency, true)) amount else destinationAmount.toDoubleOrNull()
                    if (destination != null && amount != null && destinationValue != null) {
                        viewModel.withdrawFromGoal(goalId, destination.id, actionName.ifBlank { "Goal withdrawal" }, amount, selectedGoalCurrency, destinationValue, destination.currency) { showWithdraw = false }
                    }
                }) { Text("Withdraw") }
            },
            dismissButton = { TextButton(onClick = { showWithdraw = false }) { Text("Cancel") } }
        )
    }

    if (showSpend) {
        AlertDialog(
            onDismissRequest = { showSpend = false },
            title = { Text("Mark Goal funds as spent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This reduces non-liquid assets and records an expense in General Expenses.")
                    OutlinedTextField(actionName, { actionName = it }, label = { Text("Expense name") })
                    OutlinedTextField(actionAmount, { actionAmount = it }, label = { Text("Amount ($selectedGoalCurrency)") })
                    OutlinedTextField(actionCategory, { actionCategory = it }, label = { Text("Category") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    actionAmount.toDoubleOrNull()?.let { amount ->
                        viewModel.spendFromGoal(goalId, actionName.ifBlank { "Goal expense" }, amount, selectedGoalCurrency, actionCategory) { showSpend = false }
                    }
                }) { Text("Record expense") }
            },
            dismissButton = { TextButton(onClick = { showSpend = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete this Goal?",
            message = if (missingContributionSource) "This permanently removes \"${goal?.name}\". Select where contributions from missing source Pitakas should be returned." else "This permanently removes \"${goal?.name}\", reverses its withdrawals and expenses, and returns contributions to their original Pitakas. Export a backup first if needed.",
            onConfirm = {
                goal?.let { viewModel.deleteGoal(it, refundPitaka?.id, onBack) }
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
            confirmEnabled = !missingContributionSource || refundPitaka != null,
            additionalContent = if (missingContributionSource) {{
                Spacer(Modifier.height(12.dp))
                PitakaDropdown(leafPitakas, refundPitaka, { refundPitaka = it }, "Refund destination")
            }} else null
        )
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            pitakas = leafPitakas,
            goals = goals,
            funnels = funnels,
            onSave = { replacement ->
                viewModel.replaceEntry(entry, replacement)
                editingEntry = null
            },
            onDismiss = { editingEntry = null }
        )
    }
}
