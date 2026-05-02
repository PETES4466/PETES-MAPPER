package com.petesmapper.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.petesmapper.ui.theme.PetesMapperTheme
import com.petesmapper.ui.viewmodel.ProjectViewModel

private fun workflowStepIndex(route: String?): Int? {
    return when (route) {
        AppDestination.ProjectSetup.route -> 1
        AppDestination.LetterBuilder.route -> 2
        AppDestination.WiringIntelligence.route -> 3
        AppDestination.LayerEditor.route -> 4
        AppDestination.PixelMappingPreview.route -> 5
        AppDestination.Export.route -> 6
        else -> null
    }
}

@Composable
fun PetesMapperApp() {
    PetesMapperTheme {
        val navController = rememberNavController()
        val projectViewModel: ProjectViewModel = viewModel()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val navItems = listOf(
            Triple(AppDestination.Dashboard.route, "Dashboard", Icons.Outlined.Dashboard),
            Triple(AppDestination.LetterBuilder.route, "Builder", Icons.Outlined.Draw),
            Triple(AppDestination.WiringIntelligence.route, "Wiring", Icons.Outlined.AccountTree),
            Triple(AppDestination.LayerEditor.route, "Layers", Icons.Outlined.Layers),
            Triple(AppDestination.PixelMappingPreview.route, "Preview", Icons.Outlined.Memory),
            Triple(AppDestination.Export.route, "Export", Icons.Outlined.FileDownload),
        )

        Scaffold(
            topBar = {
                val step = workflowStepIndex(currentRoute)
                if (step != null) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Step $step of 6")
                        LinearProgressIndicator(progress = { step / 6f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    navItems.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(AppDestination.Dashboard.route) { saveState = true }
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { paddingValues ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(paddingValues)) {
                AppNavGraph(navController = navController, projectViewModel = projectViewModel)
            }
        }
    }
}
