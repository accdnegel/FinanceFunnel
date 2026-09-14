package com.pitaka.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.LedgerEntry
import com.pitaka.app.data.LedgerType
import com.pitaka.app.data.commonCurrencies
import com.pitaka.app.ui.theme.batikColorPalette
import com.pitaka.app.ui.theme.parseHexColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@Composable
fun MaskedAmount(visibleText: String, masked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (masked) "••••••" else visibleText)
        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (masked) androidx.compose.material.icons.Icons.Default.VisibilityOff else androidx.compose.material.icons.Icons.Default.Visibility,
                contentDescription = if (masked) "Show amount" else "Hide amount"
            )
        }
    }
}


/** A progress bar whose fill color reflects "health" (red = low/at-risk, green = healthy). */
@Composable
fun HealthBar(ratio: Float, color: Color = com.pitaka.app.ui.theme.healthColor(ratio), modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.LightGray.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio.coerceIn(0.03f, 1f))
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@Composable
fun ColorSwatchPicker(selected: String?, onSelected: (String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        batikColorPalette.forEach { hex ->
            val color = parseHexColor(hex) ?: Color.Gray
            val isSelected = selected == hex
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape
                    )
                    .clickable { onSelected(if (isSelected) null else hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun DatePickerButton(label: String, selectedDate: Long?, onDatePicked: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val c = Calendar.getInstance()
                c.set(year, month, day, 0, 0, 0)
                onDatePicked(c.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }) {
        Text(selectedDate?.let { dateFormat.format(Date(it)) } ?: label)
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Edits name/amount/category (category only shown for EXPENSE entries) in place. */
@Composable
fun EditEntryDialog(
    entry: LedgerEntry,
    onSave: (name: String, amount: Double, category: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(entry.name) }
    var amountText by remember { mutableStateOf(entry.amount.let { kotlin.math.abs(it) }.toString()) }
    var category by remember { mutableStateOf(entry.category ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") })
                if (entry.type == LedgerType.EXPENSE) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull() ?: return@TextButton
                val signedAmount = if (entry.type == LedgerType.ADJUSTMENT) {
                    if (entry.amount < 0) -amount else amount
                } else amount
                onSave(name, signedAmount, category.takeIf { entry.type == LedgerType.EXPENSE })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Warns before letting the user override a Pitaka's balance directly, bypassing the logs. */
@Composable
fun AdjustBalanceDialog(
    currentBalance: Double,
    onConfirm: (newBalance: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var newBalanceText by remember { mutableStateOf(currentBalance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manually Adjust Balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This bypasses your income/expense/transfer logs. A manual adjustment entry " +
                        "will be recorded so there's a trail, but this can make your history harder " +
                        "to reconcile later. Proceed only if you know the real balance differs.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = newBalanceText,
                    onValueChange = { newBalanceText = it },
                    label = { Text("New Balance") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                newBalanceText.toDoubleOrNull()?.let { onConfirm(it) }
            }) { Text("Proceed Anyway", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(selected: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf(selected) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = customText,
            onValueChange = {
                customText = it.uppercase().take(3)
                onSelected(customText)
            },
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            commonCurrencies.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        customText = code
                        onSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}
