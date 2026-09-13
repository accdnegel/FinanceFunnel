package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.LedgerType
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRulesScreen(viewModel: PitakaViewModel, onBack: () -> Unit) {
    val rules by viewModel.recurringRules.collectAsState(initial = emptyList())
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    var showAddForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddForm = !showAddForm }) {
                Icon(Icons.Default.Add, contentDescription = "Add recurring rule")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Posts automatically the first time you open the app on or after the day each month.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (showAddForm) {
                AddRecurringForm(
                    pitakas = pitakas,
                    onSave = { type, name, amount, category, pitakaId, day ->
                        viewModel.createRecurringRule(type, name, amount, category, pitakaId, day)
                        showAddForm = false
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
            }

            if (rules.isEmpty()) {
                Text("No recurring income or expenses set up yet.", color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    items(rules, key = { it.id }) { rule ->
                        val pitakaName = pitakas.find { it.id == rule.pitakaId }?.name ?: "Unknown Pitaka"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(rule.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${if (rule.type == LedgerType.INCOME) "Income" else "Expense"} • " +
                                        "$${"%,.2f".format(rule.amount)} • Day ${rule.dayOfMonth} • $pitakaName",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rule.active,
                                    onCheckedChange = { viewModel.setRecurringRuleActive(rule, it) }
                                )
                                IconButton(onClick = { viewModel.deleteRecurringRule(rule) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecurringForm(
    pitakas: List<Pitaka>,
    onSave: (LedgerType, String, Double, String?, Long, Int) -> Unit
) {
    var type by remember { mutableStateOf(LedgerType.EXPENSE) }
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("1") }
    var pitaka by remember { mutableStateOf<Pitaka?>(pitakas.firstOrNull()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = type == LedgerType.INCOME,
                onClick = { type = LedgerType.INCOME },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("Income") }
            SegmentedButton(
                selected = type == LedgerType.EXPENSE,
                onClick = { type = LedgerType.EXPENSE },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("Expense") }
        }
        if (pitakas.isEmpty()) {
            Text("Create a Pitaka first.", color = Color.Gray)
        } else {
            PitakaDropdown(label = "Pitaka", pitakas = pitakas, selected = pitaka, onSelected = { pitaka = it })
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Salary, Rent)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
            if (type == LedgerType.EXPENSE) {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = dayText,
                onValueChange = { dayText = it },
                label = { Text("Day of month (1-31)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    val day = dayText.toIntOrNull()
                    val p = pitaka
                    if (name.isNotBlank() && amount != null && amount > 0 && day != null && p != null) {
                        onSave(type, name, amount, category.ifBlank { null }, p.id, day)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Rule") }
        }
    }
}
