package com.petesmapper.navigation

import androidx.compose.runtime.Composable
import com.petesmapper.ui.navigation.PetesMapperApp
import com.petesmapper.ui.theme.PetesMapperTheme

@Composable
fun AppNavHost() {
    PetesMapperTheme {
        PetesMapperApp()
    }
}
