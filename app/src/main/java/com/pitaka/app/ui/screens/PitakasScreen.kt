@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pitaka.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.pitaka.app.data.Pitaka
import com.pitaka.app.data.CurrencyBalances
import com.pitaka.app.data.displayLines
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.BatikCardSurface
import com.pitaka.app.ui.theme.parseHexColor

/**
 * Shows only top-level Pitakas.
 *
 * Children are deliberately not rendered here. A parent Pitaka acts as the
 * entry point to its own hierarchy; opening it reveals its direct children.
 * This keeps the main list compact and makes the parent/child relationship
 * visually and behaviorally clear.
 */
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
    val roots = pitakas.filter { it.parentPitakaId == null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pitakas") },
                actions = {
                    IconButton(onClick = onRecurring) { Icon(Icons.Default.Repeat, "Recurring") }
                    IconButton(onClick = onTransfer) { Icon(Icons.Default.SwapHoriz, "Transfer") }
                }
            )
        }
    ) { padding ->
        if (roots.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text("No Pitakas yet. Tap + to add your first fund source.")
            }
        } else {
            val pager = rememberPagerState(pageCount = { roots.size })
            VerticalPager(
                state = pager,
                contentPadding = PaddingValues(vertical = 28.dp),
                pageSpacing = 14.dp,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) { page ->
                PitakaBatikCard(
                    p = roots[page],
                    onOpen = onOpenPitaka
                )
            }
        }
    }
}

@Composable
private fun PitakaBatikCard(
    p: Pitaka,
    onOpen: (Long) -> Unit,
    compact: Boolean = false
) {
    val base = parseHexColor(p.colorHex) ?: Color(0xFF0278CF)

    BatikCardSurface(
        p.cardStyle,
        base,
        Modifier
            .fillMaxWidth()
            .height(if (compact) 125.dp else 170.dp)
            .clickable { onOpen(p.id) }
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "PITAKA",
                color = Color.White.copy(alpha = .8f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                p.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = if (compact) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                CurrencyBalances.parse(p.currencyBalances).displayLines(),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
