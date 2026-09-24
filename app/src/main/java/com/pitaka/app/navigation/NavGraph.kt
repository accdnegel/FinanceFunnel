package com.pitaka.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.screens.*

object Routes {
    const val HOME = "home"
    const val PITAKAS = "pitakas"
    const val GOALS = "goals"
    const val EXPENSES = "expenses"
    const val CREATE_PITAKA = "create_pitaka"
    const val EDIT_PITAKA = "edit_pitaka/{pitakaId}"
    const val PITAKA_DETAIL = "pitaka_detail/{pitakaId}"
    const val TRANSFER = "transfer"
    const val CREATE_GOAL = "create_goal"
    const val EDIT_GOAL = "edit_goal/{goalId}"
    const val GOAL_DETAIL = "goal_detail/{goalId}"
    const val BUDGET_HISTORY = "budget_history"
    const val RECURRING = "recurring"
    const val CURRENCY_SETTINGS = "currency_settings"
    const val CREATE_EXPENSE = "create_expense"
    const val CREATE_FUNNEL = "create_funnel"
    const val CATEGORY_DETAIL = "category_detail/{category}"
    const val FUNNEL_DETAIL = "funnel_detail/{funnelId}"

    fun pitakaDetail(id: Long) = "pitaka_detail/$id"
    fun editPitaka(id: Long) = "edit_pitaka/$id"
    fun goalDetail(id: Long) = "goal_detail/$id"
    fun editGoal(id: Long) = "edit_goal/$id"
    fun categoryDetail(category: String) =
        "category_detail/" + java.net.URLEncoder.encode(category, "UTF-8")
    fun funnelDetail(id: Long) = "funnel_detail/$id"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.PITAKAS, "Pitakas", Icons.Default.AccountBalanceWallet),
    Tab(Routes.GOALS, "Goals", Icons.Default.Flag),
    Tab(Routes.EXPENSES, "Spending", Icons.Default.Receipt)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitakaNavGraph(viewModel: PitakaViewModel) {
    val nav = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val operationError by viewModel.operationError.collectAsState()
    val hasMissingConversionRates by viewModel.hasMissingConversionRates.collectAsState(initial = false)

    LaunchedEffect(operationError) {
        operationError?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            viewModel.clearOperationError()
        }
    }

    LaunchedEffect(hasMissingConversionRates) {
        if (hasMissingConversionRates) {
            snackbarHostState.showSnackbar(
                "Some totals cannot be converted because an exchange rate is missing."
            )
        }
    }

    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tabs.any { it.route == route }) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel,
                    { nav.navigate(Routes.CURRENCY_SETTINGS) },
                    { category -> nav.navigate(Routes.categoryDetail(category)) },
                    { id -> nav.navigate(Routes.pitakaDetail(id)) }
                )
            }
            composable(Routes.PITAKAS) {
                PitakasScreen(
                    viewModel,
                    { nav.navigate(Routes.CREATE_PITAKA) },
                    { nav.navigate(Routes.TRANSFER) },
                    { nav.navigate(Routes.RECURRING) },
                    { id -> nav.navigate(Routes.pitakaDetail(id)) }
                )
            }
            composable(Routes.CREATE_PITAKA) {
                CreatePitakaScreen(viewModel, onDone = { nav.popBackStack() })
            }
            composable(
                Routes.EDIT_PITAKA,
                arguments = listOf(navArgument("pitakaId") { type = NavType.LongType })
            ) {
                CreatePitakaScreen(
                    viewModel,
                    it.arguments?.getLong("pitakaId") ?: 0L,
                    onDone = { nav.popBackStack() }
                )
            }
            composable(
                Routes.PITAKA_DETAIL,
                arguments = listOf(navArgument("pitakaId") { type = NavType.LongType })
            ) {
                val id = it.arguments?.getLong("pitakaId") ?: 0L
                PitakaDetailScreen(
                    viewModel,
                    id,
                    { nav.popBackStack() },
                    { nav.navigate(Routes.editPitaka(id)) },
                    { childId -> nav.navigate(Routes.pitakaDetail(childId)) }
                )
            }
            composable(Routes.TRANSFER) {
                TransferScreen(viewModel) { nav.popBackStack() }
            }
            composable(Routes.RECURRING) {
                RecurringRulesScreen(viewModel) { nav.popBackStack() }
            }
            composable(Routes.GOALS) {
                GoalsScreen(
                    viewModel,
                    { nav.navigate(Routes.CREATE_GOAL) },
                    { id -> nav.navigate(Routes.goalDetail(id)) }
                )
            }
            composable(Routes.CREATE_GOAL) {
                CreateGoalScreen(viewModel) { nav.popBackStack() }
            }
            composable(
                Routes.EDIT_GOAL,
                arguments = listOf(navArgument("goalId") { type = NavType.LongType })
            ) {
                CreateGoalScreen(
                    viewModel,
                    it.arguments?.getLong("goalId") ?: 0L
                ) { nav.popBackStack() }
            }
            composable(
                Routes.GOAL_DETAIL,
                arguments = listOf(navArgument("goalId") { type = NavType.LongType })
            ) {
                val id = it.arguments?.getLong("goalId") ?: 0L
                GoalDetailScreen(
                    viewModel,
                    id,
                    { nav.popBackStack() },
                    { nav.navigate(Routes.editGoal(id)) }
                )
            }
            composable(Routes.EXPENSES) {
                ExpensesScreen(
                    viewModel,
                    { nav.navigate(Routes.BUDGET_HISTORY) },
                    { id -> nav.navigate(Routes.funnelDetail(id)) }
                )
            }
            composable(Routes.BUDGET_HISTORY) {
                BudgetHistoryScreen(viewModel) { nav.popBackStack() }
            }
            composable(Routes.CURRENCY_SETTINGS) {
                CurrencySettingsScreen(viewModel) { nav.popBackStack() }
            }
            composable(Routes.CREATE_EXPENSE) {
                CreateExpenseScreen(viewModel) { nav.popBackStack() }
            }
            composable(Routes.CREATE_FUNNEL) {
                CreateExpenseFunnelScreen(viewModel) { nav.popBackStack() }
            }
            composable(
                Routes.CATEGORY_DETAIL,
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) {
                CategoryDetailScreen(
                    viewModel,
                    java.net.URLDecoder.decode(
                        it.arguments?.getString("category") ?: "",
                        "UTF-8"
                    )
                ) { nav.popBackStack() }
            }
            composable(
                Routes.FUNNEL_DETAIL,
                arguments = listOf(navArgument("funnelId") { type = NavType.LongType })
            ) {
                FunnelDetailScreen(
                    viewModel,
                    it.arguments?.getLong("funnelId") ?: 0L
                ) { nav.popBackStack() }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Add",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(8.dp))

                if (route == Routes.PITAKAS || route == Routes.HOME) {
                    TextButton(
                        onClick = {
                            showAdd = false
                            nav.navigate(Routes.CREATE_PITAKA)
                        }
                    ) { Text("Pitaka") }
                }

                if (route == Routes.GOALS || route == Routes.HOME) {
                    TextButton(
                        onClick = {
                            showAdd = false
                            nav.navigate(Routes.CREATE_GOAL)
                        }
                    ) { Text("Goal") }
                }

                TextButton(
                    onClick = {
                        showAdd = false
                        nav.navigate(Routes.CREATE_EXPENSE)
                    }
                ) { Text("Expense") }

                TextButton(
                    onClick = {
                        showAdd = false
                        nav.navigate(Routes.CREATE_FUNNEL)
                    }
                ) { Text("Expense Funnel") }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
