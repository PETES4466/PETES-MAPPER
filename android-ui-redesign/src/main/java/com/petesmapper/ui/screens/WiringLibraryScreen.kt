package com.petesmapper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.components.GridCanvasPlaceholder
import com.petesmapper.ui.viewmodel.ProjectViewModel

@Composable
fun WiringLibraryScreen(projectViewModel: ProjectViewModel) {
    val categories = listOf("Snake", "Zigzag", "Z Strip", "Contour", "Spiral", "Hybrid")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Wiring Library") }
        items(categories) { category ->
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(category)
                    GridCanvasPlaceholder(
                        label = "$category pattern preview",
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    Button(onClick = { projectViewModel.setWiringPattern(category) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Apply to Project")
                    }
                    Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Favorite")
                    }
                }
            }
        }
    }
}
