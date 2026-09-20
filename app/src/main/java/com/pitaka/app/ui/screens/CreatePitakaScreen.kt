package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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

    LaunchedEffect(pitakaId) {
        if (pitakaId != null) viewModel.getPitaka(pitakaId)?.let { p ->
            name=p.name; currency=p.currency; selectedColor=p.colorHex; cardStyle=p.cardStyle; parentId=p.parentPitakaId
        }
        loaded=true
    }
    if(!loaded) return

    Scaffold(topBar={TopAppBar(title={Text(if(pitakaId==null) "New Pitaka" else "Edit Pitaka")})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
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
            Text("Card color")
            ColorSwatchPicker(selectedColor){selectedColor=it}
            CardStylePicker(cardStyle){cardStyle=it}
            Spacer(Modifier.weight(1f))
            Button(onClick={
                if(name.isNotBlank()){
                    if(pitakaId==null) viewModel.createPitaka(name,startingBalance.toDoubleOrNull()?:0.0,currency,selectedColor,parentId,cardStyle)
                    else viewModel.updatePitakaMeta(pitakaId,name,currency,selectedColor,cardStyle)
                    onDone()
                }
            },modifier=Modifier.fillMaxWidth()){Text(if(pitakaId==null)"Create Pitaka" else "Save Changes")}
        }
    }
}