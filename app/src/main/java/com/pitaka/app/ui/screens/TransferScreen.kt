package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.ExchangeRate
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.PitakaDropdown
import com.pitaka.app.ui.components.CurrencyDropdown
import com.pitaka.app.data.CurrencyRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(viewModel: PitakaViewModel, onDone: () -> Unit) {
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    val leafPitakas = pitakas.filter { candidate -> pitakas.none { it.parentPitakaId == candidate.id } }
    val rates by viewModel.exchangeRates.collectAsState(initial = emptyList())
    var fromPitaka by remember { mutableStateOf<Pitaka?>(null) }
    var toPitaka by remember { mutableStateOf<Pitaka?>(null) }
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var destinationAmountText by remember { mutableStateOf("") }
    var sourceCurrency by remember { mutableStateOf("PHP") }
    var destinationCurrency by remember { mutableStateOf("PHP") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(leafPitakas) {
        if (fromPitaka == null && leafPitakas.isNotEmpty()) {
            fromPitaka = leafPitakas.first()
            sourceCurrency = leafPitakas.first().currency
        }
        if (toPitaka == null && leafPitakas.size > 1) {
            toPitaka = leafPitakas[1]
            destinationCurrency = leafPitakas[1].currency
        }
    }

    val amount = amountText.toDoubleOrNull()
    val crossCurrency = sourceCurrency != destinationCurrency
    val conversionError = if (crossCurrency && amount != null) {
        try { CurrencyRules.convert(amount, sourceCurrency, destinationCurrency, rates); null }
        catch (e: IllegalArgumentException) { e.message ?: "Configure exchange rates first." }
    } else null
    val convertedAmount = if (crossCurrency && amount != null && conversionError == null) {
        CurrencyRules.convert(amount, sourceCurrency, destinationCurrency, rates)
    } else if (!crossCurrency) amount else null

    Scaffold(topBar = { TopAppBar(title = { Text("Transfer Between Pitakas") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (leafPitakas.size < 2) {
                Text("You need at least two Pitakas to transfer between them.")
            } else {
                PitakaDropdown(label = "From", pitakas = leafPitakas, selected = fromPitaka, onSelected = { fromPitaka = it; sourceCurrency = it.currency })
                CurrencyDropdown(sourceCurrency, onSelected = { sourceCurrency = it })
                PitakaDropdown(label = "To", pitakas = leafPitakas, selected = toPitaka, onSelected = { toPitaka = it; destinationCurrency = it.currency })
                CurrencyDropdown(destinationCurrency, onSelected = { destinationCurrency = it })
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Note (e.g. Reallocate for rent)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Source amount ($sourceCurrency)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (crossCurrency && conversionError != null) {
                    Text(conversionError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (crossCurrency && convertedAmount != null) {
                    Text(
                        "Suggested: $destinationCurrency ${"%,.6f".format(convertedAmount)} based on your saved rates.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = destinationAmountText,
                        onValueChange = { destinationAmountText = it },
                        label = { Text("Destination amount ($destinationCurrency)") },
                        supportingText = { Text("Leave blank to use the suggested amount") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        when {
                            fromPitaka == null || toPitaka == null -> error = "Pick both Pitakas."
                            fromPitaka?.id == toPitaka?.id -> error = "Pick two different Pitakas."
                            amount == null || amount <= 0 || !amount.isFinite() -> error = "Enter a valid amount."
                            crossCurrency && convertedAmount == null -> error = conversionError ?: "Configure exchange rates first."
                            crossCurrency && destinationAmountText.isNotBlank() && (destinationAmountText.toDoubleOrNull()?.let { it > 0 && it.isFinite() } != true) -> error = "Enter a valid destination amount."
                            else -> {
                                val destinationAmount = if (crossCurrency) destinationAmountText.toDoubleOrNull() ?: convertedAmount else null
                                viewModel.recordTransfer(
                                    fromPitakaId = fromPitaka!!.id,
                                    toPitakaId = toPitaka!!.id,
                                    name = name.ifBlank { "Transfer" },
                                    amount = amount,
                                    secondaryAmount = destinationAmount,
                                    sourceCurrency = sourceCurrency,
                                    destinationCurrency = destinationCurrency,
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
