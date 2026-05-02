package com.petesmapper.ui.auto.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petesmapper.ui.auto.AutoImportEngine
import com.petesmapper.ui.auto.AutoLayoutComposer
import com.petesmapper.ui.auto.AutoLetterUnit
import com.petesmapper.ui.auto.AutoSignLayout
import com.petesmapper.ui.auto.AutoValidationReport
import com.petesmapper.ui.auto.CorrectedFlowModel
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel
import com.petesmapper.ui.geometry.RoutePlan
import java.io.File

private data class ProcessedLetterWorkflow(
    val sourcePath: String,
    val correctedFlow: CorrectedFlowModel,
    val routePlan: RoutePlan,
    val validationReport: AutoValidationReport,
)

private data class BatchResumeState(
    val queuedPaths: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val processedPaths: List<String> = emptyList(),
)

@Composable
fun AutoBatchImportScreen(vm: AutoWorkflowViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val importEngine = remember { AutoImportEngine() }
    val layoutComposer = remember { AutoLayoutComposer() }
    val gson = remember { Gson() }
    val resumeFile = remember { File(context.cacheDir, "auto_batch_resume.json") }

    val queue = remember { mutableStateListOf<String>() }
    val processed = remember { mutableStateListOf<ProcessedLetterWorkflow>() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var mergedLayout by remember { mutableStateOf<AutoSignLayout?>(null) }
    var status by remember { mutableStateOf("Idle") }

    fun persistBatchState() {
        val payload = BatchResumeState(
            queuedPaths = queue.toList(),
            currentIndex = currentIndex,
            processedPaths = processed.map { it.sourcePath },
        )
        runCatching { resumeFile.writeText(gson.toJson(payload)) }
    }

    fun processCurrentItem() {
        if (currentIndex !in queue.indices) return
        val path = queue[currentIndex]
        runCatching {
            vm.setNormalizedImage(importEngine.loadImage(File(path)))
            vm.runStripDetection()
            vm.runPixelDetection()
            vm.runFlowDetection()
            vm.runReverseBuild()
            vm.runValidation()
            vm.autoSaveSnapshot()
            val after = vm.state.value
            val corrected = after.correctedFlowModel ?: error("Missing corrected flow")
            val route = after.routePlan ?: error("Missing route plan")
            val validation = after.autoValidationReport ?: error("Missing validation report")
            processed.removeAll { it.sourcePath == path }
            processed += ProcessedLetterWorkflow(path, corrected, route, validation)
            status = "Processed ${currentIndex + 1}/${queue.size}"
        }.onFailure {
            status = "Process failed: ${it.message}"
        }
        persistBatchState()
    }

    LaunchedEffect(Unit) {
        if (!resumeFile.exists()) return@LaunchedEffect
        runCatching {
            val type = object : TypeToken<BatchResumeState>() {}.type
            gson.fromJson<BatchResumeState>(resumeFile.readText(), type)
        }.getOrNull()?.let { resume ->
            queue.clear()
            queue.addAll(resume.queuedPaths.filter { File(it).exists() }.take(15))
            currentIndex = resume.currentIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
            status = if (queue.isNotEmpty()) "Resumed batch with ${queue.size} images" else "Resume file found, no valid files"
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val current = queue.toMutableList()
        uris.forEach { uri ->
            if (current.size >= 15) return@forEach
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: return@runCatching
                val outFile = File(context.cacheDir, "auto_batch_${System.currentTimeMillis()}_${current.size}.png")
                outFile.outputStream().use { output -> input.copyTo(output) }
                current += outFile.absolutePath
            }
        }
        queue.clear()
        queue.addAll(current.distinct().take(15))
        if (currentIndex > queue.lastIndex) currentIndex = queue.lastIndex.coerceAtLeast(0)
        status = "Queued ${queue.size} image(s)"
        persistBatchState()
    }

    val progress = if (queue.isEmpty()) 0f else processed.size.toFloat() / queue.size.toFloat()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AUTO Batch Import", style = MaterialTheme.typography.titleLarge)
        Text("Batch status: $status")
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("Progress: ${processed.size}/${queue.size} processed (max 15)")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("image/*")) }, enabled = queue.size < 15) { Text("Add Images") }
            Button(onClick = { processCurrentItem() }, enabled = currentIndex in queue.indices) { Text("Process Current") }
            Button(onClick = {
                queue.clear(); processed.clear(); currentIndex = 0; mergedLayout = null; status = "Batch cleared"; persistBatchState()
            }) { Text("Clear") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current processing preview")
                Text("Image: ${queue.getOrNull(currentIndex)?.substringAfterLast('/') ?: "None"}")
                Text("Workflow image loaded: ${state.normalizedImage?.originalPath?.substringAfterLast('/') ?: "No active image"}")
            }
        }

        Text("Queue list")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = true)) {
            itemsIndexed(queue) { index, path ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${index + 1}. ${path.substringAfterLast('/')}")
                        Text(
                            when {
                                processed.any { it.sourcePath == path } -> "Processed"
                                index == currentIndex -> "Current"
                                else -> "Queued"
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {
                                if (index > 0) {
                                    val moved = queue.removeAt(index); queue.add(index - 1, moved); if (currentIndex == index) currentIndex--
                                    persistBatchState()
                                }
                            }, enabled = index > 0) { Text("Up") }
                            Button(onClick = {
                                if (index < queue.lastIndex) {
                                    val moved = queue.removeAt(index); queue.add(index + 1, moved); if (currentIndex == index) currentIndex++
                                    persistBatchState()
                                }
                            }, enabled = index < queue.lastIndex) { Text("Down") }
                            Button(onClick = {
                                queue.removeAt(index); processed.removeAll { it.sourcePath == path }
                                if (currentIndex > queue.lastIndex) currentIndex = queue.lastIndex.coerceAtLeast(0)
                                persistBatchState()
                            }) { Text("Remove") }
                            Button(onClick = { currentIndex = index; persistBatchState() }) { Text("Select") }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val route = vm.state.value.routePlan ?: return@Button
                val unit = AutoLetterUnit(routePlan = route)
                mergedLayout = layoutComposer.addLetterUnit(
                    mergedLayout ?: layoutComposer.compose(emptyList()),
                    unit,
                )
                status = "Added current workflow to layout"
            }) { Text("Add to Layout") }

            Button(onClick = {
                val units = processed.map { AutoLetterUnit(routePlan = it.routePlan) }
                mergedLayout = layoutComposer.compose(units)
                status = "Merged ${units.size} processed letters"
            }, enabled = processed.isNotEmpty()) { Text("Merge All") }
        }

        Text("Merged letters: ${mergedLayout?.letterUnits?.size ?: 0}")
    }
}
