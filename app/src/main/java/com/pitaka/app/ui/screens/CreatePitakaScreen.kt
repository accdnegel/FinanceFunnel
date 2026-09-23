package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.pitaka.app.data.CurrencyBalances
import com.pitaka.app.data.displayLines
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.CardStylePicker
import com.pitaka.app.ui.components.ColorSwatchPicker
import com.pitaka.app.ui.components.CurrencyDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePitakaScreen(viewModel: PitakaViewModel, pitakaId: Long? = null, parentPitakaId: Long? = null, onDone: () -> Unit) {
    val pitakas by viewModel.pitakas.collectAsState(initial=emptyList())
    var name by remember { mutableStateOf("") }
    var startingBalance by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("PHP") }
    var selectedColor by remember { mutableStateOf<String?>(null) }
    var cardStyle by remember { mutableStateOf("solid") }
    var parentId by remember { mutableStateOf(parentPitakaId) }
    var loaded by remember { mutableStateOf(pitakaId == null) }
    var showFirstChildWarning by remember { mutableStateOf(false) }
    var pendingParentId by remember { mutableStateOf<Long?>(null) }
    var pendingStartingBalance by remember { mutableStateOf(0.0) }
    var pendingCurrency by remember { mutableStateOf("PHP") }
    var pendingParentBalances by remember { mutableStateOf("") }

    LaunchedEffect(pitakaId) {
        if (pitakaId != null) viewModel.getPitaka(pitakaId)?.let { p ->
            name=p.name; currency=p.currency; selectedColor=p.colorHex; cardStyle=p.cardStyle; parentId=p.parentPitakaId
        }
        loaded=true
    }
    if(!loaded) return

    Scaffold(topBar={TopAppBar(title={Text(if(pitakaId==null) "New Pitaka" else "Edit Pitaka")})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            OutlinedTextField(name,{name=it},label={Text("Pitaka Name")},modifier=Modifier.fillMaxWidth())
            if(pitakaId==null) OutlinedTextField(startingBalance,{startingBalance=it},label={Text("Starting Balance")},modifier=Modifier.fillMaxWidth())
            CurrencyDropdown(currency,{currency=it})
            if(pitakas.isNotEmpty()){
                var expanded by remember{mutableStateOf(false)}
                ExposedDropdownMenuBox(expanded,{expanded=!expanded}){
                    OutlinedTextField(value=pitakas.find{it.id==parentId}?.name ?: "No parent (top level)",onValueChange={},readOnly=true,label={Text("Parent Pitaka (optional)")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded,{expanded=false}){
                        DropdownMenuItem(text={Text("No parent (top level)")},onClick={parentId=null;expanded=false})
                        pitakas.filter{it.id!=pitakaId}.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={parentId=p.id;expanded=false})}
                    }
                }
            }
            Text("Appearance",style=MaterialTheme.typography.titleMedium)
            Text("Card color",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ColorSwatchPicker(selectedColor){selectedColor=it}
            CardStylePicker(cardStyle){cardStyle=it}
            Spacer(Modifier.height(8.dp))
            Button(onClick={
                if(name.isNotBlank()){
                    if(pitakaId==null) {
                        val amount = startingBalance.toDoubleOrNull() ?: 0.0
                        val parent = parentId?.let { id -> pitakas.firstOrNull { it.id == id } }
                        val firstChild = parent != null && pitakas.none { it.parentPitakaId == parent.id }
                        val parentBalances = parent?.let { CurrencyBalances.parse(it.currencyBalances) } ?: emptyMap()
                        if (firstChild && parentBalances.values.any { kotlin.math.abs(it) > 0.0000001 }) {
                            pendingParentId = parent.id
                            pendingStartingBalance = amount
                            pendingCurrency = currency
                            pendingParentBalances = parentBalances.displayLines()
                            showFirstChildWarning = true
                        } else {
                            viewModel.createPitaka(name, amount, currency, selectedColor, parentId, cardStyle)
                            onDone()
                        }
                    } else {
                        viewModel.updatePitakaMeta(pitakaId,name,currency,selectedColor,cardStyle)
                        onDone()
                    }
                }
            },modifier=Modifier.fillMaxWidth()){Text(if(pitakaId==null)"Create Pitaka" else "Save Changes")}

            if (showFirstChildWarning) {
                AlertDialog(
                    onDismissRequest = { showFirstChildWarning = false },
                    title = { Text("Convert this Pitaka into a parent?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("This Pitaka already has money in it. Adding its first sub-Pitaka will move the existing balance into the new sub-Pitaka so the parent total does not lose money.")
                            Text("Existing parent balance", style = MaterialTheme.typography.labelMedium)
                            Text(pendingParentBalances, style = MaterialTheme.typography.bodyMedium)
                            Text("New sub-Pitaka starting balance", style = MaterialTheme.typography.labelMedium)
                            Text(pendingCurrency.uppercase() + " " + "%,.2f".format(pendingStartingBalance))
                            Text("The resulting sub-Pitaka will contain the existing balance plus this starting amount.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showFirstChildWarning = false
                            viewModel.createPitaka(name, pendingStartingBalance, pendingCurrency, selectedColor, pendingParentId, cardStyle)
                            onDone()
                        }) { Text("Continue") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFirstChildWarning = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}