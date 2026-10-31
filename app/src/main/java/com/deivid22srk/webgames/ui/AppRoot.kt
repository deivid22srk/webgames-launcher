package com.deivid22srk.webgames.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deivid22srk.webgames.data.AppViewModel
import com.deivid22srk.webgames.ui.diagnostics.DiagnosticsScreen
import com.deivid22srk.webgames.ui.editor.GameEditorScreen
import com.deivid22srk.webgames.ui.game.GameScreen
import com.deivid22srk.webgames.ui.home.HomeScreen
import com.deivid22srk.webgames.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val EDITOR = "editor?gameId={gameId}"
    const val GAME = "game/{gameId}"

    fun editor(gameId: String? = null) = if (gameId == null) "editor" else "editor?gameId=$gameId"
    fun game(gameId: String) = "game/$gameId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Início", Icons.Filled.Home),
    Tab(Routes.DIAGNOSTICS, "Diagnóstico", Icons.Filled.Info),
    Tab(Routes.SETTINGS, "Ajustes", Icons.Filled.Settings),
)

/**
 * Raiz do app: navegação adaptativa (NavigationBar em compacto,
 * WideNavigationRail em expandido) + NavHost.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppRoot(vm: AppViewModel, windowSize: WindowSizeClass) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val expanded = windowSize.widthSizeClass >= WindowWidthSizeClass.Medium

    val showChrome = tabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showChrome && !expanded) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy
                                ?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val railModifier = if (showChrome && expanded) Modifier.padding(padding) else Modifier
        androidx.compose.foundation.layout.Row(modifier = railModifier) {
            if (showChrome && expanded) {
                WideNavigationRail {
                    tabs.forEach { tab ->
                        WideNavigationRailItem(
                            railExpanded = true,
                            selected = currentDestination?.hierarchy
                                ?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier,
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        vm = vm,
                        onOpenGame = { navController.navigate(Routes.game(it)) },
                        onEditGame = { navController.navigate(Routes.editor(it)) },
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen(vm = vm) }
                composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(vm = vm) }
                composable(
                    route = Routes.EDITOR,
                    arguments = listOf(
                        androidx.navigation.navArgument("gameId") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    GameEditorScreen(
                        vm = vm,
                        gameId = entry.arguments?.getString("gameId"),
                        onDone = { navController.popBackStack() },
                    )
                }
                composable(Routes.GAME) { entry ->
                    val gameId = entry.arguments?.getString("gameId").orEmpty()
                    GameScreen(vm = vm, gameId = gameId, onExit = { navController.popBackStack() })
                }
            }
        }
    }
}
