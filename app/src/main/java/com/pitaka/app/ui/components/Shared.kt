package com.pitaka.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.pitaka.app.data.*
import com.pitaka.app.data.commonCurrencies
import com.pitaka.app.ui.theme.batikColorPalette
import com.pitaka.app.ui.theme.parseHexColor
import com.pitaka.app.util.DatabaseBackup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.pitaka.app.R

@Composable
fun AdaptiveText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    minFontSize: TextUnit = 10.sp
) {
    val initialFontSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val availableWidth = constraints.maxWidth
        val resolvedFontSize = remember(text, style, fontWeight, initialFontSize, minFontSize, maxLines, availableWidth) {
            if (availableWidth == Constraints.Infinity) {
                initialFontSize
            } else {
                var candidate = initialFontSize
                while (candidate > minFontSize) {
                    val result = textMeasurer.measure(
                        text = text,
                        style = style.copy(fontSize = candidate, fontWeight = fontWeight ?: style.fontWeight),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = maxLines,
                        constraints = Constraints(maxWidth = availableWidth)
                    )
                    if (!result.hasVisualOverflow) break
                    candidate = (candidate.value - 1f).coerceAtLeast(minFontSize.value).sp
                }
                candidate
            }
        }

        Text(
            text = text,
            style = style.copy(fontSize = resolvedFontSize),
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PitakaSourceFilter(
    pitakas: List<Pitaka>,
    selectedPitakaId: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pitakas.isEmpty()) return

    Column(modifier) {
        Text("Filter by source Pitaka", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedPitakaId == null,
                    onClick = { onSelected(null) },
                    label = { Text("All") }
                )
            }
            items(pitakas, key = { it.id }) { pitaka ->
                FilterChip(
                    selected = selectedPitakaId == pitaka.id,
                    onClick = { onSelected(pitaka.id) },
                    label = { AdaptiveText(pitaka.name, modifier = Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.labelLarge) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitakaDropdown(
    label: String,
    pitakas: List<com.pitaka.app.data.Pitaka>,
    selected: com.pitaka.app.data.Pitaka?,
    onSelected: (com.pitaka.app.data.Pitaka) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = !open }
    ) {
        OutlinedTextField(
            value = selected?.name ?: "Select Pitaka",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            pitakas.forEach { pitaka ->
                DropdownMenuItem(
                    text = { Text(pitaka.name) },
                    onClick = {
                        onSelected(pitaka)
                        open = false
                    }
                )
            }
        }
    }
}

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
fun DatePickerButton(label: String, selectedDate: Long?, onDatePicked: (Long) -> Unit, onClear: (() -> Unit)? = null) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
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
    if (selectedDate != null && onClear != null) {
        IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear date") }
    }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    additionalContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { Text(message); additionalContent?.invoke(); backupError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { scope.launch { runCatching { DatabaseBackup.create(context) }.onSuccess { DatabaseBackup.share(context, it) }.onFailure { backupError = it.message } } }) { Text("Export backup") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoryField(
    value: String,
    categories: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.trim()
    val suggestions = categories
        .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
        .take(8)

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("Category") },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && suggestions.isNotEmpty()) }
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = { onValueChange(suggestion); expanded = false }
                )
            }
        }
    }
}

