package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.LedgerEntry
import com.pitaka.app.data.LedgerType
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.AdjustBalanceDialog
import com.pitaka.app.ui.components.ConfirmDeleteDialog
import com.pitaka.app.ui.components.EditEntryDialog
import com.pitaka.app.ui.components.dateFormat
import com.pitaka.app.ui.theme.parseHexColor
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitakaDetailScreen(viewModel: PitakaViewModel, pitakaId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    var pitaka by remember { mutableStateOf<Pitaka?>(null) }
    val entries by viewModel.entriesForPitaka(pitakaId).collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    LaunchedEffect(pitakaId, entries) {
        pitaka = viewModel.getPitaka(pitakaId)
    }

    val accentColor = parseHexColor(pitaka?.colorHex) ?: Color(0xFF0278CF)
    val currency = pitaka?.currency ?: "USD"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pitaka?.name ?: "") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    IconButton(onClick = { showAdjustDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Adjust Balance")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Pitaka")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Pitaka")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            pitaka?.let { p ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(16.dp)
                ) {
                    Text(
                        "${p.currency} ${"%,.2f".format(p.currentAmount)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Last updated: ${dateFormat.format(Date(p.lastUpdated))}", color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Record Income", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Source (e.g. September Salary)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount ($currency)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (name.isNotBlank() && amount != null && amount > 0) {
                        viewModel.recordIncome(pitakaId, name, amount)
                        name = ""
                        amountText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Add Income")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("History", fontWeight = FontWeight.Bold)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(entries, key = { it.id }) { entry ->
                    LedgerRow(
                        entry = entry,
                        pitakaId = pitakaId,
                        currency = currency,
                        onEdit = { editingEntry = entry },
                        onDelete = { viewModel.deleteEntry(entry) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete this Pitaka?",
            message = "This permanently removes \"${pitaka?.name}\" and every income, expense, " +
                "transfer, and contribution logged against it. This can't be undone.",
            onConfirm = {
                pitaka?.let { viewModel.deletePitaka(it) }
                showDeleteConfirm = false
                onBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showAdjustDialog) {
        AdjustBalanceDialog(
            currentBalance = pitaka?.currentAmount ?: 0.0,
            onConfirm = { newBalance ->
                viewModel.adjustPitakaBalanceManually(pitakaId, newBalance, "Manual adjustment")
                showAdjustDialog = false
            },
            onDismiss = { showAdjustDialog = false }
        )
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onSave = { newName, newAmount, newCategory ->
                viewModel.updateEntry(entry, newName, newAmount, newCategory)
                editingEntry = null
            },
            onDismiss = { editingEntry = null }
        )
    }
}

@Composable
private fun LedgerRow(
    entry: LedgerEntry,
    pitakaId: Long,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (label, signedAmount, color) = describeEntry(entry, pitakaId, currency)
    val editable = entry.type != LedgerType.TRANSFER
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(entry.name, fontWeight = FontWeight.Medium)
            Text(
                "$label  •  ${dateFormat.format(Date(entry.date))}",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(signedAmount, color = color, fontWeight = FontWeight.SemiBold)
            if (editable) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun describeEntry(entry: LedgerEntry, pitakaId: Long, currency: String): Triple<String, String, Color> {
    fun fmt(amount: Double) = "$currency ${"%,.2f".format(amount)}"
    return when (entry.type) {
        LedgerType.INCOME -> Triple("Income", "+${fmt(entry.amount)}", Color(0xFF1E8E5A))
        LedgerType.EXPENSE -> Triple(entry.category ?: "Expense", "-${fmt(entry.amount)}", Color(0xFFC62800))
        LedgerType.GOAL_CONTRIBUTION -> Triple("Goal contribution", "-${fmt(entry.amount)}", Color(0xFFC62800))
        LedgerType.ADJUSTMENT -> {
            val positive = entry.amount >= 0
            Triple("Manual adjustment", "${if (positive) "+" else ""}${fmt(entry.amount)}", if (positive) Color(0xFF1E8E5A) else Color(0xFFC62800))
        }
        LedgerType.TRANSFER -> {
            if (entry.fromPitakaId == pitakaId) {
                Triple("Transfer out", "-${fmt(entry.amount)}", Color(0xFFC62800))
            } else {
                Triple("Transfer in", "+${fmt(entry.secondaryAmount ?: entry.amount)}", Color(0xFF1E8E5A))
            }
        }
    }
}
