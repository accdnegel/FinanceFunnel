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
import com.pitaka.app.ui.components.ExpenseCategoryField
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExpenseScreen(viewModel: PitakaViewModel,onDone:()->Unit){
    val pitakas by viewModel.pitakas.collectAsState(initial=emptyList())
    val funnels by viewModel.expenseFunnels.collectAsState(initial=emptyList())
    val categories by viewModel.expenseCategories.collectAsState(initial=emptyList())
    var selectedPitaka by remember{mutableStateOf<Pitaka?>(null)}
    var selectedFunnel by remember{mutableStateOf<ExpenseFunnel?>(null)}
    var name by remember{mutableStateOf("")}; var amount by remember{mutableStateOf("")}; var category by remember{mutableStateOf("")}
    var date by remember{mutableStateOf<Long?>(System.currentTimeMillis())};var currency by remember{mutableStateOf("PHP")}
    var pendingExpense by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(selectedPitaka?.id) { selectedPitaka?.let { currency = it.currency.uppercase() } }
    LaunchedEffect(pitakas){if(selectedPitaka==null)selectedPitaka=pitakas.firstOrNull()}
    Scaffold(topBar={TopAppBar(title={Text("New Expense")},navigationIcon={TextButton(onClick=onDone){Text("Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(pitakas.isNotEmpty()) CreateExpensePitakaDropdown(label="Charge to",pitakas=pitakas,selected=selectedPitaka,onSelected={selectedPitaka=it}) else Text("Create a Pitaka first.")
            FunnelDropdown(funnels,selectedFunnel){selectedFunnel=it}
            OutlinedTextField(name,{name=it},label={Text("Expense name")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(amount,{amount=it},label={Text("Amount (" + currency.uppercase() + ")")},modifier=Modifier.fillMaxWidth())
            ExpenseCategoryField(category,categories){category=it}
            CurrencyDropdown(currency, onSelected={currency=it})
            DatePickerButton("Transaction date",date){date=it}
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            Button(onClick={
                val a=amount.toDoubleOrNull()
                when {
                    selectedPitaka == null -> error = "Pick a Pitaka."
                    name.isBlank() -> error = "Enter an expense name."
                    a == null || a <= 0 -> error = "Enter a valid amount."
                    else -> {
                        error = null
                        pendingExpense = selectedFunnel != null && !currency.equals(selectedFunnel!!.currency, ignoreCase = true)
                        if (!pendingExpense) {
                            viewModel.recordExpense(selectedPitaka!!.id, name, a, category, selectedFunnel?.id, currency = currency, date = date ?: System.currentTimeMillis(), onSuccess = onDone)
                        }
                    }
                }
            },modifier=Modifier.fillMaxWidth()){Text("Save Expense")}
        }
    }

    if (pendingExpense) {
        val funnel = selectedFunnel
        val pitaka = selectedPitaka
        val sourceAmount = amount.toDoubleOrNull()
        if (funnel != null && pitaka != null && sourceAmount != null) {
            var funnelAmount by remember(funnel.id, sourceAmount, currency) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { pendingExpense = false },
                title = { Text("Different currencies") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This expense is ${currency.uppercase()}, while the selected funnel uses ${funnel.currency.uppercase()}.")
                        Text("Enter the amount that should be deducted from the funnel.")
                        OutlinedTextField(value=funnelAmount,onValueChange={funnelAmount=it},label={Text("Funnel amount (${funnel.currency.uppercase()})")},singleLine=true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val converted = funnelAmount.toDoubleOrNull()
                        if (converted == null || converted <= 0) error = "Enter a valid ${funnel.currency.uppercase()} funnel amount."
                        else {
                            viewModel.recordExpense(pitaka.id,name,sourceAmount,category,funnel.id,currency=currency,funnelAmount=converted,funnelCurrency=funnel.currency,date=date ?: System.currentTimeMillis(),onSuccess={ pendingExpense=false; onDone() })
                        }
                    }) { Text("Use ${funnel.currency.uppercase()}") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.recordExpense(pitaka.id,name,sourceAmount,category,funnel.id,currency=currency,funnelAmount=sourceAmount,funnelCurrency=currency,date=date ?: System.currentTimeMillis(),onSuccess={ pendingExpense=false; onDone() })
                        }) { Text("Keep ${currency.uppercase()}") }
                        TextButton(onClick={pendingExpense=false}) { Text("Cancel") }
                    }
                }
            )
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
@Composable private fun CreateExpensePitakaDropdown(label:String,pitakas:List<Pitaka>,selected:Pitaka?,onSelected:(Pitaka)->Unit){
    var open by remember{mutableStateOf(false)}
    ExposedDropdownMenuBox(open,{open=!open}){
        OutlinedTextField(value=selected?.name?:"Select Pitaka",onValueChange={},readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},modifier=Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open,{open=false}){pitakas.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={onSelected(p);open=false})}}
    }
}