/** Edits every mutable field while keeping the transaction type immutable. */
@Composable
fun EditEntryDialog(
    entry: LedgerEntry,
    pitakas: List<Pitaka>,
    goals: List<GoalWithProgress>,
    funnels: List<ExpenseFunnel>,
    onSave: (LedgerEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(entry.name) }
    var amountText by remember { mutableStateOf(entry.amount.let { kotlin.math.abs(it) }.toString()) }
    var category by remember { mutableStateOf(entry.category ?: "") }
    var currency by remember { mutableStateOf(entry.currency) }
    var date by remember { mutableStateOf<Long?>(entry.date) }
    var pitaka by remember { mutableStateOf(pitakas.find { it.id == entry.pitakaId }) }
    var fromPitaka by remember { mutableStateOf(pitakas.find { it.id == entry.fromPitakaId }) }
    var toPitaka by remember { mutableStateOf(pitakas.find { it.id == entry.toPitakaId }) }
    var goal by remember { mutableStateOf(goals.find { it.id == entry.goalId }) }
    var funnel by remember { mutableStateOf(funnels.find { it.id == entry.funnelId }) }
    var secondaryAmount by remember { mutableStateOf((entry.secondaryAmount ?: entry.amount).toString()) }
    var secondaryCurrency by remember { mutableStateOf(entry.secondaryCurrency ?: entry.currency) }
    var allocationAmount by remember { mutableStateOf((entry.goalAmount ?: entry.funnelAmount ?: entry.amount).toString()) }
    var allocationCurrency by remember { mutableStateOf(entry.goalCurrency ?: entry.funnelCurrency ?: entry.currency) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry") },
        text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") })
                OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase().take(3) }, label = { Text("Currency") })
                DatePickerButton("Transaction date", date, { date = it })
                if (entry.type in setOf(LedgerType.EXPENSE, LedgerType.GOAL_EXPENSE)) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                }
                when (entry.type) {
                    LedgerType.INCOME, LedgerType.EXPENSE, LedgerType.GOAL_CONTRIBUTION, LedgerType.ADJUSTMENT, LedgerType.GOAL_WITHDRAWAL ->
                        PitakaDropdown(if (entry.type == LedgerType.GOAL_WITHDRAWAL) "Destination Pitaka" else "Pitaka", pitakas, pitaka) { pitaka = it }
                    LedgerType.TRANSFER -> {
                        PitakaDropdown("Source Pitaka", pitakas, fromPitaka) { fromPitaka = it }
                        PitakaDropdown("Destination Pitaka", pitakas, toPitaka) { toPitaka = it }
                    }
                    else -> Unit
                }
                if (entry.type in setOf(LedgerType.GOAL_CONTRIBUTION, LedgerType.GOAL_WITHDRAWAL, LedgerType.GOAL_EXPENSE)) {
                    EntityDropdown("Goal", goals.map { it.id to it.name }, goal?.id) { id -> goal = goals.find { it.id == id } }
                    OutlinedTextField(allocationAmount, { allocationAmount = it }, label = { Text("Goal amount") })
                    OutlinedTextField(allocationCurrency, { allocationCurrency = it.uppercase().take(3) }, label = { Text("Goal currency") })
                }
                if (entry.type in setOf(LedgerType.EXPENSE, LedgerType.GOAL_EXPENSE)) {
                    EntityDropdown("Expense Funnel", funnels.map { it.id to it.name }, funnel?.id) { id -> funnel = funnels.find { it.id == id } }
                    if (entry.type == LedgerType.EXPENSE) {
                        OutlinedTextField(allocationAmount, { allocationAmount = it }, label = { Text("Funnel amount") })
                        OutlinedTextField(allocationCurrency, { allocationCurrency = it.uppercase().take(3) }, label = { Text("Funnel currency") })
                    }
                }
                if (entry.type in setOf(LedgerType.TRANSFER, LedgerType.GOAL_WITHDRAWAL)) {
                    OutlinedTextField(secondaryAmount, { secondaryAmount = it }, label = { Text("Destination amount") })
                    OutlinedTextField(secondaryCurrency, { secondaryCurrency = it.uppercase().take(3) }, label = { Text("Destination currency") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull() ?: return@TextButton
                val signedAmount = if (entry.type == LedgerType.ADJUSTMENT) {
                    if (entry.amount < 0) -amount else amount
                } else amount
                onSave(entry.copy(
                    name = name,
                    amount = signedAmount,
                    currency = currency,
                    category = category.takeIf { entry.type in setOf(LedgerType.EXPENSE, LedgerType.GOAL_EXPENSE) },
                    pitakaId = pitaka?.id ?: entry.pitakaId,
                    fromPitakaId = fromPitaka?.id ?: entry.fromPitakaId,
                    toPitakaId = toPitaka?.id ?: entry.toPitakaId,
                    goalId = goal?.id ?: entry.goalId,
                    funnelId = funnel?.id ?: entry.funnelId,
                    goalAmount = allocationAmount.toDoubleOrNull().takeIf { entry.type in setOf(LedgerType.GOAL_CONTRIBUTION, LedgerType.GOAL_WITHDRAWAL, LedgerType.GOAL_EXPENSE) } ?: entry.goalAmount,
                    goalCurrency = allocationCurrency.takeIf { entry.type in setOf(LedgerType.GOAL_CONTRIBUTION, LedgerType.GOAL_WITHDRAWAL, LedgerType.GOAL_EXPENSE) } ?: entry.goalCurrency,
                    funnelAmount = allocationAmount.toDoubleOrNull().takeIf { entry.type in setOf(LedgerType.EXPENSE, LedgerType.GOAL_EXPENSE) } ?: entry.funnelAmount,
                    funnelCurrency = allocationCurrency.takeIf { entry.type in setOf(LedgerType.EXPENSE, LedgerType.GOAL_EXPENSE) } ?: entry.funnelCurrency,
                    secondaryAmount = secondaryAmount.toDoubleOrNull().takeIf { entry.type in setOf(LedgerType.TRANSFER, LedgerType.GOAL_WITHDRAWAL) } ?: entry.secondaryAmount,
                    secondaryCurrency = secondaryCurrency.takeIf { entry.type in setOf(LedgerType.TRANSFER, LedgerType.GOAL_WITHDRAWAL) } ?: entry.secondaryCurrency,
                    date = date ?: entry.date
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityDropdown(label: String, items: List<Pair<Long, String>>, selectedId: Long?, onSelected: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = !open }) {
        OutlinedTextField(
            value = items.find { it.first == selectedId }?.second ?: "Select $label",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelected(id); open = false })
            }
        }
    }
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
    LaunchedEffect(selected) { customText = selected }

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


data class CardStyle(
    val id: String,
    val name: String,
    val base: Color,
    val imageRes: Int? = null
)

private val palette = listOf(
    CardStyle("template_01","Sun & Waves",Color(0xFF0278CF),R.drawable.pitaka_template_01),
    CardStyle("template_02","White Flower",Color(0xFF0278CF),R.drawable.pitaka_template_02),
    CardStyle("template_03","Sea Turtle",Color(0xFF0278CF),R.drawable.pitaka_template_03),
    CardStyle("template_04","Hibiscus",Color(0xFF0278CF),R.drawable.pitaka_template_04),
    CardStyle("template_05","Toucan",Color(0xFF0278CF),R.drawable.pitaka_template_05),
    CardStyle("template_06","Green Hills",Color(0xFF0278CF),R.drawable.pitaka_template_06),
    CardStyle("template_07","Clownfish",Color(0xFF0278CF),R.drawable.pitaka_template_07),
    CardStyle("template_08","Shell",Color(0xFF0278CF),R.drawable.pitaka_template_08),
    CardStyle("template_09","Bamboo",Color(0xFF0278CF),R.drawable.pitaka_template_09),
    CardStyle("template_10","Diamond",Color(0xFF0278CF),R.drawable.pitaka_template_10),
    CardStyle("template_11","Whale Shark",Color(0xFF0278CF),R.drawable.pitaka_template_11),
    CardStyle("template_12","Butterfly",Color(0xFF0278CF),R.drawable.pitaka_template_12),
    CardStyle("template_13","Coral Reef",Color(0xFF0278CF),R.drawable.pitaka_template_13),
    CardStyle("template_14","Sunset",Color(0xFF0278CF),R.drawable.pitaka_template_14),
    CardStyle("template_15","Bird & Leaves",Color(0xFF0278CF),R.drawable.pitaka_template_15),
    CardStyle("template_16","Mountain",Color(0xFF0278CF),R.drawable.pitaka_template_16),
    CardStyle("template_17","Starfish",Color(0xFF0278CF),R.drawable.pitaka_template_17),
    CardStyle("template_18","Leaf Burst",Color(0xFF0278CF),R.drawable.pitaka_template_18),
    CardStyle("template_19","Seahorse",Color(0xFF0278CF),R.drawable.pitaka_template_19),
    CardStyle("template_20","Flame Leaf",Color(0xFF0278CF),R.drawable.pitaka_template_20),
    CardStyle("template_21","Deep Waves",Color(0xFF0278CF),R.drawable.pitaka_template_21),
    CardStyle("template_22","Tropical Flower",Color(0xFF0278CF),R.drawable.pitaka_template_22),
    CardStyle("template_23","Reef Fish",Color(0xFF0278CF),R.drawable.pitaka_template_23),
    CardStyle("template_24","Leaf & Wave",Color(0xFF0278CF),R.drawable.pitaka_template_24),
    CardStyle("template_25","Abstract Batik",Color(0xFF0278CF),R.drawable.pitaka_template_25),
    CardStyle("template_26","Rainbow Waves",Color(0xFF0278CF),R.drawable.pitaka_template_26),
    CardStyle("template_27","Floral Red",Color(0xFF0278CF),R.drawable.pitaka_template_27),
)

fun cardStyles(): List<CardStyle> = palette
fun cardStyleById(id: String): CardStyle = palette.firstOrNull { it.id == id } ?: palette.first()

/**
 * Displays a Pitaka using an image-backed template. The artwork lives in
 * drawable-nodpi so the selected PNG is rendered without density-specific
 * scaling. "solid" remains supported for older Pitakas or callers that do
 * not select a template.
 */
@Composable
fun BatikCardSurface(
    style: String,
    baseColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val selected = if (style == "solid") null else cardStyleById(style)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(selected?.base ?: baseColor)
    ) {
        selected?.imageRes?.let { resId ->
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardStylePicker(
    selected: String,
    solidColor: Color,
    onSelected: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val current = if (selected == "solid") null else cardStyles().firstOrNull { it.id == selected }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Card Design", style = MaterialTheme.typography.titleMedium)
                Text(
                    current?.name ?: "Solid Color",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { open = true }) { Text("Change") }
        }

        // Always show the actual card that will be created.
        BatikCardSurface(
            style = selected,
            baseColor = if (selected == "solid") solidColor else (current?.base ?: solidColor),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp, max = 168.dp)
        ) {
            Text(
                "PITAKA",
                color = Color.White.copy(alpha = .82f),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                current?.name ?: "Solid Color",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Preview",
                color = Color.White.copy(alpha = .82f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Choose Card Design", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Choose either a solid color or a Batik template.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { open = false }) { Text("Done") }
                }

                Spacer(Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Solid Color is a real design option, not a fallback.
                    item {
                        val isSelected = selected == "solid"
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onSelected("solid")
                                    open = false
                                }
                                .padding(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BatikCardSurface(
                                style = "solid",
                                baseColor = solidColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.35f)
                            ) {
                                Text(
                                    "PITAKA",
                                    color = Color.White.copy(alpha = .8f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Solid",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                "Solid Color",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    items(cardStyles()) { style ->
                        val isSelected = selected == style.id
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onSelected(style.id)
                                    open = false
                                }
                                .padding(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BatikCardSurface(
                                style = style.id,
                                baseColor = style.base,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.35f)
                            ) {
                                Text(
                                    "PITAKA",
                                    color = Color.White.copy(alpha = .8f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    style.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                style.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
