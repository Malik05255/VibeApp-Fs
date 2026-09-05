package com.vibe.app.feature.agent.tool

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectContextEngineTest {

    private val engine = ProjectContextEngine()

    @Test
    fun `auth query ranks oauth implementation ahead of unrelated UI`() {
        withProject { root ->
            write(
                root,
                "src/main/kotlin/demo/auth/OpenRouterOAuthCoordinator.kt",
                """
                package demo.auth
                class OpenRouterOAuthCoordinator {
                    fun exchangeAuthorizationCode(code: String) = code
                }
                """.trimIndent(),
            )
            write(
                root,
                "src/main/kotlin/demo/ui/HomeScreen.kt",
                "package demo.ui\nclass HomeScreen",
            )
            write(root, "src/main/AndroidManifest.xml", "<manifest />")

            val result = engine.select(
                root,
                "Fix OpenRouter OAuth authorization code exchange",
                maxFiles = 5,
            )

            assertTrue(result.selectedFiles.isNotEmpty())
            assertEquals(
                "src/main/kotlin/demo/auth/OpenRouterOAuthCoordinator.kt",
                result.selectedFiles.first().path,
            )
            assertTrue(result.selectedFiles.first().symbols.contains("OpenRouterOAuthCoordinator"))
            assertTrue(result.selectedFiles.first().excerpts.isNotEmpty())
        }
    }

    @Test
    fun `build query promotes gradle files`() {
        withProject { root ->
            write(root, "app/build.gradle.kts", "dependencies { implementation(\"x:y:1\") }")
            write(root, "app/src/main/kotlin/demo/Feature.kt", "class Feature")

            val result = engine.select(root, "Fix Gradle dependency build failure", maxFiles = 3)

            assertEquals("app/build.gradle.kts", result.selectedFiles.first().path)
            assertTrue(result.selectedFiles.first().reasons.any { it.contains("build") || it == "project-core" })
        }
    }

    @Test
    fun `binary and generated directories are ignored`() {
        withProject { root ->
            write(root, "build/generated/Broken.kt", "class Broken OAuth")
            write(root, "src/main/kotlin/demo/Real.kt", "class Real OAuth")
            File(root, "src/main/res/drawable/image.png").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(0, 1, 2, 3))
            }

            val result = engine.select(root, "OAuth", maxFiles = 10)

            assertTrue(result.selectedFiles.any { it.path.endsWith("Real.kt") })
            assertFalse(result.selectedFiles.any { it.path.startsWith("build/") })
            assertFalse(result.selectedFiles.any { it.path.endsWith(".png") })
        }
    }

    private fun withProject(block: (File) -> Unit) {
        val root = Files.createTempDirectory("lmai-context-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun write(root: File, relativePath: String, content: String) {
        File(root, relativePath).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
    }
}
