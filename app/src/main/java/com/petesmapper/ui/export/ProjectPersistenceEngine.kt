package com.petesmapper.ui.export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.petesmapper.ui.geometry.JumpConnection
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.viewmodel.ProjectUiState
import java.io.File
import java.time.Instant

data class SavedProjectMetadata(
    val projectId: String,
    val projectName: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Int,
)

data class SavedRouteState(
    val routePlan: RoutePlan,
    val selectedMode: String,
    val selectedPattern: String,
    val lockedSegments: Set<Int>,
    val jumpConnections: List<JumpConnection>,
    val manualModified: Boolean,
)

data class SavedControllerState(
    val controllerMapExport: ControllerMapExport,
    val controllerType: ControllerType,
    val portUsage: String,
)

data class SavedBomState(
    val billOfMaterials: BillOfMaterials,
)

data class SavedQuoteState(
    val quotation: Quotation,
    val pricingInput: PricingInput,
)

data class SavedProject(
    val metadata: SavedProjectMetadata,
    val projectUiState: ProjectUiState,
    val savedRouteState: SavedRouteState,
    val savedControllerState: SavedControllerState,
    val savedBomState: SavedBomState,
    val savedQuoteState: SavedQuoteState,
)

class ProjectPersistenceEngine(
    baseDirectory: File,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
    private val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    private val rootDir = File(baseDirectory, "projects")
    private val indexFile = File(rootDir, "index.json")

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
        if (!indexFile.exists()) writeIndex(emptyList())
    }

    fun saveProject(project: SavedProject) {
        val normalized = normalizeForSave(project)
        writeProjectFile(normalized)
        upsertMetadata(normalized.metadata)
    }

    fun loadProject(projectId: String): SavedProject? {
        val file = projectFile(projectId)
        if (!file.exists()) return null
        val json = file.readText()
        val wrapper = gson.fromJson(json, SavedProjectDocument::class.java) ?: return null
        val migrated = migrateIfNeeded(wrapper)
        return migrated.project
    }

    fun updateProject(project: SavedProject) {
        val existing = loadProject(project.metadata.projectId)
        require(existing != null) { "Project not found: ${project.metadata.projectId}" }
        val updated = project.copy(
            metadata = project.metadata.copy(
                createdAt = existing.metadata.createdAt,
                updatedAt = nowIso(),
                version = schemaVersion,
            ),
        )
        saveProject(updated)
    }

    fun deleteProject(projectId: String) {
        projectFile(projectId).takeIf { it.exists() }?.delete()
        val metadata = readIndex().filterNot { it.projectId == projectId }
        writeIndex(metadata)
    }

    fun listProjects(): List<SavedProjectMetadata> = readIndex().sortedByDescending { it.updatedAt }

    private fun normalizeForSave(project: SavedProject): SavedProject {
        val now = nowIso()
        val metadata = project.metadata.copy(
            createdAt = project.metadata.createdAt.ifBlank { now },
            updatedAt = now,
            version = schemaVersion,
        )
        val routeState = project.savedRouteState.copy(
            jumpConnections = if (project.savedRouteState.jumpConnections.isNotEmpty()) {
                project.savedRouteState.jumpConnections
            } else {
                project.savedRouteState.routePlan.effectiveJumpConnections()
            },
        )
        return project.copy(metadata = metadata, savedRouteState = routeState)
    }

    private fun writeProjectFile(project: SavedProject) {
        val file = projectFile(project.metadata.projectId)
        val document = SavedProjectDocument(version = schemaVersion, project = project)
        file.writeText(gson.toJson(document))
    }

    private fun projectFile(projectId: String): File = File(rootDir, "$projectId.json")

    private fun upsertMetadata(metadata: SavedProjectMetadata) {
        val current = readIndex().toMutableList()
        val i = current.indexOfFirst { it.projectId == metadata.projectId }
        if (i >= 0) current[i] = metadata else current.add(metadata)
        writeIndex(current)
    }

    private fun readIndex(): List<SavedProjectMetadata> {
        if (!indexFile.exists()) return emptyList()
        val text = indexFile.readText()
        if (text.isBlank()) return emptyList()
        val type = object : TypeToken<List<SavedProjectMetadata>>() {}.type
        return gson.fromJson(text, type) ?: emptyList()
    }

    private fun writeIndex(metadata: List<SavedProjectMetadata>) {
        indexFile.writeText(gson.toJson(metadata))
    }

    private fun migrateIfNeeded(document: SavedProjectDocument): SavedProjectDocument {
        if (document.version == schemaVersion) return document
        // Placeholder migration chain for future schema upgrades.
        return document.copy(version = schemaVersion)
    }

    private fun nowIso(): String = Instant.now().toString()

    private data class SavedProjectDocument(
        val version: Int,
        val project: SavedProject,
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
