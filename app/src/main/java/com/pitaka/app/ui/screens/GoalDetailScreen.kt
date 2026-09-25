package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.pitaka.app.data.displayLines
import com.pitaka.app.data.LedgerEntry
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
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
    val rates by viewModel.exchangeRates.collectAsState(initial = emptyList())
    val currencySettings by viewModel.currencySettings.collectAsState(initial = null)

    var sourcePitaka by remember { mutableStateOf<Pitaka?>(null) }
    var note by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var pendingContribution by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(goalId) { goal = viewModel.getGoal(goalId) }
    LaunchedEffect(pitakas) { if (sourcePitaka == null && pitakas.isNotEmpty()) sourcePitaka = pitakas.first() }

    val progress = entries.filter { it.goalCurrency?.equals(goal?.currency, ignoreCase = true) ?: it.currency.equals(goal?.currency, ignoreCase = true) }.sumOf { it.goalAmount ?: it.amount }
    val isInvestment = goal?.type == GoalType.INVESTMENT
    val accentColor = parseHexColor(goal?.colorHex) ?: if (isInvestment) Color(0xFF056C3F) else Color(0xFF0278CF)
    val target = (goal?.targetAmount ?: 0.0).coerceAtLeast(0.01)
    val ratio = (progress / target).toFloat().coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(goal?.name ?: "") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit Goal") }
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
                        CurrencyBalances.parse(g.currencyBalances).displayLines().ifBlank { "${g.currency} 0.00" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Target: ${dateFormat.format(Date(g.targetDate))}", color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    HealthBar(ratio = ratio, color = healthColor(ratio))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Contribute", fontWeight = FontWeight.Bold)
            if (pitakas.isEmpty()) {
                Text("Create a Pitaka first so you have somewhere to contribute from.", color = Color.Gray)
            } else {
                PitakaDropdown(
                    label = "From Pitaka",
                    pitakas = pitakas,
                    selected = sourcePitaka,
                    onSelected = { sourcePitaka = it }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                            amount > (CurrencyBalances.parse(src.currencyBalances)[src.currency.uppercase()] ?: 0.0) -> error = "${src.name} only has ${src.currency} ${"%,.2f".format(CurrencyBalances.parse(src.currencyBalances)[src.currency.uppercase()] ?: 0.0)}."
                            else -> {
                                error = null
                                if (goal != null && !src.currency.equals(goal!!.currency, ignoreCase = true)) {
                                    pendingContribution = amount
                                } else {
                                    viewModel.recordGoalContribution(
                                        sourcePitakaId = src.id,
                                        goalId = goalId,
                                        name = note.ifBlank { "Contribution" },
                                        amount = amount,
                                        currency = src.currency,
                                        onSuccess = { note = ""; amountText = "" }
                                    )
                                    note = ""
                                    amountText = ""
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Contribute")
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

    pendingContribution?.let { sourceAmount ->
        val src = sourcePitaka
        val g = goal
        if (src != null && g != null) {
            val base = currencySettings?.baseCurrency ?: "PHP"
            val fromRate = if (src.currency.equals(base, true)) 1.0 else rates.find { it.code.equals(src.currency, true) }?.rateToBase
            val toRate = if (g.currency.equals(base, true)) 1.0 else rates.find { it.code.equals(g.currency, true) }?.rateToBase
            val converted = if (fromRate != null && toRate != null && fromRate > 0 && toRate > 0) sourceAmount * fromRate / toRate else null
            AlertDialog(
                onDismissRequest = { pendingContribution = null },
                title = { Text("Different currencies") },
                text = { Text("This Pitaka uses ${src.currency}, while this Goal uses ${g.currency}. Choose how to record the contribution.") },
                confirmButton = {
                    TextButton(onClick = {
                        if (converted == null) {
                            error = "A usable exchange rate is required to convert ${src.currency} to ${g.currency}."
                        } else {
                            viewModel.recordGoalContribution(src.id, g.id, note.ifBlank { "Contribution" }, sourceAmount, src.currency, converted, g.currency, onSuccess = { note = ""; amountText = ""; pendingContribution = null })
                        }
                    }) { Text("Convert to ${g.currency}") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.recordGoalContribution(src.id, g.id, note.ifBlank { "Contribution" }, sourceAmount, src.currency, sourceAmount, src.currency, onSuccess = { note = ""; amountText = ""; pendingContribution = null })
                        }) { Text("Keep ${src.currency}") }
                        TextButton(onClick = { pendingContribution = null }) { Text("Cancel") }
                    }
                }
            )
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete this Goal?",
            message = "This removes \"${goal?.name}\" from your Goals. Any money already " +
                "contributed to it stays deducted from the Pitakas it came from — only the " +
                "progress tracking for this goal goes away.",
            onConfirm = {
                goal?.let { goalToDelete ->
                    viewModel.deleteGoal(goalToDelete, onSuccess = {
                        showDeleteConfirm = false
                        onBack()
                    })
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onSave = { newName, newAmount, _ ->
                viewModel.updateEntry(entry, newName, newAmount, null, onSuccess = {
                    editingEntry = null
                })
            },
            onDismiss = { editingEntry = null }
        )
    }
}
