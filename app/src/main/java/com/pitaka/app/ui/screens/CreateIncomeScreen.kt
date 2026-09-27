package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.CurrencyDropdown
import com.pitaka.app.ui.components.DatePickerButton
import com.pitaka.app.ui.components.PitakaDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIncomeScreen(viewModel: PitakaViewModel, onDone: () -> Unit) {
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    val leaves = pitakas.filter { candidate -> pitakas.none { it.parentPitakaId == candidate.id } }
    var selected by remember { mutableStateOf<Pitaka?>(null) }
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("PHP") }
    var date by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(leaves) {
        if (selected == null) {
            selected = leaves.firstOrNull()
            selected?.let { currency = it.currency }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Income") },
                navigationIcon = { TextButton(onClick = onDone) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leaves.isEmpty()) {
                Text("Create a leaf Pitaka before recording income.")
            } else {
                PitakaDropdown("Credit to", leaves, selected) {
                    selected = it
                    currency = it.currency
                }
                OutlinedTextField(name, { name = it }, label = { Text("Income name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount ($currency)") }, modifier = Modifier.fillMaxWidth())
                CurrencyDropdown(currency, onSelected = { currency = it })
                DatePickerButton("Income date", date, { date = it })
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        when {
                            selected == null -> error = "Select a destination Pitaka."
                            name.isBlank() -> error = "Enter an income name."
                            amount == null || amount <= 0 || !amount.isFinite() -> error = "Enter a valid positive amount."
                            else -> viewModel.recordIncome(selected!!.id, name.trim(), amount, date ?: System.currentTimeMillis(), currency, onDone)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save Income") }
            }
        }
    }
}
