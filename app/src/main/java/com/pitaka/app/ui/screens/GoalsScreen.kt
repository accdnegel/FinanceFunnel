package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitaka.app.data.GoalType
import com.pitaka.app.data.GoalWithProgress
import com.pitaka.app.data.CurrencyBalances
import com.pitaka.app.data.displayLines
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.HealthBar
import com.pitaka.app.ui.components.AdaptiveText
import com.pitaka.app.ui.components.dateFormat
import com.pitaka.app.ui.theme.healthColor
import com.pitaka.app.ui.theme.parseHexColor
import java.util.Date

private enum class GoalFilter(val label: String) { ALL("All"), SAVINGS("Savings"), INVESTMENT("Investment") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: PitakaViewModel,
    onAddGoal: () -> Unit,
    onOpenGoal: (Long) -> Unit
) {
    val goals by viewModel.goals.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(GoalFilter.ALL) }

    val visibleGoals = when (filter) {
        GoalFilter.ALL -> goals
        GoalFilter.SAVINGS -> goals.filter { it.type == GoalType.SAVINGS }
        GoalFilter.INVESTMENT -> goals.filter { it.type == GoalType.INVESTMENT }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Goals") }) },

    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val selectedIndex = GoalFilter.entries.indexOf(filter)
            TabRow(selectedTabIndex = selectedIndex) {
                GoalFilter.entries.forEach { option ->
                    Tab(
                        selected = filter == option,
                        onClick = { filter = option },
                        text = { Text(option.label) }
                    )
                }
            }
            if (visibleGoals.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No goals yet. Tap + to set a savings or investment target.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(visibleGoals, key = { it.id }) { goal ->
                        GoalListItem(goal = goal, onClick = { onOpenGoal(goal.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalListItem(goal: GoalWithProgress, onClick: () -> Unit) {
    val isInvestment = goal.type == GoalType.INVESTMENT
    val accentColor = parseHexColor(goal.colorHex) ?: if (isInvestment) Color(0xFF056C3F) else Color(0xFF0278CF)
    val targets = CurrencyBalances.parse(goal.targetBalances)
    val progress = CurrencyBalances.parse(goal.currencyBalances)
    val ratios = targets.map { (code, target) -> (progress[code] ?: 0.0) / target.coerceAtLeast(0.000001) }
    val ratio = (ratios.minOrNull() ?: 0.0).toFloat().coerceIn(0f, 1f)
    val complete = targets.isNotEmpty() && ratios.all { it >= 1.0 }

    ListItem(
        headlineContent = { AdaptiveText(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Column {
                AdaptiveText(
                    progress.displayLines() + " / " + targets.displayLines(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(goal.targetDate?.let { "Target: ${dateFormat.format(Date(it))}" } ?: "No target date", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                HealthBar(ratio = ratio, color = healthColor(ratio))
            }
        },
        leadingContent = { Box(Modifier.width(6.dp).height(48.dp).background(accentColor)) },
        trailingContent = { AdaptiveText(if (complete) "Completed" else if (isInvestment) "Investment" else "Savings", style = MaterialTheme.typography.labelMedium, minFontSize = 9.sp) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}
