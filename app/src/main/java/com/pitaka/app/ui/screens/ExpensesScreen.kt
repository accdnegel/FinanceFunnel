package com.pitaka.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.ExpenseFunnel
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.HealthBar
import com.pitaka.app.ui.components.BatikCardSurface
import com.pitaka.app.ui.components.dateFormat
import java.time.YearMonth
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(viewModel: PitakaViewModel,onOpenBudgetHistory:()->Unit,onOpenFunnel:(Long)->Unit){
    val expenses by viewModel.allExpenses.collectAsState(initial=emptyList())
    val funnels by viewModel.expenseFunnels.collectAsState(initial=emptyList())
    val pitakas by viewModel.pitakas.collectAsState(initial=emptyList())
    var editing by remember { mutableStateOf<com.pitaka.app.data.LedgerEntry?>(null) }
    val month=YearMonth.now().toString()
    val thisMonth=expenses.filter{java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString().startsWith(month)}
    Scaffold(topBar={TopAppBar(title={Text("Spending")},actions={IconButton(onClick=onOpenBudgetHistory){Icon(Icons.Default.History,"Budget history")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)){
            Text("Expense Funnels",style=MaterialTheme.typography.titleMedium)
            if(funnels.isEmpty())Text("No funnels yet. Use the global + button to create one.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=10.dp)){
                items(funnels,key={it.id}){f->
                    val spent=expenses.filter{it.funnelId==f.id}.sumOf{it.amount};val remaining=f.limit-spent
                    BatikCardSurface(f.cardStyle,com.pitaka.app.ui.theme.parseHexColor(f.colorHex) ?: MaterialTheme.colorScheme.primary,Modifier.fillMaxWidth().clickable{onOpenFunnel(f.id)}){
                        Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(f.name,style=MaterialTheme.typography.titleMedium);Text(f.currency+" "+"%,.2f".format(remaining)+" left")};Text("Spent "+f.currency+" "+"%,.2f".format(spent)+" / "+"%,.2f".format(f.limit),style=MaterialTheme.typography.bodySmall);if(f.limit>0)HealthBar(((remaining/f.limit).toFloat()).coerceIn(0f,1f));Text("Tap for full history",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}}
                }
            }
            HorizontalDivider()
            Text("This Month's Expenses",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=10.dp))
            if(thisMonth.isEmpty())Text("No expenses logged this month.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            thisMonth.take(10).forEach{entry->
                var masked by remember(entry.id){mutableStateOf(false)}
                Row(Modifier.fillMaxWidth().padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){Text(entry.name);Text((entry.category?:"Uncategorized Expense")+" • "+dateFormat.format(Date(entry.date)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Row{Text(if(masked)"••••••" else entry.currency+" "+"%,.2f".format(entry.amount),color=MaterialTheme.colorScheme.error);IconButton({masked=!masked}){Icon(if(masked)Icons.Default.VisibilityOff else Icons.Default.Visibility,"Mask")};TextButton({editing=entry}){Text("Edit")}}
                }
            }
        }
    }
    editing?.let { entry ->
        EditExpenseDialog(entry,pitakas,{name,amount,category,pitakaId->
            viewModel.updateEntry(entry,name,amount,category,pitakaId);editing=null
        },{editing=null})
    }
}