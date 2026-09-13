package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.HealthBar
import com.pitaka.app.ui.components.dateFormat
import com.pitaka.app.ui.theme.parseHexColor
import java.util.Date
import kotlin.math.abs

/* private enum class ViewMode { LIST, CARDS } */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitakasScreen(
    viewModel: PitakaViewModel,
    onAddPitaka: () -> Unit,
    onTransfer: () -> Unit,
    onRecurring: () -> Unit,
    onOpenPitaka: (Long) -> Unit
) {
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pitakas") },
                actions = {
                    IconButton(onClick = onRecurring) {
                        Icon(Icons.Default.Repeat, contentDescription = "Recurring rules")
                    }
                    IconButton(onClick = onTransfer) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer between Pitakas")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPitaka) {
                Icon(Icons.Default.Add, contentDescription = "Add Pitaka")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pitakas.isNotEmpty()) {
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
                    ) { Text("Cards") }
                }
            }

            if (pitakas.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No Pitakas yet. Tap + to add your first fund source.", color = Color.Gray)
                }
            } else when (viewMode) {
                ViewMode.LIST -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pitakas, key = { it.id }) { pitaka ->
                        PitakaCard(pitaka = pitaka, onClick = { onOpenPitaka(pitaka.id) })
                    }
                }
                ViewMode.CARDS -> PitakaCardCarousel(
                    pitakas = pitakas,
                    onOpenPitaka = onOpenPitaka,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PitakaCard(pitaka: Pitaka, onClick: () -> Unit) {
    val accentColor = parseHexColor(pitaka.colorHex) ?: Color(0xFF0278CF)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(accentColor))
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Text(pitaka.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${pitaka.currency} ${"%,.2f".format(pitaka.currentAmount)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Last updated: ${dateFormat.format(Date(pitaka.lastUpdated))}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PitakaCardCarousel(pitakas: List<Pitaka>, onOpenPitaka: (Long) -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { pitakas.size })

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 44.dp),
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxWidth().height(280.dp)
            ) { page ->
                val pitaka = pitakas[page]
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val distance = abs(pageOffset).coerceIn(0f, 1f)
                PitakaTcgCard(
                    pitaka = pitaka,
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = lerp(0.85f, 1f, 1f - distance)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(0.55f, 1f, 1f - distance)
                        }
                        .clickable { onOpenPitaka(pitaka.id) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            pitakas.forEachIndexed { index, _ ->
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
            "Swipe to browse  •  Tap a card to open it",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun PitakaTcgCard(pitaka: Pitaka, modifier: Modifier = Modifier) {
    val baseColor = parseHexColor(pitaka.colorHex) ?: Color(0xFF0278CF)
    val gradient = Brush.verticalGradient(
        listOf(lerpColor(baseColor, Color.White, 0.12f), lerpColor(baseColor, Color.Black, 0.35f))
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "PITAKA",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(pitaka.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${pitaka.currency} ${"%,.2f".format(pitaka.currentAmount)}",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Updated ${dateFormat.format(Date(pitaka.lastUpdated))}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}
