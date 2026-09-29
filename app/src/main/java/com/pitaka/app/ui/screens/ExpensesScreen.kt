package com.pitaka.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitaka.app.data.ExpenseFunnel
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.EditEntryDialog
import com.pitaka.app.ui.components.AdaptiveText
import com.pitaka.app.ui.components.HealthBar
import com.pitaka.app.ui.components.dateFormat
import java.time.YearMonth
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(viewModel: PitakaViewModel,onOpenBudgetHistory:()->Unit,onOpenFunnel:(Long)->Unit){
    val expenses by viewModel.allExpenses.collectAsState(initial=emptyList())
    val funnels by viewModel.expenseFunnels.collectAsState(initial=emptyList())
    val allFunnels by viewModel.allExpenseFunnelsIncludingArchived.collectAsState(initial=emptyList())
    val categories by viewModel.expenseCategories.collectAsState(initial=emptyList())
    val pitakas by viewModel.pitakas.collectAsState(initial=emptyList())
    val goals by viewModel.goals.collectAsState(initial=emptyList())
    val budget by viewModel.currentMonthBudget.collectAsState(initial=null)
    val monthSpent by viewModel.currentMonthExpenseTotal.collectAsState(initial=0.0)
    var editing by remember { mutableStateOf<com.pitaka.app.data.LedgerEntry?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var expensesExpanded by remember { mutableStateOf(false) }
    val visibleFunnels=if(showArchived)allFunnels else funnels
    val month=YearMonth.now().toString()
    val thisMonth=expenses.filter{runCatching{java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString().startsWith(month)}.getOrDefault(false)}
    Scaffold(topBar={TopAppBar(title={Text("Spending")},actions={TextButton({showArchived=!showArchived}){Text(if(showArchived)"Active" else "Archived")};IconButton(onClick=onOpenBudgetHistory){Icon(Icons.Default.History,"Budget history")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)){
            Text("Monthly Expense Limit",style=MaterialTheme.typography.titleMedium)
            budget?.let { current ->
                val remaining=current.limit-monthSpent
                Card(Modifier.fillMaxWidth().padding(vertical=8.dp)){Column(Modifier.padding(14.dp)){
                    Text("Spent ${"%,.2f".format(monthSpent)} / ${"%,.2f".format(current.limit)}")
                    Text("Valid through ${YearMonth.now().atEndOfMonth()}", style=MaterialTheme.typography.bodySmall)
                    Text(if(remaining>=0) "${"%,.2f".format(remaining)} remaining" else "${"%,.2f".format(-remaining)} over limit",color=if(remaining>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if(current.limit>0) HealthBar((remaining/current.limit).toFloat().coerceIn(0f,1f))
                }}
            } ?: Text("No limit set. Use Budget history to configure one.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Expense Funnels",style=MaterialTheme.typography.titleMedium)
            if(visibleFunnels.isEmpty())Text("No funnels yet. Use the global + button to create one.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=10.dp)){
                items(visibleFunnels,key={it.id}){f->
                    val spent=expenses.filter{it.funnelId==f.id && it.funnelCurrency.equals(f.currency,true)}.sumOf{it.funnelAmount ?: it.amount};val remaining=f.limit-spent
                    ListItem(
                        headlineContent={AdaptiveText(f.name,style=MaterialTheme.typography.titleMedium)},
                        supportingContent={Column{AdaptiveText(if(f.isSystem) "Spent ${f.currency} ${"%,.2f".format(spent)} (no limit)" else "Spent ${f.currency} ${"%,.2f".format(spent)} / ${"%,.2f".format(f.limit)}",style=MaterialTheme.typography.bodyMedium,maxLines=2);if(f.limit>0)HealthBar(((remaining/f.limit).toFloat()).coerceIn(0f,1f))}},
                        trailingContent={AdaptiveText(if(f.isSystem) "Unlimited" else if(remaining>=0) "${f.currency} ${"%,.2f".format(remaining)} left" else "${f.currency} ${"%,.2f".format(-remaining)} over",modifier=Modifier.widthIn(max=96.dp),style=MaterialTheme.typography.labelMedium,color=if(!f.isSystem&&remaining<0)MaterialTheme.colorScheme.error else LocalContentColor.current,maxLines=2,minFontSize=9.sp)},
                        modifier=Modifier.fillMaxWidth().clickable{onOpenFunnel(f.id)}
                    )
                    HorizontalDivider()
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { expensesExpanded = !expensesExpanded }.padding(top=10.dp, bottom=6.dp),
                horizontalArrangement=Arrangement.SpaceBetween
            ) {
                Column {
                    Text("This Month's Expenses",style=MaterialTheme.typography.titleMedium)
                    Text("${thisMonth.size} transaction${if(thisMonth.size==1)"" else "s"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if(expensesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expensesExpanded) "Collapse expenses" else "Expand expenses")
            }
            if(expensesExpanded) {
                if(thisMonth.isEmpty())Text("No expenses logged this month.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max=240.dp),
                    verticalArrangement=Arrangement.spacedBy(2.dp)
                ) {
                    items(thisMonth,key={it.id}){entry->
                        var masked by remember(entry.id){mutableStateOf(false)}
                        Row(Modifier.fillMaxWidth().padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween){
                            Column(Modifier.weight(1f)){AdaptiveText(entry.name,style=MaterialTheme.typography.bodyMedium);AdaptiveText((entry.category?:"Uncategorized")+" • "+dateFormat.format(Date(entry.date)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            Row{AdaptiveText(if(masked)"••••••" else entry.currency+" "+"%,.2f".format(entry.amount),modifier=Modifier.widthIn(max=96.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.error,minFontSize=9.sp);IconButton({masked=!masked}){Icon(if(masked)Icons.Default.VisibilityOff else Icons.Default.Visibility,"Mask")};TextButton({editing=entry}){Text("Edit")}}
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    editing?.let { entry ->
        EditEntryDialog(
            entry=entry,
            pitakas=pitakas.filter{candidate->pitakas.none{it.parentPitakaId==candidate.id}},
            goals=goals,
            funnels=funnels,
            onSave={replacement->viewModel.replaceEntry(entry,replacement,onSuccess={editing=null})},
            onDismiss={editing=null}
        )
    }
}