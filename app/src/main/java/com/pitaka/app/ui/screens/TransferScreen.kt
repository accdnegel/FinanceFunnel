package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.ExchangeRate
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(viewModel: PitakaViewModel, onDone: () -> Unit) {
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    val rates by viewModel.exchangeRates.collectAsState(initial = emptyList())
    val currencySettings by viewModel.currencySettings.collectAsState(initial = null)
    val baseCurrency = currencySettings?.baseCurrency ?: "PHP"
    var fromPitaka by remember { mutableStateOf<Pitaka?>(null) }
    var toPitaka by remember { mutableStateOf<Pitaka?>(null) }
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pitakas) {
        if (fromPitaka == null && pitakas.isNotEmpty()) fromPitaka = pitakas.first()
        if (toPitaka == null && pitakas.size > 1) toPitaka = pitakas[1]
    }

    val amount = amountText.toDoubleOrNull()
    val crossCurrency = fromPitaka != null && toPitaka != null && fromPitaka?.currency != toPitaka?.currency
    val convertedAmount = if (crossCurrency && amount != null) {
        convertBetween(amount, fromPitaka!!.currency, toPitaka!!.currency, baseCurrency, rates)
    } else null

    Scaffold(topBar = { TopAppBar(title = { Text("Transfer Between Pitakas") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (pitakas.size < 2) {
                Text("You need at least two Pitakas to transfer between them.")
            } else {
                PitakaDropdown(label = "From", pitakas = pitakas, selected = fromPitaka, onSelected = { fromPitaka = it })
                PitakaDropdown(label = "To", pitakas = pitakas, selected = toPitaka, onSelected = { toPitaka = it })
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Note (e.g. Reallocate for rent)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${fromPitaka?.currency ?: ""})") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (crossCurrency && convertedAmount != null) {
                    Text(
                        "≈ ${toPitaka?.currency} ${"%,.2f".format(convertedAmount)} will be added to ${toPitaka?.name}, " +
                            "based on your saved exchange rates.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        when {
                            fromPitaka == null || toPitaka == null -> error = "Pick both Pitakas."
                            fromPitaka?.id == toPitaka?.id -> error = "Pick two different Pitakas."
                            amount == null || amount <= 0 -> error = "Enter a valid amount."
                            else -> {
                                viewModel.recordTransfer(
                                    fromPitakaId = fromPitaka!!.id,
                                    toPitakaId = toPitaka!!.id,
                                    name = name.ifBlank { "Transfer" },
                                    amount = amount,
                                    secondaryAmount = convertedAmount,
                                    onSuccess = onDone
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Transfer")
                }
            }
        }
    }
}

private fun convertBetween(amount: Double, from: String, to: String, baseCurrency: String, rates: List<ExchangeRate>): Double? {
    if (from == to) return amount
    val fromRate = if (from.equals(baseCurrency, true)) 1.0 else rates.find { it.code.equals(from, true) }?.rateToBase
    val toRate = if (to.equals(baseCurrency, true)) 1.0 else rates.find { it.code.equals(to, true) }?.rateToBase
    // The base currency has an implicit 1:1 rate. Never silently treat a
    // missing non-base rate as 1.0; that would produce a false conversion.
    if (fromRate == null || toRate == null) return null
    return amount * fromRate / toRate
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitakaDropdown(label: String, pitakas: List<Pitaka>, selected: Pitaka?, onSelected: (Pitaka) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.name} (${it.currency})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pitakas.forEach { pitaka ->
                DropdownMenuItem(
                    text = { Text("${pitaka.name} (${pitaka.currency} ${"%,.2f".format(pitaka.currentAmount)})") },
                    onClick = {
                        onSelected(pitaka)
                        expanded = false
                    }
                )
            }
        }
    }
}
