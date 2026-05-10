package com.petesmapper.ui.export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.time.Instant
import java.util.UUID

data class AutoSaveSnapshot(
    val snapshotId: String,
    val projectId: String,
    val timestamp: String,
    val savedProject: SavedProject,
)

class AutoSaveEngine(
    private val persistence: ProjectPersistenceEngine,
    baseDirectory: File,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
    private val maxSnapshotsPerProject: Int = 10,
    val autoSaveIntervalMillis: Long = 2 * 60 * 1000L,
) {
    private val snapshotDir = File(baseDirectory, "project_snapshots")

    init {
        if (!snapshotDir.exists()) snapshotDir.mkdirs()
    }

    fun autoSave(savedProject: SavedProject): AutoSaveSnapshot {
        val snapshot = AutoSaveSnapshot(
            snapshotId = UUID.randomUUID().toString(),
            projectId = savedProject.metadata.projectId,
            timestamp = Instant.now().toString(),
            savedProject = savedProject,
        )
        val file = snapshotFile(snapshot.projectId, snapshot.snapshotId)
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(snapshot))

        pruneOldSnapshots(snapshot.projectId)
        return snapshot
    }

    fun restoreLatestSnapshot(projectId: String): SavedProject? {
        val latest = listSnapshots(projectId).maxByOrNull { it.timestamp } ?: return null
        persistence.updateProject(latest.savedProject)
        return latest.savedProject
    }

    fun listSnapshots(projectId: String): List<AutoSaveSnapshot> {
        val dir = File(snapshotDir, projectId)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { gson.fromJson(file.readText(), AutoSaveSnapshot::class.java) }.getOrNull()
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun deleteSnapshot(projectId: String, snapshotId: String): Boolean {
        val file = snapshotFile(projectId, snapshotId)
        return file.exists() && file.delete()
    }

    private fun pruneOldSnapshots(projectId: String) {
        val snapshots = listSnapshots(projectId)
        if (snapshots.size <= maxSnapshotsPerProject) return
        snapshots.drop(maxSnapshotsPerProject).forEach { old ->
            snapshotFile(projectId, old.snapshotId).delete()
        }
    }

    private fun snapshotFile(projectId: String, snapshotId: String): File {
        val dir = File(snapshotDir, projectId)
        return File(dir, "$snapshotId.json")
    }
}
