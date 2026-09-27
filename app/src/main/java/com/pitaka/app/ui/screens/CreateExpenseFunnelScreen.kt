package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.DatePickerButton
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExpenseFunnelScreen(viewModel: PitakaViewModel,onDone:()->Unit){
    val month = remember { YearMonth.now() }
    var name by remember{mutableStateOf("")};var limit by remember{mutableStateOf("")};var from by remember{mutableStateOf<Long?>(month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())};var until by remember{mutableStateOf<Long?>(month.atEndOfMonth().atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())};var validationError by remember{mutableStateOf<String?>(null)}
    Scaffold(topBar={TopAppBar(title={Text("New Expense Funnel")},navigationIcon={TextButton(onClick=onDone){Text("Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(name,{name=it},label={Text("Funnel name")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(limit,{limit=it},label={Text("Limit (PHP by default)")},modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DatePickerButton("Start date",from,{from=it},{from=null});DatePickerButton("End date",until,{until=it},{until=null})}
            }
            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick={
                val amount=limit.toDoubleOrNull()
                when {
                    name.isBlank() -> validationError="Enter a funnel name."
                    amount==null || amount<=0 -> validationError="Enter a valid limit greater than zero."
                    from!=null && until!=null && from!!>until!! -> validationError="Start date must not be after end date."
                    else -> { validationError=null; viewModel.createExpenseFunnel(name.trim(),amount,from,until,null,"solid",onSuccess=onDone) }
                }
            },modifier=Modifier.fillMaxWidth()){Text("Create Funnel")}
        }
    }
}