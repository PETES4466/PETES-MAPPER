package com.petesmapper.ui.auto.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petesmapper.ui.auto.screens.*
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel

@Composable
fun AutoNavGraph() {
    val vm: AutoWorkflowViewModel = viewModel()
    AutoLandingScreen(vm)
}
