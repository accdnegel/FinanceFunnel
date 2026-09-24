package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.ExchangeRate
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.data.CurrencyRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(viewModel: PitakaViewModel, onDone: () -> Unit) {
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    val rates by viewModel.exchangeRates.collectAsState(initial = emptyList())
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
    val conversionError = if (crossCurrency && amount != null) {
        try { CurrencyRules.convert(amount, fromPitaka!!.currency, toPitaka!!.currency, rates); null }
        catch (e: IllegalArgumentException) { e.message ?: "Configure exchange rates first." }
    } else null
    val convertedAmount = if (crossCurrency && amount != null && conversionError == null) {
        CurrencyRules.convert(amount, fromPitaka!!.currency, toPitaka!!.currency, rates)
    } else if (!crossCurrency) amount else null

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
                if (crossCurrency && conversionError != null) {
                    Text(conversionError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
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
                            amount == null || amount <= 0 || !amount.isFinite() -> error = "Enter a valid amount."
                            crossCurrency && convertedAmount == null -> error = conversionError ?: "Configure exchange rates first."
                            else -> {
                                viewModel.recordTransfer(
                                    fromPitakaId = fromPitaka!!.id,
                                    toPitakaId = toPitaka!!.id,
                                    name = name.ifBlank { "Transfer" },
                                    amount = amount,
                                    secondaryAmount = convertedAmount
                                )
                                onDone()
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
