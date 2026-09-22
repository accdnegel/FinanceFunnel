package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.dateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunnelDetailScreen(viewModel: PitakaViewModel,funnelId:Long,onBack:()->Unit){
    val funnels by viewModel.expenseFunnels.collectAsState(initial=emptyList())
    val funnel=funnels.find{it.id==funnelId}
    val entries by viewModel.observeExpensesForFunnel(funnelId).collectAsState(initial=emptyList())
    Scaffold(topBar={TopAppBar(title={Text(funnel?.name?:"Expense Funnel")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)){
            funnel?.let{Text("Limit: "+it.currency+" "+"%,.2f".format(it.limit),style=MaterialTheme.typography.titleMedium)}
            Text("Full history",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(vertical=12.dp))
            LazyColumn{items(entries,key={it.id}){e->Column(Modifier.fillMaxWidth().padding(vertical=7.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(e.name);Text(e.currency+" "+"%,.2f".format(e.amount))};Text((e.category?:"Uncategorized Expense")+" • "+dateFormat.format(Date(e.date)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);HorizontalDivider()}}}
        }
    }
}