package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.CardStylePicker
import com.pitaka.app.ui.components.DatePickerButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExpenseFunnelScreen(viewModel: PitakaViewModel,onDone:()->Unit){
    var name by remember{mutableStateOf("")};var limit by remember{mutableStateOf("")};var from by remember{mutableStateOf<Long?>(null)};var until by remember{mutableStateOf<Long?>(null)};var style by remember{mutableStateOf("solid")}
    Scaffold(topBar={TopAppBar(title={Text("New Expense Funnel")},navigationIcon={TextButton(onClick=onDone){Text("Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            OutlinedTextField(name,{name=it},label={Text("Funnel name")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(limit,{limit=it},label={Text("Limit (PHP by default)")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DatePickerButton("Start date",from){from=it};DatePickerButton("End date",until){until=it}}
            CardStylePicker(selected = style, solidColor = MaterialTheme.colorScheme.primary) { style = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick={val l=limit.toDoubleOrNull();if(name.isNotBlank()&&l!=null&&l>0){viewModel.createExpenseFunnel(name.trim(),l,from,until,null,style);onDone()}},modifier=Modifier.fillMaxWidth()){Text("Create Funnel")}
        }
    }
}