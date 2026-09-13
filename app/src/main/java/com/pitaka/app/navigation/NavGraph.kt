package com.pitaka.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
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

    fun pitakaDetail(id: Long) = "pitaka_detail/$id"
    fun editPitaka(id: Long) = "edit_pitaka/$id"
    fun goalDetail(id: Long) = "goal_detail/$id"
    fun editGoal(id: Long) = "edit_goal/$id"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Default.Home),
    BottomTab(Routes.PITAKAS, "Pitakas", Icons.Default.AccountBalanceWallet),
    BottomTab(Routes.GOALS, "Goals", Icons.Default.Flag),
    BottomTab(Routes.EXPENSES, "Expenses", Icons.Default.Receipt)
)

@Composable
fun PitakaNavGraph(viewModel: PitakaViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    Scaffold(
        bottomBar = {
            if (bottomTabs.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenCurrencySettings = { navController.navigate(Routes.CURRENCY_SETTINGS) }
                )
            }
            composable(Routes.CURRENCY_SETTINGS) {
                CurrencySettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.PITAKAS) {
                PitakasScreen(
                    viewModel = viewModel,
                    onAddPitaka = { navController.navigate(Routes.CREATE_PITAKA) },
                    onTransfer = { navController.navigate(Routes.TRANSFER) },
                    onRecurring = { navController.navigate(Routes.RECURRING) },
                    onOpenPitaka = { id -> navController.navigate(Routes.pitakaDetail(id)) }
                )
            }
            composable(Routes.CREATE_PITAKA) {
                CreatePitakaScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }
            composable(
                route = Routes.EDIT_PITAKA,
                arguments = listOf(navArgument("pitakaId") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("pitakaId") ?: 0L
                CreatePitakaScreen(viewModel = viewModel, pitakaId = id, onDone = { navController.popBackStack() })
            }
            composable(Routes.TRANSFER) {
                TransferScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }
            composable(Routes.RECURRING) {
                RecurringRulesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PITAKA_DETAIL,
                arguments = listOf(navArgument("pitakaId") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("pitakaId") ?: 0L
                PitakaDetailScreen(
                    viewModel = viewModel,
                    pitakaId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editPitaka(id)) }
                )
            }

            composable(Routes.GOALS) {
                GoalsScreen(
                    viewModel = viewModel,
                    onAddGoal = { navController.navigate(Routes.CREATE_GOAL) },
                    onOpenGoal = { id -> navController.navigate(Routes.goalDetail(id)) }
                )
            }
            composable(Routes.CREATE_GOAL) {
                CreateGoalScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }
            composable(
                route = Routes.EDIT_GOAL,
                arguments = listOf(navArgument("goalId") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("goalId") ?: 0L
                CreateGoalScreen(viewModel = viewModel, goalId = id, onDone = { navController.popBackStack() })
            }
            composable(
                route = Routes.GOAL_DETAIL,
                arguments = listOf(navArgument("goalId") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("goalId") ?: 0L
                GoalDetailScreen(
                    viewModel = viewModel,
                    goalId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editGoal(id)) }
                )
            }

            composable(Routes.EXPENSES) {
                ExpensesScreen(
                    viewModel = viewModel,
                    onOpenBudgetHistory = { navController.navigate(Routes.BUDGET_HISTORY) }
                )
            }
            composable(Routes.BUDGET_HISTORY) {
                BudgetHistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
