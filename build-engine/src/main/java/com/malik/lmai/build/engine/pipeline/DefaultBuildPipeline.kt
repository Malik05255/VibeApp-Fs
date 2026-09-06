package com.malik.lmai.build.engine.pipeline

import android.content.Context
import android.util.Log
import com.malik.lmai.build.engine.internal.BuildWorkspace
import com.malik.lmai.build.engine.internal.BuildWorkspacePreparer
import com.malik.lmai.build.engine.model.BuildArtifact
import com.malik.lmai.build.engine.model.BuildMode
import com.malik.lmai.build.engine.model.BuildResult
import com.malik.lmai.build.engine.model.BuildStage
import com.malik.lmai.build.engine.model.BuildStatus
import com.malik.lmai.build.engine.model.CompileInput
import com.malik.lmai.build.engine.release.GeneratedAppReleaseManager
import java.io.File
import java.security.MessageDigest

class DefaultBuildPipeline(
    private val context: Context,
    private val resourceCompiler: ResourceCompiler,
    private val compiler: Compiler,
    private val dexConverter: DexConverter,
    private val apkBuilder: ApkBuilder,
    private val apkSigner: ApkSigner,
) : BuildPipeline {

    private val tag = "BuildEngine-Pipeline"

    override suspend fun run(
        input: CompileInput,
        progressListener: BuildProgressListener?,
    ): BuildResult {
        Log.d(tag, "Pipeline start for ${input.projectId} at ${input.workingDirectory}")
        val workspace = BuildWorkspacePreparer.prepare(context, input)

        // Preview/install is requested very frequently from the chat screen. If no project
        // input changed since the last successful build, launching the already-signed APK is
        // both correct and dramatically faster than recompiling resources/classes/dex again.
        val currentFingerprint = workspaceFingerprint(workspace.rootDir)
        if (isReusableBuild(workspace, currentFingerprint)) {
            Log.d(tag, "Reusing unchanged signed APK for ${input.projectId}: ${workspace.signedApk}")
            progressListener?.onProgress(
                BuildProgressUpdate(
                    stage = BuildStage.SIGN,
                    completedSteps = PIPELINE_STEP_COUNT,
                    totalSteps = PIPELINE_STEP_COUNT,
                    state = BuildProgressState.COMPLETED,
                ),
            )
            return BuildResult.success(
                artifacts = listOf(
                    BuildArtifact(
                        stage = BuildStage.SIGN,
                        path = workspace.signedApk.absolutePath,
                        description = "Cached signed APK (project unchanged)",
                    ),
                ) + BuildWorkspace.pipelineArtifacts(workspace),
                logs = emptyList(),
            )
        }

        if (input.cleanOutput) {
            Log.d(tag, "Cleaning build directory: ${workspace.buildDir.absolutePath}")
            workspace.buildDir.deleteRecursively()
        }

        val preparedRelease = if (input.buildMode == BuildMode.STANDALONE) {
            GeneratedAppReleaseManager.prepare(
                manifestFile = File(input.workingDirectory, "src/main/AndroidManifest.xml"),
                expectedPackageName = input.packageName,
            )
        } else {
            null
        }

        var buildSucceeded = false
        try {
            preparedRelease?.let {
                Log.d(
                    tag,
                    "Prepared installable update: package=${it.packageName}, versionCode=${it.versionCode}",
                )
            }

            val stepRunners = listOf(
                BuildStage.RESOURCE to suspend { resourceCompiler.compile(input) },
                BuildStage.COMPILE to suspend { compiler.compile(input) },
                BuildStage.DEX to suspend { dexConverter.convert(input) },
                BuildStage.PACKAGE to suspend { apkBuilder.build(input) },
                BuildStage.SIGN to suspend { apkSigner.sign(input) },
            )
            val stepResults = mutableListOf<BuildResult>()
            val totalSteps = stepRunners.size

            stepRunners.forEachIndexed { index, (stage, step) ->
                progressListener?.onProgress(
                    BuildProgressUpdate(
                        stage = stage,
                        completedSteps = index,
                        totalSteps = totalSteps,
                        state = BuildProgressState.STARTED,
                    ),
                )
                val result = step()
                stepResults += result
                Log.d(
                    tag,
                    "Step ${index + 1}/$totalSteps finished with status=${result.status}, error=${result.errorMessage}",
                )
                if (result.status == BuildStatus.FAILED) {
                    Log.e(tag, "Pipeline failed: ${result.errorMessage}")
                    return BuildResult(
                        status = BuildStatus.FAILED,
                        artifacts = stepResults.flatMap { it.artifacts },
                        logs = stepResults.flatMap { it.logs },
                        errorMessage = result.errorMessage,
                    )
                }
                progressListener?.onProgress(
                    BuildProgressUpdate(
                        stage = stage,
                        completedSteps = index + 1,
                        totalSteps = totalSteps,
                        state = BuildProgressState.COMPLETED,
                    ),
                )
            }

            buildSucceeded = true
            // The build itself updates AndroidManifest versionCode for installable updates.
            // Recompute after success so the saved fingerprint matches the exact on-disk state
            // that produced this signed APK.
            saveFingerprint(workspace, workspaceFingerprint(workspace.rootDir))
            Log.d(tag, "Pipeline succeeded")
            return BuildResult.success(
                artifacts = stepResults.flatMap { it.artifacts } + BuildWorkspace.pipelineArtifacts(workspace),
                logs = stepResults.flatMap { it.logs },
            )
        } finally {
            if (!buildSucceeded) {
                preparedRelease?.rollback()
                if (preparedRelease != null) {
                    Log.d(tag, "Build failed; restored previous manifest version/package identity")
                }
            }
        }
    }

    private fun isReusableBuild(workspace: BuildWorkspace, currentFingerprint: String): Boolean {
        if (!workspace.signedApk.exists() || workspace.signedApk.length() <= 0L) return false
        val marker = fingerprintFile(workspace)
        if (!marker.exists()) return false
        return runCatching { marker.readText().trim() == currentFingerprint }.getOrDefault(false)
    }

    private fun saveFingerprint(workspace: BuildWorkspace, fingerprint: String) {
        runCatching {
            val marker = fingerprintFile(workspace)
            marker.parentFile?.mkdirs()
            marker.writeText(fingerprint)
        }.onFailure {
            // Cache failure must never turn a successful APK build into a failed build.
            Log.w(tag, "Unable to persist build fingerprint", it)
        }
    }

    private fun fingerprintFile(workspace: BuildWorkspace): File =
        File(workspace.rootDir, FINGERPRINT_FILE_NAME)

    private fun workspaceFingerprint(root: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        if (!root.exists()) return "missing"

        val files = root.walkTopDown()
            .onEnter { dir ->
                dir == root || (dir.name != "build" && dir.name != ".gradle")
            }
            .filter { file ->
                file.isFile && file.name != FINGERPRINT_FILE_NAME
            }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .toList()

        files.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            digest.update(relative.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(file.lastModified().toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }

        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private companion object {
        const val PIPELINE_STEP_COUNT = 5
        const val FINGERPRINT_FILE_NAME = ".vibe-build-fingerprint"
    }
}
