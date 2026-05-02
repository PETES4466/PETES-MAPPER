package com.petesmapper.ui.auto.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.petesmapper.ui.auto.screens.AutoExportScreen
import com.petesmapper.ui.auto.screens.AutoFlowCorrectionScreen
import com.petesmapper.ui.auto.screens.AutoImagePrepScreen
import com.petesmapper.ui.auto.screens.AutoLandingScreen
import com.petesmapper.ui.auto.screens.AutoPixelDetectionScreen
import com.petesmapper.ui.auto.screens.AutoStripDetectionScreen
import com.petesmapper.ui.auto.screens.AutoValidationScreen
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel

object AutoRoutes {
    const val AUTO_LANDING = "AUTO_LANDING"
    const val AUTO_IMAGE_PREP = "AUTO_IMAGE_PREP"
    const val AUTO_STRIP_DETECTION = "AUTO_STRIP_DETECTION"
    const val AUTO_PIXEL_DETECTION = "AUTO_PIXEL_DETECTION"
    const val AUTO_FLOW_CORRECTION = "AUTO_FLOW_CORRECTION"
    const val AUTO_VALIDATION = "AUTO_VALIDATION"
    const val AUTO_EXPORT = "AUTO_EXPORT"
}

@Composable
fun AutoNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    val vm: AutoWorkflowViewModel = viewModel()
    val backStack by navController.currentBackStackEntryAsState()

    AnimatedContent(
        targetState = backStack?.destination?.route ?: AutoRoutes.AUTO_LANDING,
        transitionSpec = {
            (slideInHorizontally(animationSpec = tween(220)) { it / 5 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(animationSpec = tween(220)) { -it / 5 } + fadeOut(tween(180)))
        },
        label = "auto-route-transition",
    ) {
        NavHost(navController = navController, startDestination = AutoRoutes.AUTO_LANDING) {
            composable(AutoRoutes.AUTO_LANDING) {
                StepLayout(
                    content = { AutoLandingScreen(vm) },
                    onNext = { navController.navigate(AutoRoutes.AUTO_IMAGE_PREP, workflowNavOptions(AutoRoutes.AUTO_LANDING)) },
                    onReset = { vm.resetFromStep(0) },
                    onResume = { vm.restoreWorkflow() },
                )
            }
            composable(AutoRoutes.AUTO_IMAGE_PREP) {
                StepLayout(
                    content = { AutoImagePrepScreen(vm) },
                    onNext = { navController.navigate(AutoRoutes.AUTO_STRIP_DETECTION, workflowNavOptions(AutoRoutes.AUTO_LANDING)) },
                    onReset = { vm.resetFromStep(1) },
                    onResume = { vm.restoreWorkflow() },
                )
            }
            composable(AutoRoutes.AUTO_STRIP_DETECTION) {
                StepLayout(
                    content = { AutoStripDetectionScreen(vm) },
                    onNext = { navController.navigate(AutoRoutes.AUTO_PIXEL_DETECTION, workflowNavOptions(AutoRoutes.AUTO_LANDING)) },
                    onReset = { vm.resetFromStep(2) },
                    onResume = { vm.restoreWorkflow() },
                )
            }
            composable(AutoRoutes.AUTO_PIXEL_DETECTION) {
                StepLayout(
                    content = { AutoPixelDetectionScreen(vm) },
                    onNext = { navController.navigate(AutoRoutes.AUTO_FLOW_CORRECTION, workflowNavOptions(AutoRoutes.AUTO_LANDING)) },
                    onReset = { vm.resetFromStep(3) },
                    onResume = { vm.restoreWorkflow() },
                )
            }
            composable(AutoRoutes.AUTO_FLOW_CORRECTION) {
                StepLayout(
                    content = { AutoFlowCorrectionScreen(vm) },
                    onNext = { navController.navigate(AutoRoutes.AUTO_VALIDATION, workflowNavOptions(AutoRoutes.AUTO_LANDING)) },
                    onReset = { vm.resetFromStep(4) },
                    onResume = { vm.restoreWorkflow() },
                )
            }
            composable(AutoRoutes.AUTO_VALIDATION) {
                StepLayout(
                    content = { AutoValidationScreen(vm) },
                    onNext = {
                        val canExport = vm.state.value.autoValidationReport?.canExport ?: false
                        if (canExport) navController.navigate(AutoRoutes.AUTO_EXPORT, workflowNavOptions(AutoRoutes.AUTO_LANDING))
                    },
                    onReset = { vm.resetFromStep(5) },
                    onResume = { vm.restoreWorkflow() },
                )
            }
            composable(AutoRoutes.AUTO_EXPORT) {
                StepLayout(
                    content = { AutoExportScreen(vm) },
                    onNext = {},
                    onReset = { vm.resetFromStep(6) },
                    onResume = { vm.restoreWorkflow() },
                )
            }
        }
    }
}

fun workflowNavOptions(popUpRoute: String): NavOptionsBuilder.() -> Unit = {
    launchSingleTop = true
    restoreState = true
    popUpTo(popUpRoute) {
        saveState = true
    }
}

@Composable
private fun StepLayout(
    content: @Composable () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
    onResume: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        content()
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume Workflow") }
            Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset From Step") }
        }
    }
}
