package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.LedgerEntry
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.EditEntryDialog
import com.pitaka.app.ui.components.HealthBar
import com.pitaka.app.ui.components.dateFormat
import com.pitaka.app.ui.theme.healthColor
import com.pitaka.app.util.findSimilarCategory
import java.time.YearMonth
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(viewModel: PitakaViewModel, onOpenBudgetHistory: () -> Unit) {
    val currentBudget by viewModel.currentMonthBudget.collectAsState(initial = null)
    val currentMonthTotal by viewModel.currentMonthExpenseTotal.collectAsState(initial = 0.0)
    val allExpenses by viewModel.allExpenses.collectAsState(initial = emptyList())
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    val categorySuggestions by viewModel.expenseCategories.collectAsState(initial = emptyList())
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    val currentMonthKey = remember { viewModel.currentMonthKey }
    val thisMonthExpenses = allExpenses.filter {
        val ym = YearMonth.from(java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault()))
        ym.toString() == currentMonthKey
    }

    var showLimitEditor by remember { mutableStateOf(false) }
    var limitText by remember { mutableStateOf(currentBudget?.limit?.toString() ?: "") }

    var expenseName by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }
    var expenseCategory by remember { mutableStateOf("") }
    var selectedPitaka by remember { mutableStateOf<Pitaka?>(null) }
    var categoryError by remember { mutableStateOf(false) }
    val similarCategory by remember(expenseCategory, categorySuggestions) {
        derivedStateOf { findSimilarCategory(expenseCategory, categorySuggestions) }
    }

    LaunchedEffect(pitakas) { if (selectedPitaka == null && pitakas.isNotEmpty()) selectedPitaka = pitakas.first() }

    val limit = currentBudget?.limit
    val isInherited = currentBudget != null && currentBudget?.month != currentMonthKey
    val ratio = if (limit != null && limit > 0) {
        ((limit - currentMonthTotal) / limit).toFloat().coerceIn(0f, 1f)
    } else 1f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = {
                    IconButton(onClick = onOpenBudgetHistory) {
                        Icon(Icons.Default.History, contentDescription = "Budget history")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Monthly limit card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("This month's spending", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { limitText = limit?.toString() ?: ""; showLimitEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit monthly limit")
                    }
                }
                Text(
                    "$${"%,.2f".format(currentMonthTotal)}" + (limit?.let { " / $${"%,.2f".format(it)}" } ?: ""),
                    style = MaterialTheme.typography.headlineSmall
                )
                if (limit != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HealthBar(ratio = ratio, color = healthColor(ratio))
                    if (isInherited) {
                        Text(
                            "Carried forward from ${currentBudget?.month} — edit to set a limit just for this month.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    Text("No monthly limit set yet.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (showLimitEditor) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Max Expense for ${currentMonthKey}") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        viewModel.setMonthlyExpenseLimit(currentMonthKey, limitText.toDoubleOrNull())
                        showLimitEditor = false
                    }) { Text("Save") }
                    OutlinedButton(onClick = { showLimitEditor = false }) { Text("Cancel") }
                }
            }

            HorizontalDivider()

            Text("Log Expense", fontWeight = FontWeight.Bold)
            if (pitakas.isEmpty()) {
                Text("Create a Pitaka first so you have something to charge this to.", color = Color.Gray)
            } else {
                PitakaDropdown(
                    label = "Charge to",
                    pitakas = pitakas,
                    selected = selectedPitaka,
                    onSelected = { selectedPitaka = it }
                )
                OutlinedTextField(
                    value = expenseName,
                    onValueChange = { expenseName = it },
                    label = { Text("Expense name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expenseAmount,
                    onValueChange = { expenseAmount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expenseCategory,
                    onValueChange = { expenseCategory = it; if (it.isNotBlank()) categoryError = false },
                    label = { Text("Category (e.g. Groceries, Transport)") },
                    isError = categoryError,
                    supportingText = {
                        if (categoryError) Text("Add a category so this expense can be classified", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                similarCategory?.let { suggestion ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Did you mean ", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(
                            "\"$suggestion\"?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { expenseCategory = suggestion; categoryError = false }
                        )
                    }
                }
                if (categorySuggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categorySuggestions) { suggestion ->
                            AssistChip(
                                onClick = { expenseCategory = suggestion; categoryError = false },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        val amount = expenseAmount.toDoubleOrNull()
                        val pitaka = selectedPitaka
                        val missingCategory = expenseCategory.isBlank()
                        categoryError = missingCategory
                        if (expenseName.isNotBlank() && amount != null && amount > 0 && pitaka != null && !missingCategory) {
                            viewModel.recordExpense(pitaka.id, expenseName, amount, expenseCategory)
                            expenseName = ""
                            expenseAmount = ""
                            expenseCategory = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Expense")
                }
            }

            HorizontalDivider()
            Text("This Month's Expenses", fontWeight = FontWeight.Bold)
            if (thisMonthExpenses.isEmpty()) {
                Text("No expenses logged this month yet.", color = Color.Gray)
            } else {
                thisMonthExpenses.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(entry.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${entry.category ?: "Uncategorized"}  •  ${dateFormat.format(Date(entry.date))}",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$${"%,.2f".format(entry.amount)}", color = Color(0xFFD64545))
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
