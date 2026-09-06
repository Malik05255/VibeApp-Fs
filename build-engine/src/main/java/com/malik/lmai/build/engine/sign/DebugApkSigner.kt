package com.malik.lmai.build.engine.sign

import android.content.Context
import com.android.apksig.ApkSigner
import com.tyron.common.util.Decompress
import com.malik.lmai.build.engine.internal.BuildStep
import com.malik.lmai.build.engine.internal.BuildWorkspace
import com.malik.lmai.build.engine.internal.RecordingLogger
import com.malik.lmai.build.engine.model.BuildArtifact
import com.malik.lmai.build.engine.model.BuildResult
import com.malik.lmai.build.engine.model.BuildStage
import com.malik.lmai.build.engine.model.CompileInput
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

class DebugApkSigner(
    context: Context,
) : BuildStep(context, BuildStage.SIGN), com.malik.lmai.build.engine.pipeline.ApkSigner {

    override suspend fun sign(input: CompileInput): BuildResult = run(input)

    override suspend fun execute(
        input: CompileInput,
        workspace: BuildWorkspace,
        logger: RecordingLogger,
    ): BuildResult {
        require(workspace.unsignedApk.exists()) { "Unsigned APK not found: ${workspace.unsignedApk.absolutePath}" }

        val keyFile = resolveSigningKey(input)
        val certFile = resolveSigningCert(input)
        val privateKey = loadPrivateKey(keyFile)
        val certificate = loadCertificate(certFile)
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "lmai-debug",
            privateKey,
            listOf(certificate),
        ).build()

        if (workspace.signedApk.exists()) {
            workspace.signedApk.delete()
        }

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(workspace.unsignedApk)
            .setOutputApk(workspace.signedApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setMinSdkVersion(input.minSdk)
            .build()
            .sign()

        return BuildResult.success(
            artifacts = listOf(
                BuildArtifact(
                    stage = BuildStage.SIGN,
                    path = workspace.signedApk.absolutePath,
                    description = "Signed APK ready for installation",
                ),
            ),
            logs = logger.entries,
        )
    }

    private fun resolveSigningKey(input: CompileInput): File {
        return input.signingConfig.privateKeyPk8Path?.let(::File) ?: extractBundledAsset(
            "testkey.pk8.zip",
            File(context.filesDir, "build-engine/signing/testkey.pk8"),
        )
    }

    private fun resolveSigningCert(input: CompileInput): File {
        return input.signingConfig.certificatePemPath?.let(::File) ?: extractBundledAsset(
            "testkey.x509.pem.zip",
            File(context.filesDir, "build-engine/signing/testkey.x509.pem"),
        )
    }

    private fun extractBundledAsset(assetName: String, outputFile: File): File {
        if (outputFile.exists()) {
            return outputFile
        }
        outputFile.parentFile?.mkdirs()
        Decompress.unzipFromAssets(context, assetName, outputFile.parentFile?.absolutePath)
        return outputFile
    }

    private fun loadPrivateKey(file: File): PrivateKey {
        val encoded = file.readBytes()
        val keySpec = PKCS8EncodedKeySpec(encoded)
        val algorithms = listOf("RSA", "EC", "DSA")
        for (algorithm in algorithms) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec)
            } catch (_: Exception) {
            }
        }
        throw IllegalArgumentException("Unsupported private key format: ${file.absolutePath}")
    }

    private fun loadCertificate(file: File): X509Certificate {
        FileInputStream(file).use { stream ->
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(stream) as X509Certificate
        }
    }
}
