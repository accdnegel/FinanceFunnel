package com.pitaka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.LedgerEntry
import com.pitaka.app.data.Pitaka

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(entry:LedgerEntry,pitakas:List< Pitaka>,categories:List<String>,onSave:(String,Double,String?,Long?)->Unit,onDismiss:()->Unit){
    var name by remember{mutableStateOf(entry.name)};var amount by remember{mutableStateOf(entry.amount.toString())};var category by remember{mutableStateOf(entry.category?:"Uncategorized Expense")};var pitaka by remember{mutableStateOf(pitakas.find{it.id==entry.pitakaId})};var open by remember{mutableStateOf(false)};var categoryOpen by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Edit Expense")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(name,{name=it},label={Text("Name")})
        OutlinedTextField(amount,{amount=it},label={Text("Amount")})
        ExposedDropdownMenuBox(expanded=categoryOpen && categories.isNotEmpty(),onExpandedChange={categoryOpen=it}){OutlinedTextField(value=category,onValueChange={category=it},label={Text("Category")},singleLine=true,modifier=Modifier.menuAnchor().fillMaxWidth());val suggestions=categories.filter{category.trim().isBlank()||it.contains(category.trim(),ignoreCase=true)}.take(8);ExposedDropdownMenu(expanded=categoryOpen&&suggestions.isNotEmpty(),onDismissRequest={categoryOpen=false}){suggestions.forEach{suggestion->DropdownMenuItem(text={Text(suggestion)},onClick={category=suggestion;categoryOpen=false})}}}
        ExposedDropdownMenuBox(open,{open=!open}){
            OutlinedTextField(value=pitaka?.name?:"Select Pitaka",onValueChange={},readOnly=true,label={Text("Charged from")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},modifier=Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(open,{open=false}){pitakas.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={pitaka=p;open=false})}}
        }
    }},confirmButton={TextButton(onClick={amount.toDoubleOrNull()?.let{onSave(name,it,category,pitaka?.id)}}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}