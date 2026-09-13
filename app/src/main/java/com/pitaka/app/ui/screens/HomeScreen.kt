package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitaka.app.data.CategorySpend
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.util.buildLedgerCsv
import com.pitaka.app.util.exportAndShareCsv
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val palette = listOf(
    Color(0xFFE06A00), Color(0xFF0278CF), Color(0xFF056C3F),
    Color(0xFF8E44AD), Color(0xFFD4537E), Color(0xFFBA7517)
)

private fun monthLabel(yyyyMM: String): String = try {
    YearMonth.parse(yyyyMM).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
} catch (e: Exception) { yyyyMM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PitakaViewModel, onOpenCurrencySettings: () -> Unit) {
    val totalLiquid by viewModel.totalLiquid.collectAsState(initial = 0.0)
    val totalSavings by viewModel.totalSavingsProgress.collectAsState(initial = 0.0)
    val totalInvestments by viewModel.totalInvestmentProgress.collectAsState(initial = 0.0)
    val totalNetWorth by viewModel.totalNetWorth.collectAsState(initial = 0.0)
    val currencySettings by viewModel.currencySettings.collectAsState(initial = null)
    val goals by viewModel.goals.collectAsState(initial = emptyList())
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val monthlyIncome by viewModel.monthlyIncome.collectAsState(initial = emptyList())
    val monthlyExpenses by viewModel.monthlyExpenses.collectAsState(initial = emptyList())
    val monthlyNetChange by viewModel.monthlyNetChange.collectAsState(initial = emptyList())
    val expenseBreakdown by viewModel.expenseBreakdown.collectAsState(initial = emptyList())
    val availableMonths by viewModel.availableMonths.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pitaka") },
                actions = {
                    IconButton(onClick = onOpenCurrencySettings) {
                        Icon(Icons.Default.CurrencyExchange, contentDescription = "Currency settings")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val entries = viewModel.getAllEntriesOnce()
                            val goalNames = goals.associate { it.id to it.name }
                            val csv = buildLedgerCsv(entries, pitakas, goalNames)
                            exportAndShareCsv(context, csv)
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export as CSV")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Net worth summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(16.dp)
            ) {
                Text("Total Net Worth", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(
                    "${currencySettings?.baseCurrency ?: "USD"} ${"%,.2f".format(totalNetWorth)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NetWorthChip("Liquid", totalLiquid, Color(0xFF0278CF))
                    NetWorthChip("Savings", totalSavings, Color(0xFF056C3F))
                    NetWorthChip("Investments", totalInvestments, Color(0xFF8E44AD))
                }
            }

            SectionHeader("Spending by category")
            if (expenseBreakdown.isEmpty()) EmptyHint("No expenses logged yet.") else BreakdownList(expenseBreakdown)

            SectionHeader("Monthly income trend")
            if (monthlyIncome.isEmpty()) EmptyHint("No income logged yet.") else BarChart(
                data = monthlyIncome.map { it.month to it.total }, barColor = Color(0xFF1E8E5A)
            )

            SectionHeader("Monthly expense trend")
            if (monthlyExpenses.isEmpty()) EmptyHint("No expenses logged yet.") else BarChart(
                data = monthlyExpenses.map { it.month to it.total }, barColor = Color(0xFFD64545)
            )

            SectionHeader("Monthly net change")
            Text(
                "Income minus expenses — the only two things that actually change your net worth.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            if (monthlyNetChange.isEmpty()) EmptyHint("No data yet.") else SignedBarChart(
                monthlyNetChange.map { it.month to it.net }
            )

            SectionHeader("Compare months")
            if (availableMonths.size < 2) {
                EmptyHint("Log entries in at least two different months to compare them.")
            } else {
                MonthComparisonSection(viewModel, availableMonths, monthlyIncome, monthlyExpenses, monthlyNetChange)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NetWorthChip(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text("$${"%,.0f".format(amount)}", fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BreakdownList(items: List<CategorySpend>) {
    val total = items.sumOf { it.total }.coerceAtLeast(0.01)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { index, item ->
            val pct = item.total / total * 100
            val color = palette[index % palette.size]
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                    Text("$${"%.2f".format(item.total)} (${"%.0f".format(pct)}%)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(color = Color.LightGray.copy(alpha = 0.3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                        drawRoundRect(color = color, size = size.copy(width = size.width * (item.total / total).toFloat()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChart(data: List<Pair<String, Double>>, barColor: Color) {
    val maxVal = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.01)
    Column {
        Row(modifier = Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.forEach { (_, value) ->
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("$${value.toInt()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .weight(1f, fill = false)
                            .height(((value / maxVal) * 100).dp.coerceAtLeast(2.dp))
                            .background(barColor, shape = RoundedCornerShape(4.dp))
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.forEach { (month, _) ->
                Text(monthLabel(month), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SignedBarChart(data: List<Pair<String, Double>>) {
    val maxAbs = (data.maxOfOrNull { abs(it.second) } ?: 0.0).coerceAtLeast(0.01)
    Column {
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.forEach { (_, value) ->
                val isNegative = value < 0
                val barHeight = ((abs(value) / maxAbs) * 70).dp.coerceAtLeast(2.dp)
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (!isNegative) Box(modifier = Modifier.fillMaxWidth(0.6f).height(barHeight).background(Color(0xFF1E8E5A), RoundedCornerShape(4.dp)))
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Gray))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        if (isNegative) Box(modifier = Modifier.fillMaxWidth(0.6f).height(barHeight).background(Color(0xFFD64545), RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.forEach { (month, value) ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthLabel(month), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("$${"%.0f".format(value)}", style = MaterialTheme.typography.labelSmall, color = if (value < 0) Color(0xFFD64545) else Color(0xFF1E8E5A))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthComparisonSection(
    viewModel: PitakaViewModel,
    availableMonths: List<String>,
    monthlyIncome: List<com.pitaka.app.data.MonthlyAmount>,
    monthlyExpenses: List<com.pitaka.app.data.MonthlyAmount>,
    monthlyNetChange: List<com.pitaka.app.ui.MonthlyNetChange>
) {
    var monthA by remember(availableMonths) { mutableStateOf(availableMonths.getOrNull(availableMonths.size - 2) ?: availableMonths.first()) }
    var monthB by remember(availableMonths) { mutableStateOf(availableMonths.last()) }

    val breakdownA by viewModel.expenseBreakdownForMonth(monthA).collectAsState(initial = emptyList())
    val breakdownB by viewModel.expenseBreakdownForMonth(monthB).collectAsState(initial = emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthDropdown("Month A", availableMonths, monthA) { monthA = it }
            MonthDropdown("Month B", availableMonths, monthB) { monthB = it }
        }

        val incomeA = monthlyIncome.find { it.month == monthA }?.total ?: 0.0
        val incomeB = monthlyIncome.find { it.month == monthB }?.total ?: 0.0
        val expenseA = monthlyExpenses.find { it.month == monthA }?.total ?: 0.0
        val expenseB = monthlyExpenses.find { it.month == monthB }?.total ?: 0.0
        val netA = monthlyNetChange.find { it.month == monthA }?.net ?: (incomeA - expenseA)
        val netB = monthlyNetChange.find { it.month == monthB }?.net ?: (incomeB - expenseB)

        ComparisonRow("Earned", incomeA, incomeB, higherIsBetter = true)
        ComparisonRow("Spent", expenseA, expenseB, higherIsBetter = false)
        ComparisonRow("Net Change", netA, netB, higherIsBetter = true)

        Text("Category breakdown", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        val allCategories = (breakdownA.map { it.name } + breakdownB.map { it.name }).distinct().sorted()
        allCategories.forEach { category ->
            val a = breakdownA.find { it.name == category }?.total ?: 0.0
            val b = breakdownB.find { it.name == category }?.total ?: 0.0
            ComparisonRow(category, a, b, higherIsBetter = false, compact = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDropdown(label: String, months: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
        OutlinedTextField(
            value = monthLabel(selected) + " " + selected.substringBefore("-"),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            months.forEach { month ->
                DropdownMenuItem(text = { Text(monthLabel(month) + " " + month.substringBefore("-")) }, onClick = { onSelected(month); expanded = false })
            }
        }
    }
}

@Composable
private fun ComparisonRow(label: String, a: Double, b: Double, higherIsBetter: Boolean, compact: Boolean = false) {
    val delta = b - a
    val improved = if (higherIsBetter) delta >= 0 else delta <= 0
    val deltaColor = if (delta == 0.0) Color.Gray else if (improved) Color(0xFF1E8E5A) else Color(0xFFD64545)
    val fontSize = if (compact) 11.sp else 13.sp
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1.3f), fontSize = fontSize)
        Text("$${"%,.0f".format(a)}", modifier = Modifier.weight(1f), fontSize = fontSize, color = Color.Gray, textAlign = TextAlign.End)
        Text("$${"%,.0f".format(b)}", modifier = Modifier.weight(1f), fontSize = fontSize, color = Color.Gray, textAlign = TextAlign.End)
        Text(
            (if (delta >= 0) "+" else "") + "$${"%,.0f".format(delta)}",
            modifier = Modifier.weight(1f), fontSize = fontSize, color = deltaColor, fontWeight = FontWeight.Medium, textAlign = TextAlign.End
        )
    }
}
