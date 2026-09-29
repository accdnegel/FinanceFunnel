package com.pitaka.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.screens.*

object Routes{
 const val HOME="home";const val PITAKAS="pitakas";const val GOALS="goals";const val EXPENSES="expenses"
 const val CREATE_INCOME="create_income"
 const val CREATE_PITAKA="create_pitaka";const val EDIT_PITAKA="edit_pitaka/{pitakaId}";const val PITAKA_DETAIL="pitaka_detail/{pitakaId}"
 const val TRANSFER="transfer";const val CREATE_GOAL="create_goal";const val EDIT_GOAL="edit_goal/{goalId}";const val GOAL_DETAIL="goal_detail/{goalId}"
 const val BUDGET_HISTORY="budget_history";const val CURRENCY_SETTINGS="currency_settings"
 const val CREATE_EXPENSE="create_expense";const val CREATE_FUNNEL="create_funnel";const val CATEGORY_DETAIL="category_detail/{category}";const val FUNNEL_DETAIL="funnel_detail/{funnelId}"
 fun pitakaDetail(id:Long)="pitaka_detail/$id";fun editPitaka(id:Long)="edit_pitaka/$id";fun goalDetail(id:Long)="goal_detail/$id";fun editGoal(id:Long)="edit_goal/$id";fun categoryDetail(c:String)="category_detail/"+java.net.URLEncoder.encode(c,"UTF-8");fun funnelDetail(id:Long)="funnel_detail/$id"
}
private data class Tab(val route:String,val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
private val tabs=listOf(Tab(Routes.HOME,"Home",Icons.Default.Home),Tab(Routes.PITAKAS,"Pitakas",Icons.Default.AccountBalanceWallet),Tab(Routes.GOALS,"Goals",Icons.Default.Flag),Tab(Routes.EXPENSES,"Spending",Icons.Default.Receipt))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PitakaNavGraph(viewModel:PitakaViewModel){
 val nav=rememberNavController();val back by nav.currentBackStackEntryAsState();val route=back?.destination?.route;var showAdd by remember{mutableStateOf(false)}
 val operationError by viewModel.operationError.collectAsState();val snackbarHostState=remember{SnackbarHostState()}
 LaunchedEffect(operationError){operationError?.let{snackbarHostState.showSnackbar(it);viewModel.clearOperationError()}}
 Scaffold(snackbarHost={SnackbarHost(snackbarHostState)},bottomBar={if(tabs.any{it.route==route})NavigationBar{tabs.forEach{t->NavigationBarItem(selected=route==t.route,onClick={nav.navigate(t.route){popUpTo(Routes.HOME){saveState=true};launchSingleTop=true;restoreState=true}},icon={Icon(t.icon,t.label)},label={Text(t.label)})}}},
 floatingActionButton={if(tabs.any{it.route==route})DraggableAddButton { showAdd=true }} ){padding->
  NavHost(nav,Routes.HOME,Modifier.padding(padding)){
   composable(Routes.HOME){HomeScreen(viewModel,{nav.navigate(Routes.CURRENCY_SETTINGS)},{c->nav.navigate(Routes.categoryDetail(c))},{id->nav.navigate(Routes.pitakaDetail(id))})}
    composable(Routes.PITAKAS){PitakasScreen(viewModel,{nav.navigate(Routes.CREATE_PITAKA)},{nav.navigate(Routes.TRANSFER)},{nav.navigate(Routes.pitakaDetail(it))})}
   composable(Routes.CREATE_PITAKA){CreatePitakaScreen(viewModel,onDone={nav.popBackStack()})}
   composable(Routes.EDIT_PITAKA,arguments=listOf(navArgument("pitakaId"){type=NavType.LongType})){CreatePitakaScreen(viewModel,it.arguments?.getLong("pitakaId")?:0L,onDone={nav.popBackStack()})}
   composable(Routes.PITAKA_DETAIL,arguments=listOf(navArgument("pitakaId"){type=NavType.LongType})){val id=it.arguments?.getLong("pitakaId")?:0L;PitakaDetailScreen(viewModel,id,{nav.popBackStack()},{nav.navigate(Routes.editPitaka(id))},{childId -> nav.navigate(Routes.pitakaDetail(childId))})}
   composable(Routes.TRANSFER){TransferScreen(viewModel){nav.popBackStack()}}
   composable(Routes.GOALS){GoalsScreen(viewModel,{nav.navigate(Routes.CREATE_GOAL)},{nav.navigate(Routes.goalDetail(it))})}
   composable(Routes.CREATE_GOAL){CreateGoalScreen(viewModel){nav.popBackStack()}}
   composable(Routes.EDIT_GOAL,arguments=listOf(navArgument("goalId"){type=NavType.LongType})){CreateGoalScreen(viewModel,it.arguments?.getLong("goalId")?:0L){nav.popBackStack()}}
   composable(Routes.GOAL_DETAIL,arguments=listOf(navArgument("goalId"){type=NavType.LongType})){val id=it.arguments?.getLong("goalId")?:0L;GoalDetailScreen(viewModel,id,{nav.popBackStack()},{nav.navigate(Routes.editGoal(id))})}
   composable(Routes.EXPENSES){ExpensesScreen(viewModel,{nav.navigate(Routes.BUDGET_HISTORY)},{id->nav.navigate(Routes.funnelDetail(id))})}
   composable(Routes.BUDGET_HISTORY){BudgetHistoryScreen(viewModel){nav.popBackStack()}}
   composable(Routes.CURRENCY_SETTINGS){CurrencySettingsScreen(viewModel){nav.popBackStack()}}
   composable(Routes.CREATE_EXPENSE){CreateExpenseScreen(viewModel){nav.popBackStack()}}
    composable(Routes.CREATE_INCOME){CreateIncomeScreen(viewModel){nav.popBackStack()}}
   composable(Routes.CREATE_FUNNEL){CreateExpenseFunnelScreen(viewModel){nav.popBackStack()}}
   composable(Routes.CATEGORY_DETAIL,arguments=listOf(navArgument("category"){type=NavType.StringType})){CategoryDetailScreen(viewModel,java.net.URLDecoder.decode(it.arguments?.getString("category")?:"","UTF-8")){nav.popBackStack()}}
   composable(Routes.FUNNEL_DETAIL,arguments=listOf(navArgument("funnelId"){type=NavType.LongType})){FunnelDetailScreen(viewModel,it.arguments?.getLong("funnelId")?:0L){nav.popBackStack()}}
  }
 }
    if(showAdd)ModalBottomSheet(onDismissRequest={showAdd=false}){Column(Modifier.padding(24.dp)){Text("Add",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.padding(4.dp));if(route==Routes.PITAKAS||route==Routes.HOME)TextButton({showAdd=false;nav.navigate(Routes.CREATE_PITAKA)}){Text("Pitaka")};if(route==Routes.GOALS||route==Routes.HOME)TextButton({showAdd=false;nav.navigate(Routes.CREATE_GOAL)}){Text("Goal")};TextButton({showAdd=false;nav.navigate(Routes.CREATE_INCOME)}){Text("Income")};TextButton({showAdd=false;nav.navigate(Routes.CREATE_EXPENSE)}){Text("Expense")};TextButton({showAdd=false;nav.navigate(Routes.CREATE_FUNNEL)}){Text("Expense Funnel")};Spacer(Modifier.height(24.dp))}}
}

@Composable
private fun DraggableAddButton(onClick: () -> Unit) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val horizontalLimit = with(density) { (configuration.screenWidthDp.dp - 88.dp).coerceAtLeast(0.dp).toPx() }
    val verticalLimit = with(density) { (configuration.screenHeightDp.dp - 200.dp).coerceAtLeast(0.dp).toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
            .pointerInput(horizontalLimit, verticalLimit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(-horizontalLimit, 0f)
                    offsetY = (offsetY + dragAmount.y).coerceIn(-verticalLimit, 0f)
                }
            }
    ) {
        Icon(Icons.Default.Add, "Add")
    }
}
