package com.pitaka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.pitaka.app.data.displayLines
import com.pitaka.app.ui.PitakaViewModel
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
    onOpenPitaka: (Long) -> Unit
) {
    val pitakas by viewModel.pitakas.collectAsState(initial = emptyList())
    val roots = pitakas.filter { it.parentPitakaId == null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pitakas") },
                actions = {
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(roots, key = { it.id }) { pitaka ->
                    val childCount = pitakas.count { it.parentPitakaId == pitaka.id }
                    val accent = parseHexColor(pitaka.colorHex) ?: MaterialTheme.colorScheme.primary
                    ListItem(
                        headlineContent = { Text(pitaka.name, fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Column {
                                Text(viewModel.effectivePitakaBalances(pitaka.id, pitakas).displayLines())
                                if (childCount > 0) Text("$childCount sub-Pitaka${if (childCount == 1) "" else "s"}")
                            }
                        },
                        leadingContent = { Box(Modifier.width(6.dp).height(48.dp).background(accent)) },
                        trailingContent = { Text("Open") },
                        modifier = Modifier.fillMaxWidth().clickable { onOpenPitaka(pitaka.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
