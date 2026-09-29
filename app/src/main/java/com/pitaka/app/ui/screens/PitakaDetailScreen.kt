package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitaka.app.data.LedgerEntry
import com.pitaka.app.data.LedgerType
import com.pitaka.app.data.Pitaka
import com.pitaka.app.data.CurrencyBalances
import com.pitaka.app.data.displayLines
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.AdjustBalanceDialog
import com.pitaka.app.ui.components.AdaptiveText
import com.pitaka.app.ui.components.ConfirmDeleteDialog
import com.pitaka.app.ui.components.EditEntryDialog
import com.pitaka.app.ui.components.dateFormat
import com.pitaka.app.ui.theme.parseHexColor
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitakaDetailScreen(viewModel: PitakaViewModel, pitakaId: Long, onBack: () -> Unit, onEdit: () -> Unit, onOpenChild: (Long) -> Unit) {
    var pitaka by remember { mutableStateOf<Pitaka?>(null) }
    val allEntries by viewModel.allEntries.collectAsState(initial = emptyList())
    val activePitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    val allPitakas by viewModel.allPitakasIncludingArchived.collectAsState(initial = emptyList())
    val allGoals by viewModel.goals.collectAsState(initial = emptyList())
    val allFunnels by viewModel.expenseFunnels.collectAsState(initial = emptyList())
    val leafPitakas = activePitakas.filter { candidate -> activePitakas.none { it.parentPitakaId == candidate.id } }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val descendantIds = remember(allPitakas, pitakaId) {
        val found = mutableSetOf<Long>()
        fun collect(id: Long) {
            allPitakas.filter { it.parentPitakaId == id && found.add(it.id) }.forEach { collect(it.id) }
        }
        collect(pitakaId)
        found + pitakaId
    }
    val childPitakas = allPitakas.filter { it.parentPitakaId == pitakaId }
    val incomeTargets = allPitakas.filter { candidate ->
        candidate.id in descendantIds && candidate.id != pitakaId &&
            allPitakas.none { it.parentPitakaId == candidate.id }
    }
    val entries = allEntries.filter { e ->
        val ids = listOfNotNull(e.pitakaId, e.fromPitakaId, e.toPitakaId).toSet()
        (selectedIds.ifEmpty { descendantIds }).any { it in ids }
    }

    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var incomeTargetId by remember(pitakaId) { mutableLongStateOf(pitakaId) }
    var incomeTargetOpen by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    LaunchedEffect(pitakaId, entries) {
        pitaka = viewModel.getPitaka(pitakaId)
    }
    LaunchedEffect(incomeTargets) {
        if (incomeTargets.isNotEmpty() && incomeTargets.none { it.id == incomeTargetId }) {
            incomeTargetId = incomeTargets.first().id
        }
    }

    val accentColor = parseHexColor(pitaka?.colorHex) ?: Color(0xFF0278CF)
    val incomeTarget = allPitakas.find { it.id == incomeTargetId } ?: pitaka
    val currency = incomeTarget?.currency ?: pitaka?.currency ?: "PHP"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AdaptiveText(pitaka?.name ?: "", style = MaterialTheme.typography.titleLarge, minFontSize = 12.sp) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    IconButton(onClick = { showAdjustDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Adjust Balance")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Pitaka")
                    }
                    if (pitaka?.archivedAt == null) IconButton(onClick = { viewModel.archivePitaka(pitakaId, onBack) }) {
                        Icon(Icons.Default.Archive, contentDescription = "Archive Pitaka")
                    } else IconButton(onClick = { viewModel.restorePitaka(pitakaId) }) {
                        Icon(Icons.Default.Unarchive, contentDescription = "Restore Pitaka")
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
                    AdaptiveText(
                        viewModel.effectivePitakaBalances(p.id, allPitakas).displayLines(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text("Last updated: ${dateFormat.format(Date(p.lastUpdated))}", color = Color.Gray)
                }
            }

            if (childPitakas.isNotEmpty()) {
                Text("Sub-Pitakas", fontWeight = FontWeight.Bold)
                Text(
                    "Open a sub-Pitaka to view its own balance and history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    childPitakas.forEach { child ->
                        val childColor = parseHexColor(child.colorHex) ?: accentColor
                        ListItem(
                            headlineContent = { AdaptiveText(child.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                            supportingContent = { AdaptiveText(viewModel.effectivePitakaBalances(child.id, allPitakas).displayLines(), style = MaterialTheme.typography.bodyMedium, maxLines = 2) },
                            leadingContent = { Box(Modifier.width(6.dp).height(42.dp).background(childColor)) },
                            trailingContent = { Text("Open") },
                            modifier = Modifier.fillMaxWidth().clickable { onOpenChild(child.id) }
                        )
                        HorizontalDivider()
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Record Income", fontWeight = FontWeight.Bold)
            if (incomeTargets.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = incomeTargetOpen, onExpandedChange = { incomeTargetOpen = !incomeTargetOpen }) {
                    OutlinedTextField(
                        value = incomeTarget?.name ?: "Select sub-Pitaka",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Income destination") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(incomeTargetOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = incomeTargetOpen, onDismissRequest = { incomeTargetOpen = false }) {
                        incomeTargets.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.name) },
                                onClick = { incomeTargetId = target.id; incomeTargetOpen = false }
                            )
                        }
                    }
                }
            }
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
                    when {
                        name.isBlank() -> validationError = "Enter an income source."
                        amount == null || amount <= 0 -> validationError = "Enter a valid amount greater than zero."
                        incomeTarget == null -> validationError = "Select a destination Pitaka."
                        else -> {
                            validationError = null
                            viewModel.recordIncome(incomeTarget.id, name, amount)
                            name = ""
                            amountText = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Add Income")
            }
            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(modifier = Modifier.height(16.dp))
            Text("History", fontWeight = FontWeight.Bold)
            Text("Filter by Pitaka / Sub-Pitaka", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                allPitakas.filter { it.id in descendantIds }.forEach { p ->
                    FilterChip(selected = p.id in selectedIds, onClick = { selectedIds = if (p.id in selectedIds) selectedIds - p.id else selectedIds + p.id }, label = { Text(p.name) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            Text(if (selectedIds.isEmpty()) "Showing this Pitaka and all descendants" else "Showing selected Pitakas", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

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
            message = "This permanently removes \"${pitaka?.name}\". Children become root Pitakas; a leaf's records and every linked accounting effect are reversed. Export a backup first if needed.",
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
            pitakas = leafPitakas,
            goals = allGoals,
            funnels = allFunnels,
            onSave = { replacement ->
                viewModel.replaceEntry(entry, replacement)
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
    var masked by remember { mutableStateOf(false) }
    val (label, signedAmount, color) = describeEntry(entry, pitakaId, currency)
    val editable = entry.type != LedgerType.OPENING_BALANCE
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        AdaptiveText(entry.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        AdaptiveText(
            "$label  •  ${dateFormat.format(Date(entry.date))}",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AdaptiveText(if (masked) "••••••" else signedAmount, modifier = Modifier.weight(1f), color = color, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { masked = !masked }) {
                Icon(if (masked) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (masked) "Show amount" else "Hide amount")
            }
            if (editable) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
            }
            if (editable) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

private fun describeEntry(entry: LedgerEntry, pitakaId: Long, currency: String): Triple<String, String, Color> {
    fun fmt(amount: Double) = "$currency ${"%,.2f".format(amount)}"
    return when (entry.type) {
        LedgerType.OPENING_BALANCE -> Triple("Opening balance", "+${fmt(entry.amount)}", Color(0xFF1E8E5A))
        LedgerType.INCOME -> Triple("Income", "+${fmt(entry.amount)}", Color(0xFF1E8E5A))
        LedgerType.EXPENSE -> Triple(entry.category ?: "Expense", "-${fmt(entry.amount)}", Color(0xFFC62800))
        LedgerType.GOAL_CONTRIBUTION -> Triple("Goal contribution", "-${fmt(entry.amount)}", Color(0xFFC62800))
        LedgerType.GOAL_WITHDRAWAL -> Triple("Goal withdrawal", "+${fmt(entry.secondaryAmount ?: entry.amount)}", Color(0xFF1E8E5A))
        LedgerType.GOAL_EXPENSE -> Triple(entry.category ?: "Goal expense", "-${fmt(entry.amount)}", Color(0xFFC62800))
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
