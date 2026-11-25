package fr.btssio.voxresto.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private enum class Tab(val route: String, val title: String, val icon: ImageVector) {
    Order("order", "Commande", Icons.Filled.Mic),
    Menu("menu", "Menu", Icons.Filled.RestaurantMenu),
    History("history", "Historique", Icons.Filled.History),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxRestoUi() {
    // ViewModels rattachés à l'activité : l'écoute et l'état survivent aux changements d'onglet.
    val orderVm: OrderViewModel = viewModel()
    val menuVm: MenuViewModel = viewModel()
    val historyVm: HistoryViewModel = viewModel()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Tab.Order.route
    val currentTab = Tab.entries.first { it.route == currentRoute }

    Scaffold(
        topBar = { TopAppBar(title = { Text(currentTab.title) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == currentTab,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Order.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Order.route) { OrderScreen(orderVm) }
            composable(Tab.Menu.route) { MenuScreen(menuVm) }
            composable(Tab.History.route) { HistoryScreen(historyVm) }
        }
    }
}
