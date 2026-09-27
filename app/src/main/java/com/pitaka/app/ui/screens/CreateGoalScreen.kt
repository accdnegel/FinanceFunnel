package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.GoalType
import com.pitaka.app.data.CurrencyBalances
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.ColorSwatchPicker
import com.pitaka.app.ui.components.DatePickerButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalScreen(viewModel: PitakaViewModel, goalId: Long? = null, onDone: () -> Unit) {
    var type by remember { mutableStateOf(GoalType.SAVINGS) }
    var name by remember { mutableStateOf("") }
    val targets = remember { mutableStateListOf("PHP" to "") }
    var targetDate by remember { mutableStateOf<Long?>(null) }
    var selectedColor by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(goalId == null) }

    LaunchedEffect(goalId) {
        if (goalId != null) {
            viewModel.getGoal(goalId)?.let { g ->
                type = g.type
                name = g.name
                targets.clear()
                CurrencyBalances.parse(g.targetBalances).forEach { (code, amount) -> targets.add(code to amount.toString()) }
                targetDate = g.targetDate
                selectedColor = g.colorHex
            }
            loaded = true
        }
    }

    if (!loaded) return

    Scaffold(topBar = { TopAppBar(title = { Text(if (goalId == null) "New Goal" else "Edit Goal") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
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
                Text("Target amounts", style = MaterialTheme.typography.titleMedium)
                targets.forEachIndexed { index, target ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = target.first,
                            onValueChange = { targets[index] = it.uppercase().take(3) to target.second },
                            label = { Text("Currency") },
                            singleLine = true,
                            modifier = Modifier.weight(0.35f)
                        )
                        OutlinedTextField(
                            value = target.second,
                            onValueChange = { targets[index] = target.first to it },
                            label = { Text("Amount") },
                            singleLine = true,
                            modifier = Modifier.weight(0.55f)
                        )
                        if (targets.size > 1) {
                            IconButton(onClick = { targets.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove target")
                            }
                        }
                    }
                }
                TextButton(onClick = { targets.add("" to "") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add currency target")
                }
                DatePickerButton(
                label = "Target Completion Date",
                selectedDate = targetDate,
                onDatePicked = { targetDate = it },
                onClear = { targetDate = null }
                )
                if (type == GoalType.INVESTMENT) {
                    Text(
                    "Investment contributions come out of a Pitaka like an expense, but are tracked " +
                        "as a non-liquid asset rather than counting toward your monthly expense limit.",
                    style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("Color")
                ColorSwatchPicker(selected = selectedColor, onSelected = { selectedColor = it })
            }

            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val parsedTargets = targets.mapNotNull { (code, text) ->
                        text.toDoubleOrNull()?.let { code.trim().uppercase() to it }
                    }.toMap()
                    val validRows = targets.all { (code, text) ->
                        code.length == 3 && code.all { it in 'A'..'Z' } &&
                            text.toDoubleOrNull()?.let { it > 0 && it.isFinite() } == true
                    }
                    when {
                        name.isBlank() -> validationError = "Enter a goal name."
                        !validRows -> validationError = "Every target needs a 3-letter currency and positive amount."
                        parsedTargets.size != targets.size -> validationError = "Each target currency must be unique."
                        else -> {
                            validationError = null
                            val primary = parsedTargets.entries.first()
                            if (goalId == null) {
                                viewModel.createGoal(name, type, primary.value, targetDate, selectedColor, "solid", primary.key, parsedTargets, onSuccess = onDone)
                            } else {
                                viewModel.updateGoal(goalId, name, type, primary.value, targetDate, selectedColor, "solid", primary.key, parsedTargets, onSuccess = onDone)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (goalId == null) "Create Goal" else "Save Changes")
            }
        }
    }
}
