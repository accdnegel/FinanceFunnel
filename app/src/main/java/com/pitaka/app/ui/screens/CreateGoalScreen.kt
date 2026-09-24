package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.GoalType
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.ColorSwatchPicker
import com.pitaka.app.ui.components.CardStylePicker
import com.pitaka.app.ui.components.DatePickerButton
import com.pitaka.app.ui.components.CurrencyDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalScreen(viewModel: PitakaViewModel, goalId: Long? = null, onDone: () -> Unit) {
    var type by remember { mutableStateOf(GoalType.SAVINGS) }
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf<Long?>(null) }
    var selectedColor by remember { mutableStateOf<String?>(null) }
    var cardStyle by remember { mutableStateOf("solid") }
    var currency by remember { mutableStateOf("PHP") }
    var loaded by remember { mutableStateOf(goalId == null) }

    LaunchedEffect(goalId) {
        if (goalId != null) {
            viewModel.getGoal(goalId)?.let { g ->
                type = g.type
                name = g.name
                targetAmount = g.targetAmount.toString()
                targetDate = g.targetDate
                selectedColor = g.colorHex
                cardStyle = g.cardStyle
                currency = g.currency
            }
            loaded = true
        }
    }

    if (!loaded) return

    Scaffold(topBar = { TopAppBar(title = { Text(if (goalId == null) "New Goal" else "Edit Goal") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == GoalType.SAVINGS,
                    onClick = { type = GoalType.SAVINGS },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Savings") }
                SegmentedButton(
                    selected = type == GoalType.INVESTMENT,
                    onClick = { type = GoalType.INVESTMENT },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Investment") }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Goal Name (e.g. Emergency Fund, Index Fund)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetAmount,
                onValueChange = { targetAmount = it },
                label = { Text("Target Amount") },
                modifier = Modifier.fillMaxWidth()
            )
            CurrencyDropdown(currency, { currency = it })
            DatePickerButton(
                label = "Target Completion Date",
                selectedDate = targetDate,
                onDatePicked = { targetDate = it }
            )
            if (type == GoalType.INVESTMENT) {
                Text(
                    "Investment contributions come out of a Pitaka like an expense, but are tracked " +
                        "as a non-liquid asset rather than counting toward your monthly expense limit.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("Card Color")
            ColorSwatchPicker(selected = selectedColor, onSelected = { selectedColor = it })
            CardStylePicker(selected = cardStyle, solidColor = com.pitaka.app.ui.components.parseHexColor(selectedColor) ?: MaterialTheme.colorScheme.primary) { cardStyle = it }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val amount = targetAmount.toDoubleOrNull() ?: 0.0
                    val date = targetDate ?: System.currentTimeMillis()
                    if (name.isNotBlank() && amount > 0) {
                        if (goalId == null) {
                            viewModel.createGoal(name, type, amount, date, selectedColor, cardStyle, currency)
                        } else {
                            viewModel.updateGoal(goalId, name, type, amount, date, selectedColor, cardStyle, currency)
                        }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (goalId == null) "Create Goal" else "Save Changes")
            }
        }
    }
}
