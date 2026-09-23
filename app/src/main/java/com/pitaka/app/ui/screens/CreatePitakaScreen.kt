package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.data.CurrencyBalances
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
    var showFirstChildConfirm by remember { mutableStateOf(false) }

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
            Text(
                if (cardStyle == "solid") "Solid color" else "Batik template color",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorSwatchPicker(selectedColor) { selectedColor = it }
            Text(
                if (cardStyle == "solid")
                    "Choose the color used for your solid card."
                else
                    "This color is used for the card's supporting color; the selected Batik template provides the artwork.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CardStylePicker(
                selected = cardStyle,
                solidColor = selectedColor?.let { com.pitaka.app.ui.theme.parseHexColor(it) }
                    ?: MaterialTheme.colorScheme.primary,
                onSelected = { cardStyle = it }
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick={
                if(name.isNotBlank()){
                    if(pitakaId==null){
                        val parent = parentId?.let { id -> pitakas.find { it.id == id } }
                        val stored = parent?.let { CurrencyBalances.parse(it.currencyBalances) }.orEmpty()
                        val hasExistingBalance = parent != null && (stored.values.any { it != 0.0 } || (stored.isEmpty() && parent.currentAmount != 0.0))
                        val firstChild = parent != null && pitakas.none { it.parentPitakaId == parent.id }
                        if(firstChild && hasExistingBalance) showFirstChildConfirm = true
                        else {
                            viewModel.createPitaka(name,startingBalance.toDoubleOrNull()?:0.0,currency,selectedColor,parentId,cardStyle)
                            onDone()
                        }
                    } else {
                        viewModel.updatePitakaMeta(pitakaId,name,currency,selectedColor,cardStyle)
                        onDone()
                    }
                }
            },modifier=Modifier.fillMaxWidth()){Text(if(pitakaId==null)"Create Pitaka" else "Save Changes")}
        }
    }
    if (showFirstChildConfirm) {
        val parent = parentId?.let { id -> pitakas.find { it.id == id } }
        val balances = parent?.let { CurrencyBalances.parse(it.currencyBalances) }.orEmpty()
        AlertDialog(
            onDismissRequest = { showFirstChildConfirm = false },
            title = { Text("Convert this Pitaka into a parent?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will create the first sub-Pitaka under the selected Pitaka.")
                    if (balances.isNotEmpty()) {
                        Text("Existing balance: " + balances.entries.joinToString(" • ") { it.key + " " + "%,.2f".format(it.value) })
                    }
                    Text("The existing balance will be preserved in the new sub-Pitaka. The parent becomes a container and its stored balance is set to zero, preventing double-counting.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFirstChildConfirm = false
                    viewModel.createPitaka(name,startingBalance.toDoubleOrNull()?:0.0,currency,selectedColor,parentId,cardStyle)
                    onDone()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showFirstChildConfirm = false }) { Text("Cancel") } }
        )
    }

}