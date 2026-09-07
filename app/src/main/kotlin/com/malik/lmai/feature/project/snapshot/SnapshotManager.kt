package com.malik.lmai.feature.project.snapshot

import com.malik.lmai.feature.project.LmaiProjectDirs
import java.io.File

interface SnapshotManager {
    suspend fun prepare(
        projectId: String,
        workspaceRoot: File,
        vibeDirs: LmaiProjectDirs,
        type: SnapshotType,
        label: String,
        turnIndex: Int?,
    ): SnapshotHandle

    suspend fun list(projectId: String, vibeDirs: LmaiProjectDirs): List<Snapshot>

    suspend fun restore(
        snapshotId: String,
        projectId: String,
        workspaceRoot: File,
        vibeDirs: LmaiProjectDirs,
        createBackup: Boolean = true,
    ): RestoreResult

    suspend fun delete(
        snapshotId: String,
        projectId: String,
        vibeDirs: LmaiProjectDirs,
    )

    suspend fun enforceRetention(
        projectId: String,
        vibeDirs: LmaiProjectDirs,
        keepTurnCount: Int = 20,
    )

    /**
     * If `.vibe/snapshots/.pending_restore` exists from a previous interrupted
     * restore, replay the restore to reach a consistent workspace state and delete
     * the marker. Safe to call on every startup — a no-op when the marker is absent.
     */
    suspend fun recoverPendingRestore(
        projectId: String,
        workspaceRoot: File,
        vibeDirs: LmaiProjectDirs,
    )
}

/**
 * Per-turn lazy-commit primitive. `prepare()` returns a handle without any disk I/O.
 * The handle only dumps the workspace on [commit], and only appends to the index
 * on [finalize]. If neither is called (e.g. a read-only turn), the handle leaves
 * no trace.
 */
interface SnapshotHandle {
    val id: String
    suspend fun commit()
    suspend fun finalize(
        buildSucceeded: Boolean,
        affectedFiles: List<String>,
        deletedFiles: List<String>,
    )
}

interface Clock {
    fun nowEpochMs(): Long
}

interface SnapshotIdGenerator {
    fun generate(): String
}
