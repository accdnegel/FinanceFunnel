package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.DatePickerButton
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetHistoryScreen(viewModel: PitakaViewModel, onBack: () -> Unit) {
    val budgets by viewModel.allBudgets.collectAsState(initial = emptyList())

    var pickedDate by remember { mutableStateOf<Long?>(null) }
    var amountText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget History") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Set a limit for a specific month", fontWeight = FontWeight.Bold)
            Text(
                "It'll apply from that month onward until you set another one for a later month.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            DatePickerButton(label = "Pick any date in the target month", selectedDate = pickedDate, onDatePicked = { pickedDate = it })
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Monthly Max Expense") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    val date = pickedDate
                    if (amount != null && date != null) {
                        val month = YearMonth.from(
                            java.time.Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
                        ).toString()
                        viewModel.setMonthlyExpenseLimit(month, amount)
                        pickedDate = null
                        amountText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text("History", fontWeight = FontWeight.Bold)

            if (budgets.isEmpty()) {
                Text("No monthly limits set yet.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(budgets, key = { it.month }) { budget ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(budget.month, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$${"%,.2f".format(budget.limit)}")
                                IconButton(onClick = { viewModel.setMonthlyExpenseLimit(budget.month, null) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear this month's limit")
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
