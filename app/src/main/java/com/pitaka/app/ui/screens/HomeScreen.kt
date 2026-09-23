package com.pitaka.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitaka.app.data.CategorySpend
import com.pitaka.app.data.Pitaka
import com.pitaka.app.data.displayLines
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.util.buildLedgerCsv
import com.pitaka.app.util.exportAndShareCsv
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

private val chartColors = listOf(Color(0xFFE06A00),Color(0xFF0278CF),Color(0xFF056C3F),Color(0xFF8E44AD),Color(0xFFD4537E),Color(0xFFBA7517))
private fun monthLabel(key:String)=try { YearMonth.parse(key).month.getDisplayName(TextStyle.SHORT,Locale.getDefault())+" "+key.substringBefore("-") } catch(_:Exception){key}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PitakaViewModel, onOpenCurrencySettings: () -> Unit, onOpenCategory: (String) -> Unit = {}, onOpenPitaka: (Long) -> Unit = {}) {
    val pitakas by viewModel.pitakas.collectAsState(initial=emptyList())
    val rootPitakas by viewModel.rootPitakas.collectAsState(initial=emptyList())
    val goals by viewModel.goals.collectAsState(initial=emptyList())
    val settings by viewModel.currencySettings.collectAsState(initial=null)
    val liquid by viewModel.totalLiquid.collectAsState(initial=0.0)
    val savings by viewModel.totalSavingsProgress.collectAsState(initial=0.0)
    val investments by viewModel.totalInvestmentProgress.collectAsState(initial=0.0)
    val netWorth by viewModel.totalNetWorth.collectAsState(initial=0.0)
    val income by viewModel.monthlyIncome.collectAsState(initial=emptyList())
    val expenses by viewModel.monthlyExpenses.collectAsState(initial=emptyList())
    val months by viewModel.availableMonths.collectAsState(initial=emptyList())
    val allExpenses by viewModel.allExpenses.collectAsState(initial=emptyList())
    val allEntries by viewModel.allEntries.collectAsState(initial=emptyList())
    var selectedMonth by remember(months) { mutableStateOf(months.lastOrNull() ?: YearMonth.now().toString()) }
    var expandedNet by remember { mutableStateOf<String?>(null) }
    var expandedFlow by remember { mutableStateOf(false) }
    val breakdown by viewModel.expenseBreakdownForMonth(selectedMonth).collectAsState(initial=emptyList())
    val selectedNetWorth by viewModel.netWorthForMonth(selectedMonth).collectAsState(initial=netWorth)
    val scope=rememberCoroutineScope()
    val context=androidx.compose.ui.platform.LocalContext.current
    val currency=settings?.baseCurrency ?: "PHP"

    Scaffold(topBar={TopAppBar(title={Text("Pitaka")},actions={
        IconButton(onClick=onOpenCurrencySettings){Icon(Icons.Default.CurrencyExchange,null)}
        IconButton(onClick={ { scope.launch { val csv=buildLedgerCsv(viewModel.getAllEntriesOnce(),pitakas,goals.associate{it.id to it.name}); exportAndShareCsv(context,csv) } } }){Icon(Icons.Default.Share,null)}
    })}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)){
            MonthSelector(months.ifEmpty{listOf(selectedMonth)},selectedMonth){selectedMonth=it}
            SectionTitle("Total Net Worth")
            Card(shape=RoundedCornerShape(20.dp)){
                Column(Modifier.padding(16.dp)){
                    Text(currency + " " + "%,.2f".format(selectedNetWorth),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text("Selected month: " + monthLabel(selectedMonth),color=Color.Gray,style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    NetRow("Liquid",liquid,currency,expandedNet=="Liquid"){expandedNet=if(expandedNet=="Liquid")null else "Liquid"}
                    if(expandedNet=="Liquid") rootPitakas.forEach { p ->
                        val balances = viewModel.effectivePitakaBalances(p.id, pitakas)
                        AssetRow(p.name, balances.displayLines(), Modifier.clickable { onOpenPitaka(p.id) })
                    }
                    NetRow("Savings",savings,currency,expandedNet=="Savings"){expandedNet=if(expandedNet=="Savings")null else "Savings"}
                    if(expandedNet=="Savings") goals.filter{it.type==com.pitaka.app.data.GoalType.SAVINGS}.forEach{g->AssetRow(g.name,g.progress.toString()+" "+currency)}
                    NetRow("Investments",investments,currency,expandedNet=="Investments"){expandedNet=if(expandedNet=="Investments")null else "Investments"}
                    if(expandedNet=="Investments") goals.filter{it.type==com.pitaka.app.data.GoalType.INVESTMENT}.forEach{g->AssetRow(g.name,g.progress.toString()+" "+currency)}
                }
            }
            SectionTitle("Monthly Trends")
            CombinedMonthlyChart(income,expenses)
            SectionTitle("Spending by Category")
            if(breakdown.isEmpty()) Text("No expenses for " + monthLabel(selectedMonth) + ".",color=Color.Gray)
            else {
                CategoryPie(breakdown)
                breakdown.forEachIndexed { i,item ->
                    Row(Modifier.fillMaxWidth().clickable{onOpenCategory(item.name)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                        Box(Modifier.size(10.dp).background(chartColors[i%chartColors.size],RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(10.dp)); Text(item.name,Modifier.weight(1f)); Text(currency+" "+"%,.2f".format(item.total),fontWeight=FontWeight.SemiBold)
                    }
                }
            }
            SectionTitle("Cash Inflow / Outflow")
            val monthExpenses=allExpenses.filter{java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString().startsWith(selectedMonth)}
            val monthIncome=income.find{it.month==selectedMonth}?.total ?: 0.0
            val monthOut=expenses.find{it.month==selectedMonth}?.total ?: monthExpenses.sumOf{it.amount}
            val monthEntries=allEntries.filter{java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString().startsWith(selectedMonth)}
            CashFlowTable(monthIncome,monthOut,currency,monthEntries,expandedFlow){expandedFlow=!expandedFlow}
        }
    }
}
@Composable private fun SectionTitle(t:String){Text(t,fontWeight=FontWeight.Bold,fontSize=17.sp)}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MonthSelector(months:List<String>,selected:String,onSelect:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    ExposedDropdownMenuBox(open,{open=!open},Modifier.fillMaxWidth()){
        OutlinedTextField(value=monthLabel(selected),onValueChange={},readOnly=true,label={Text("Month")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},modifier=Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open,{open=false}){months.distinct().sorted().reversed().forEach{m->DropdownMenuItem(text={Text(monthLabel(m))},onClick={onSelect(m);open=false})}}
    }
}
@Composable private fun NetRow(label:String,value:Double,currency:String,expanded:Boolean,onClick:()->Unit)=Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(if(expanded)"▾ $label" else "▸ $label");Text(currency+" "+"%,.2f".format(value),fontWeight=FontWeight.SemiBold)}
@Composable private fun AssetRow(name:String,balance:String,modifier:Modifier=Modifier)=Text("    "+name+"  •  "+balance,style=MaterialTheme.typography.bodySmall,color=Color.Gray,modifier=modifier.padding(vertical=3.dp))
@Composable private fun CombinedMonthlyChart(income:List<com.pitaka.app.data.MonthlyAmount>,expense:List<com.pitaka.app.data.MonthlyAmount>){
    val keys=(income.map{it.month}+expense.map{it.month}).distinct().sorted().takeLast(8)
    if(keys.isEmpty()){Text("No monthly data yet.",color=Color.Gray);return}
    val maxVal=max(1.0,keys.maxOf{max(income.find{x->x.month==it}?.total?:0.0,expense.find{x->x.month==it}?.total?:0.0)})
    Row(Modifier.fillMaxWidth().height(190.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        keys.forEach{m->Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom){
            Row(Modifier.height(140.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(2.dp)){
                Box(Modifier.width(10.dp).height(((income.find{x->x.month==m}?.total?:0.0)/maxVal*120).dp.coerceAtLeast(2.dp)).background(Color(0xFF1E8E5A),RoundedCornerShape(3.dp)))
                Box(Modifier.width(10.dp).height(((expense.find{x->x.month==m}?.total?:0.0)/maxVal*120).dp.coerceAtLeast(2.dp)).background(Color(0xFFD64545),RoundedCornerShape(3.dp)))
            };Text(monthLabel(m).substringBefore(" "),style=MaterialTheme.typography.labelSmall)
        }}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){Text("● Income / Assets",color=Color(0xFF1E8E5A),style=MaterialTheme.typography.labelSmall);Text("● Expenses",color=Color(0xFFD64545),style=MaterialTheme.typography.labelSmall)}
}
@Composable private fun CategoryPie(items:List<CategorySpend>){
    val total=items.sumOf{it.total}.coerceAtLeast(.01)
    Canvas(Modifier.fillMaxWidth().height(210.dp).padding(8.dp)){var start=-90f;items.forEachIndexed{i,x->val sweep=(x.total/total*360).toFloat();drawArc(chartColors[i%chartColors.size],start,sweep,true);start+=sweep}}
}
@Composable private fun CashFlowTable(inflow:Double,outflow:Double,currency:String,entries:List<com.pitaka.app.data.LedgerEntry>,expanded:Boolean,onClick:()->Unit){
    val inflowItems=entries.filter{it.type==com.pitaka.app.data.LedgerType.INCOME}.sortedByDescending{it.amount}
    val outflowItems=entries.filter{it.type==com.pitaka.app.data.LedgerType.EXPENSE}.sortedByDescending{it.amount}
    Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().clickable(onClick=onClick)){Column(Modifier.padding(14.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            FlowColumn("Cash Inflow",inflow,inflowItems,currency,Color(0xFF1E8E5A),expanded,Modifier.weight(1f))
            FlowColumn("Cash Outflow",outflow,outflowItems,currency,Color(0xFFD64545),expanded,Modifier.weight(1f))
        }
        Text(if(expanded)"Tap to collapse" else "Tap to view the complete list",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=8.dp))
    }}
}
@Composable private fun FlowColumn(title:String,total:Double,items:List<com.pitaka.app.data.LedgerEntry>,currency:String,color:Color,expanded:Boolean,modifier:Modifier){
    Column(modifier){
        Text(title,fontWeight=FontWeight.Bold)
        Text(currency+" "+"%,.2f".format(total),color=color,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        (if(expanded)items else items.take(4)).forEach{Text(it.name+" • "+currency+" "+"%,.2f".format(it.amount),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=2.dp))}
        if(items.size>4&&!expanded)Text("+"+(items.size-4)+" more",style=MaterialTheme.typography.labelSmall,color=Color.Gray)
    }
}
