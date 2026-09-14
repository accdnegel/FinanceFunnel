package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.ColorSwatchPicker
import com.pitaka.app.ui.components.CurrencyDropdown

/** Used for both creating a new Pitaka and editing an existing one's name/currency/color.
 *  The starting balance field only appears when creating — once a Pitaka exists, its
 *  balance only changes through logged transactions (or the explicit "Adjust Balance"
 *  action on its detail screen), never through this form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePitakaScreen(viewModel: PitakaViewModel, pitakaId: Long? = null, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var startingBalance by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("PHP") }
    var selectedColor by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(pitakaId == null) }

    LaunchedEffect(pitakaId) {
        if (pitakaId != null) {
            viewModel.getPitaka(pitakaId)?.let { p ->
                name = p.name
                currency = p.currency
                selectedColor = p.colorHex
            }
            loaded = true
        }
    }

    if (!loaded) return

    Scaffold(topBar = { TopAppBar(title = { Text(if (pitakaId == null) "New Pitaka" else "Edit Pitaka") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Pitaka Name (e.g. Payroll Bank, Cash, GCash)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (pitakaId == null) {
                OutlinedTextField(
                    value = startingBalance,
                    onValueChange = { startingBalance = it },
                    label = { Text("Starting Balance") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    "To change the balance, use \"Adjust Balance\" on this Pitaka's detail screen instead.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            CurrencyDropdown(selected = currency, onSelected = { currency = it })

            Text("Card Color")
            ColorSwatchPicker(selected = selectedColor, onSelected = { selectedColor = it })
            Text(
                "Leave unselected to use the default color.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        if (pitakaId == null) {
                            val balance = startingBalance.toDoubleOrNull() ?: 0.0
                            viewModel.createPitaka(name, balance, currency, selectedColor)
                        } else {
                            viewModel.updatePitakaMeta(pitakaId, name, currency, selectedColor)
                        }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (pitakaId == null) "Create Pitaka" else "Save Changes")
            }
        }
    }
}
