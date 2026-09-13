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
import com.pitaka.app.ui.components.CurrencyDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySettingsScreen(viewModel: PitakaViewModel, onBack: () -> Unit) {
    val settings by viewModel.currencySettings.collectAsState(initial = null)
    val rates by viewModel.exchangeRates.collectAsState(initial = emptyList())
    val baseCurrency = settings?.baseCurrency ?: "USD"

    var newRateCode by remember { mutableStateOf("PHP") }
    var newRateValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Currency Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Display currency", fontWeight = FontWeight.Bold)
            Text(
                "Net worth totals on Home are converted into this currency.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            CurrencyDropdown(selected = baseCurrency, onSelected = { viewModel.setBaseCurrency(it) })

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Exchange rates", fontWeight = FontWeight.Bold)
            Text(
                "Manually maintained — this app works fully offline, so there's no live rate lookup. " +
                    "Enter how many $baseCurrency one unit of another currency is worth.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyDropdown(selected = newRateCode, onSelected = { newRateCode = it }, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = newRateValue,
                    onValueChange = { newRateValue = it },
                    label = { Text("= $baseCurrency") },
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    val rate = newRateValue.toDoubleOrNull()
                    if (rate != null && newRateCode.isNotBlank()) {
                        viewModel.setExchangeRate(newRateCode, rate)
                        newRateValue = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Save Rate")
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (rates.isEmpty()) {
                Text("No custom rates saved yet — currencies other than $baseCurrency will be treated 1:1 until you add one.", color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(rates, key = { it.code }) { rate ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("1 ${rate.code} = ${rate.rateToBase} $baseCurrency")
                            IconButton(onClick = { viewModel.deleteExchangeRate(rate.code) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete rate")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
