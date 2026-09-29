package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.AdaptiveText
import com.pitaka.app.ui.components.dateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(viewModel: PitakaViewModel, category: String, onBack: () -> Unit) {
    val entries by viewModel.observeExpensesForCategory(category).collectAsState(initial=emptyList())
    Scaffold(topBar={TopAppBar(title={AdaptiveText(category,style=MaterialTheme.typography.titleLarge)},navigationIcon={TextButton(onClick=onBack){Text("Back")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            item{Text("Expense history",style=MaterialTheme.typography.titleMedium)}
            items(entries,key={it.id}){e->Column(Modifier.fillMaxWidth().padding(vertical=6.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){AdaptiveText(e.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);AdaptiveText(e.currency+" "+"%,.2f".format(e.amount),modifier=Modifier.widthIn(max=110.dp),style=MaterialTheme.typography.bodyMedium)};Text(dateFormat.format(Date(e.date)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);HorizontalDivider()}}
        }
    }
}