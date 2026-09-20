@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.pitaka.app.data.GoalType
import com.pitaka.app.data.GoalWithProgress
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.HealthBar
import com.pitaka.app.ui.components.dateFormat
import com.pitaka.app.ui.theme.healthColor
import com.pitaka.app.ui.theme.parseHexColor
import java.util.Date
import kotlin.math.abs

private enum class GoalFilter(val label: String) { ALL("All"), SAVINGS("Savings"), INVESTMENT("Investment") }
private enum class ViewMode { LIST, CARDS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: PitakaViewModel,
    onAddGoal: () -> Unit,
    onOpenGoal: (Long) -> Unit
) {
    val goals by viewModel.goals.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(GoalFilter.ALL) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(
                    selected = viewMode == ViewMode.LIST,
                    onClick = { viewMode = ViewMode.LIST },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("List") }
                SegmentedButton(
                    selected = viewMode == ViewMode.CARDS,
                    onClick = { viewMode = ViewMode.CARDS },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Piggy Banks") }
            }

            if (visibleGoals.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No goals yet. Tap + to set a savings or investment target.", color = Color.Gray)
                }
            } else when (viewMode) {
                ViewMode.LIST -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleGoals, key = { it.id }) { goal ->
                        GoalListCard(goal = goal, onClick = { onOpenGoal(goal.id) })
                    }
                }
                ViewMode.CARDS -> GoalCardCarousel(
                    goals = visibleGoals,
                    onOpenGoal = onOpenGoal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GoalListCard(goal: GoalWithProgress, onClick: () -> Unit) {
    val isInvestment = goal.type == GoalType.INVESTMENT
    val accentColor = parseHexColor(goal.colorHex) ?: if (isInvestment) Color(0xFF056C3F) else Color(0xFF0278CF)
    val ratio = (goal.progress / goal.targetAmount.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, elevation = CardDefaults.cardElevation(2.dp)) {
        Row {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(accentColor))
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(goal.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    AssistChip(onClick = {}, label = { Text(if (isInvestment) "Investment" else "Savings") })
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "$${"%,.2f".format(goal.progress)} / $${"%,.2f".format(goal.targetAmount)}",
                    fontWeight = FontWeight.SemiBold
                )
                Text("Target: ${dateFormat.format(Date(goal.targetDate))}", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                HealthBar(ratio = ratio, color = healthColor(ratio))
            }
        }
    }
}

@Composable
private fun GoalCardCarousel(goals: List<GoalWithProgress>, onOpenGoal: (Long) -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { goals.size })

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            VerticalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 44.dp),
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxWidth().height(320.dp)
            ) { page ->
                val goal = goals[page]
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val distance = abs(pageOffset).coerceIn(0f, 1f)
                PiggyBankCard(
                    goal = goal,
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = lerp(0.85f, 1f, 1f - distance)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(0.55f, 1f, 1f - distance)
                        }
                        .clickable { onOpenGoal(goal.id) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            goals.forEachIndexed { index, _ ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.LightGray)
                )
            }
        }
        Text(
            "Scroll vertically  •  Tap a card to open it",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun PiggyBankCard(goal: GoalWithProgress, modifier: Modifier = Modifier) {
    val isInvestment = goal.type == GoalType.INVESTMENT
    val baseColor = parseHexColor(goal.colorHex) ?: if (isInvestment) Color(0xFF056C3F) else Color(0xFF0278CF)
    val gradient = Brush.verticalGradient(listOf(lerpColor(baseColor, Color.White, 0.12f), lerpColor(baseColor, Color.Black, 0.35f)))
    val ratio = (goal.progress / goal.targetAmount.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                if (isInvestment) "INVESTMENT" else "SAVINGS",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(goal.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "$${"%,.0f".format(goal.progress)} / $${"%,.0f".format(goal.targetAmount)}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            HealthBar(ratio = ratio, color = healthColor(ratio))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Target: ${dateFormat.format(Date(goal.targetDate))}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}
