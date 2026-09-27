package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.dateFormat
import com.pitaka.app.ui.components.EditEntryDialog
import com.pitaka.app.data.LedgerEntry
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunnelDetailScreen(viewModel: PitakaViewModel,funnelId:Long,onBack:()->Unit){
    val activeFunnels by viewModel.expenseFunnels.collectAsState(initial=emptyList())
    val funnels by viewModel.allExpenseFunnelsIncludingArchived.collectAsState(initial=emptyList())
    val funnel=funnels.find{it.id==funnelId}
    val entries by viewModel.observeExpensesForFunnel(funnelId).collectAsState(initial=emptyList())
    val pitakas by viewModel.pitakas.collectAsState(initial=emptyList())
    val goals by viewModel.goals.collectAsState(initial=emptyList())
    val leafPitakas=pitakas.filter{candidate->pitakas.none{it.parentPitakaId==candidate.id}}
    var showDelete by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    Scaffold(topBar={TopAppBar(title={Text(funnel?.name?:"Expense Funnel")},navigationIcon={TextButton(onClick=onBack){Text("Back")}},actions={if(funnel?.isSystem==false){if(funnel.archivedAt==null)IconButton({viewModel.archiveExpenseFunnel(funnelId,onBack)}){Icon(Icons.Default.Archive,"Archive")}else IconButton({viewModel.restoreExpenseFunnel(funnelId)}){Icon(Icons.Default.Unarchive,"Restore")};IconButton({showDelete=true}){Icon(Icons.Default.Delete,"Delete")}}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)){
            funnel?.let{current->
                val spent=entries.filter{(it.funnelCurrency?:it.currency).equals(current.currency,true)}.sumOf{it.funnelAmount?:it.amount}
                val remaining=current.limit-spent
                Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(if(current.isSystem)"Unlimited" else "Limit: ${current.currency} ${"%,.2f".format(current.limit)}",style=MaterialTheme.typography.titleMedium)
                    Text("Spent: ${current.currency} ${"%,.2f".format(spent)}")
                    if(!current.isSystem)Text(if(remaining>=0)"Remaining: ${current.currency} ${"%,.2f".format(remaining)}" else "Over limit: ${current.currency} ${"%,.2f".format(-remaining)}",color=if(remaining>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text("Valid: ${current.validFrom?.let{dateFormat.format(Date(it))}?:"No start"} – ${current.validUntil?.let{dateFormat.format(Date(it))}?:"No end"}",style=MaterialTheme.typography.bodySmall)
                }}
            }
            Text("Full history",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(vertical=12.dp))
            LazyColumn{items(entries,key={it.id}){e->Column(Modifier.fillMaxWidth().padding(vertical=7.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(e.name);Text(e.currency+" "+"%,.2f".format(e.amount))};Row{IconButton({editingEntry=e}){Icon(Icons.Default.Edit,"Edit")};IconButton({viewModel.deleteEntry(e)}){Icon(Icons.Default.Delete,"Delete")}}};Text((e.category?:"Uncategorized")+" • "+dateFormat.format(Date(e.date)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);HorizontalDivider()}}}
        }
    }
    if(showDelete&&funnel!=null) com.pitaka.app.ui.components.ConfirmDeleteDialog("Delete this Expense Funnel?","Its expenses will be preserved and moved to General Expenses.",{viewModel.deleteExpenseFunnel(funnel,onSuccess=onBack);showDelete=false},{showDelete=false})
    editingEntry?.let { entry ->
        EditEntryDialog(entry,leafPitakas,goals,activeFunnels,{replacement->viewModel.replaceEntry(entry,replacement);editingEntry=null},{editingEntry=null})
    }
}