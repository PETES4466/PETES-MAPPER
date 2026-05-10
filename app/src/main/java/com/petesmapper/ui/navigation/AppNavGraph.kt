package com.petesmapper.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.petesmapper.ui.screens.DashboardScreen
import com.petesmapper.ui.screens.ExportScreen
import com.petesmapper.ui.screens.LayerEditorScreen
import com.petesmapper.ui.screens.LetterBuilderScreen
import com.petesmapper.ui.screens.PixelMappingPreviewScreen
import com.petesmapper.ui.screens.ProjectSetupScreen
import com.petesmapper.ui.screens.WiringIntelligenceScreen
import com.petesmapper.ui.screens.WiringLibraryScreen
import com.petesmapper.ui.viewmodel.ExportViewModel
import com.petesmapper.ui.viewmodel.LayerEditorViewModel
import com.petesmapper.ui.viewmodel.ProjectViewModel
import com.petesmapper.ui.viewmodel.WiringViewModel

private fun NavOptionsBuilder.workflowNavOptions() {
    launchSingleTop = true
    restoreState = true
    popUpTo(AppDestination.Dashboard.route) {
        saveState = true
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    projectViewModel: ProjectViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Dashboard.route,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it / 8 }) + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 8 }) + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 8 }) + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it / 8 }) + fadeOut()
        },
    ) {
        composable(AppDestination.Dashboard.route) {
            DashboardScreen(
                projectViewModel = projectViewModel,
                onNewProject = {
                    navController.navigate(AppDestination.ProjectSetup.route) { workflowNavOptions() }
                },
                onOpenProject = {
                    navController.navigate(AppDestination.ProjectSetup.route) { workflowNavOptions() }
                },
                onWiringLibrary = {
                    navController.navigate(AppDestination.WiringLibrary.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onTemplates = {
                    navController.navigate(AppDestination.ProjectSetup.route) { workflowNavOptions() }
                },
            )
        }
        composable(AppDestination.ProjectSetup.route) {
            ProjectSetupScreen(
                projectViewModel = projectViewModel,
                onStartDesigning = {
                    navController.navigate(AppDestination.LetterBuilder.route) { workflowNavOptions() }
                },
            )
        }
        composable(AppDestination.LetterBuilder.route) {
            LetterBuilderScreen(
                projectViewModel = projectViewModel,
                onGenerated = { navController.navigate(AppDestination.WiringIntelligence.route) { workflowNavOptions() } },
            )
        }
        composable(AppDestination.WiringIntelligence.route) {
            WiringIntelligenceScreen(
                projectViewModel = projectViewModel,
                wiringViewModel = viewModel<WiringViewModel>(),
                onGenerated = { navController.navigate(AppDestination.LayerEditor.route) { workflowNavOptions() } },
            )
        }
        composable(AppDestination.LayerEditor.route) {
            LayerEditorScreen(
                layerViewModel = viewModel<LayerEditorViewModel>(),
                projectViewModel = projectViewModel,
                onFinalize = { navController.navigate(AppDestination.PixelMappingPreview.route) { workflowNavOptions() } },
            )
        }
        composable(AppDestination.PixelMappingPreview.route) {
            PixelMappingPreviewScreen(
                projectViewModel = projectViewModel,
                onProceed = { navController.navigate(AppDestination.Export.route) { workflowNavOptions() } },
            )
        }
        composable(AppDestination.Export.route) {
            ExportScreen(
                exportViewModel = viewModel<ExportViewModel>(),
                projectViewModel = projectViewModel,
            )
        }
        composable(AppDestination.WiringLibrary.route) {
            WiringLibraryScreen(projectViewModel = projectViewModel)
        }
    }
}
