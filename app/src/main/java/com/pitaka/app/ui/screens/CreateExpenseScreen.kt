package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.ExpenseFunnel
import com.pitaka.app.data.Pitaka
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.components.DatePickerButton
import com.pitaka.app.ui.components.CurrencyDropdown
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExpenseScreen(viewModel: PitakaViewModel,onDone:()->Unit){
    val pitakas by viewModel.pitakas.collectAsState(initial=emptyList())
    val funnels by viewModel.expenseFunnels.collectAsState(initial=emptyList())
    var selectedPitaka by remember{mutableStateOf<Pitaka?>(null)}
    var selectedFunnel by remember{mutableStateOf<ExpenseFunnel?>(null)}
    var name by remember{mutableStateOf("")}; var amount by remember{mutableStateOf("")}; var category by remember{mutableStateOf("")}
    var date by remember{mutableStateOf<Long?>(System.currentTimeMillis())};var currency by remember{mutableStateOf("PHP")}
    LaunchedEffect(pitakas){if(selectedPitaka==null)selectedPitaka=pitakas.firstOrNull()}
    Scaffold(topBar={TopAppBar(title={Text("New Expense")},navigationIcon={TextButton(onClick=onDone){Text("Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(pitakas.isNotEmpty()) ExpensePitakaDropdown("Charge to",pitakas,selectedPitaka){selectedPitaka=it} else Text("Create a Pitaka first.")
            FunnelDropdown(funnels,selectedFunnel){selectedFunnel=it}
            OutlinedTextField(name,{name=it},label={Text("Expense name")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(amount,{amount=it},label={Text("Amount (PHP by default)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(category,{category=it},label={Text("Category (optional)")},modifier=Modifier.fillMaxWidth())
            CurrencyDropdown(currency){currency=it}
            DatePickerButton("Transaction date",date){date=it}
            Spacer(Modifier.weight(1f))
            Button(onClick={val a=amount.toDoubleOrNull();if(name.isNotBlank()&&a!=null&&a>0&&selectedPitaka!=null)viewModel.recordExpense(selectedPitaka!!.id,name,a,category,selectedFunnel?.id,currency,date?:System.currentTimeMillis());onDone()},modifier=Modifier.fillMaxWidth()){Text("Save Expense")}
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FunnelDropdown(funnels:List<ExpenseFunnel>,selected:ExpenseFunnel?,onSelected:(ExpenseFunnel?)->Unit){
    var open by remember{mutableStateOf(false)}
    ExposedDropdownMenuBox(open,{open=!open}){
        OutlinedTextField(value=selected?.name?:"Unclassified Expense",onValueChange={},readOnly=true,label={Text("Expense funnel")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},modifier=Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open,{open=false}){DropdownMenuItem(text={Text("Unclassified Expense")},onClick={onSelected(null);open=false});funnels.filter{!it.isSystem}.forEach{f->DropdownMenuItem(text={Text(f.name)},onClick={onSelected(f);open=false})}}
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ExpensePitakaDropdown(label:String,pitakas:List<Pitaka>,selected:Pitaka?,onSelected:(Pitaka)->Unit){
    var open by remember{mutableStateOf(false)}
    ExposedDropdownMenuBox(open,{open=!open}){
        OutlinedTextField(value=selected?.name?:"Select Pitaka",onValueChange={},readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},modifier=Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open,{open=false}){pitakas.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={onSelected(p);open=false})}}
    }
}