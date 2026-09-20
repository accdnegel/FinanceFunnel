package com.pitaka.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
                imageVector = if (masked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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


@Composable
fun CardStylePicker(selected: String, onSelected: (String) -> Unit) {
    val styles = listOf("solid" to "Solid", "waves" to "Waves", "floral" to "Floral", "leaf" to "Leaf", "ocean" to "Ocean", "diamond" to "Diamond", "sun" to "Sun", "wildlife" to "Wildlife")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Card art")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(styles) { (id, label) ->
                FilterChip(selected = selected == id, onClick = { onSelected(id) }, label = { Text(label) })
            }
        }
    }
}

@Composable
fun BatikCardSurface(style: String, baseColor: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(22.dp)).background(baseColor)) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            when (style) {
                "waves", "ocean" -> {
                    repeat(5) { i ->
                        val y = size.height * (0.52f + i * 0.12f)
                        drawArc(Color(0xFF17C6D4).copy(alpha = .30f), 190f, 160f, false,
                            androidx.compose.ui.geometry.Rect(-size.width*.15f, y-size.height*.18f, size.width*1.15f, y+size.height*.18f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(size.height*.045f))
                    }
                }
                "floral" -> {
                    val cx=size.width*.78f; val cy=size.height*.30f
                    repeat(6){i->
                        val a=Math.toRadians((i*60).toDouble())
                        val x=cx+size.width*.13f*kotlin.math.cos(a).toFloat()
                        val y=cy+size.height*.13f*kotlin.math.sin(a).toFloat()
                        drawCircle(Color(0xFFFFE082).copy(alpha=.65f),size.minDimension*.07f,androidx.compose.ui.geometry.Offset(x,y))
                    }
                    drawCircle(Color(0xFFFF8A00),size.minDimension*.045f,androidx.compose.ui.geometry.Offset(cx,cy))
                }
                "leaf" -> {
                    repeat(7){i->
                        val x=size.width*(.62f+i*.05f); val y=size.height*(.18f+i*.10f)
                        drawOval(Color(0xFF75C043).copy(alpha=.55f),androidx.compose.ui.geometry.Rect(x-size.width*.04f,y-size.height*.08f,x+size.width*.04f,y+size.height*.08f))
                    }
                }
                "diamond" -> {
                    repeat(3){i->
                        val left=size.width*(.62f+i*.08f); val top=size.height*(.12f+i*.09f)
                        val path=androidx.compose.ui.graphics.Path().apply{moveTo(left,top);lineTo(left+size.width*.08f,top+size.height*.09f);lineTo(left,top+size.height*.18f);lineTo(left-size.width*.08f,top+size.height*.09f);close()}
                        drawPath(path,Color(0xFFFFC107).copy(alpha=.7f))
                    }
                }
                "sun" -> {
                    val center=androidx.compose.ui.geometry.Offset(size.width*.78f,size.height*.25f)
                    drawCircle(Color(0xFFFFC107).copy(alpha=.75f),size.minDimension*.12f,center)
                    repeat(10){i->drawLine(Color(0xFFFFD54F).copy(alpha=.65f),center,center+androidx.compose.ui.geometry.Offset(kotlin.math.cos(i*0.628f),kotlin.math.sin(i*0.628f))*size.minDimension*.24f,strokeWidth=size.minDimension*.025f)}
                }
                "wildlife" -> {
                    drawCircle(Color(0xFF101B4D).copy(alpha=.7f),size.minDimension*.12f,androidx.compose.ui.geometry.Offset(size.width*.78f,size.height*.28f))
                    drawCircle(Color(0xFFFF8A00).copy(alpha=.9f),size.minDimension*.045f,androidx.compose.ui.geometry.Offset(size.width*.87f,size.height*.26f))
                    drawOval(Color(0xFF0C7A63).copy(alpha=.7f),androidx.compose.ui.geometry.Rect(size.width*.62f,size.height*.35f,size.width*.92f,size.height*.58f))
                }
            }
        }
        content()
    }
}
