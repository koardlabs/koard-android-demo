package com.koard.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.koard.android.R
import com.koard.android.ui.MainScreen
import com.koard.android.ui.SettingsScreen
import com.koard.android.ui.TransactionDetailsScreen
import com.koard.android.ui.TransactionFlowScreen
import com.koard.android.ui.TransactionHistoryScreen

@Composable
fun KoardNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavigationRoutes.Tabs,
        modifier = modifier
    ) {
        composable<NavigationRoutes.Tabs> {
            TabsRoot(
                onNavigateToTransactionFlow = {
                    navController.navigate(NavigationRoutes.TransactionFlow)
                },
                onTransactionSelected = { transactionId ->
                    navController.navigate(NavigationRoutes.TransactionDetails(transactionId))
                }
            )
        }

        composable<NavigationRoutes.TransactionFlow> {
            TransactionFlowScreen(onNavigateBack = navController::popBackStack)
        }

        composable<NavigationRoutes.TransactionDetails> { backStackEntry ->
            val transactionDetails = backStackEntry.toRoute<NavigationRoutes.TransactionDetails>()
            TransactionDetailsScreen(
                transactionId = transactionDetails.transactionId,
                onNavigateBack = navController::popBackStack
            )
        }
    }
}

private enum class HomeTab(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    History(R.string.tab_history, Icons.Default.List),
    Home(R.string.tab_home, Icons.Default.Home),
    Settings(R.string.tab_settings, Icons.Default.Settings)
}

@Composable
private fun TabsRoot(
    onNavigateToTransactionFlow: () -> Unit,
    onTransactionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Home) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                HomeTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            HomeTab.History -> TransactionHistoryScreen(
                modifier = Modifier.padding(paddingValues),
                showBackButton = false,
                onTransactionClick = onTransactionSelected
            )

            HomeTab.Home -> MainScreen(
                modifier = Modifier.padding(paddingValues),
                onNavigateToTransactionFlow = onNavigateToTransactionFlow
            )

            HomeTab.Settings -> SettingsScreen(
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
