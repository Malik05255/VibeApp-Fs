package com.almi.ai.update

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Streaming patch format used by ALMI self-updates.
 *
 * The patch never contains the whole target APK unless every target block changed. COPY operations
 * reference byte ranges from the currently installed APK and DATA operations carry only bytes that
 * are new. This keeps update traffic proportional to changed code/resources while reconstructing the
 * exact signed target APK byte-for-byte before Android's package installer sees it.
 */
internal object AlmiDeltaPatch {
    private val MAGIC = byteArrayOf('A'.code.toByte(), 'L'.code.toByte(), 'M'.code.toByte(), 'I'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
    private const val FORMAT_VERSION = 1
    private const val OP_COPY = 0
    private const val OP_DATA = 1
    private const val BUFFER_SIZE = 256 * 1024

    data class Header(
        val baseSize: Long,
        val targetSize: Long,
        val baseSha256: String,
        val targetSha256: String,
        val operationCount: Int,
    )

    fun readHeader(patch: File): Header = DataInputStream(BufferedInputStream(FileInputStream(patch))).use { input ->
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        check(magic.contentEquals(MAGIC)) { "Not an ALMI delta patch" }
        check(input.readInt() == FORMAT_VERSION) { "Unsupported ALMI delta patch version" }
        val baseSize = input.readLong()
        val targetSize = input.readLong()
        val baseHash = ByteArray(32).also { input.readFully(it) }
        val targetHash = ByteArray(32).also { input.readFully(it) }
        val operationCount = input.readInt()
        check(baseSize >= 0L && targetSize >= 0L && operationCount >= 0) { "Invalid ALMI delta header" }
        Header(baseSize, targetSize, baseHash.hex(), targetHash.hex(), operationCount)
    }

    fun apply(baseApk: File, patch: File, targetApk: File, onProgress: (Float) -> Unit = {}) {
        val expected = readHeader(patch)
        check(baseApk.isFile && baseApk.length() == expected.baseSize) { "Installed APK size does not match patch base" }
        check(sha256(baseApk).equals(expected.baseSha256, ignoreCase = true)) { "Installed APK hash does not match patch base" }

        targetApk.parentFile?.mkdirs()
        val temporary = File(targetApk.parentFile, "${targetApk.name}.partial")
        if (temporary.exists()) temporary.delete()

        DataInputStream(BufferedInputStream(FileInputStream(patch), BUFFER_SIZE)).use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            check(magic.contentEquals(MAGIC))
            check(input.readInt() == FORMAT_VERSION)
            input.readLong()
            input.readLong()
            input.skipExactly(32L + 32L)
            val operationCount = input.readInt()

            RandomAccessFile(baseApk, "r").use { base ->
                BufferedOutputStream(FileOutputStream(temporary), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = 0L
                    repeat(operationCount) {
                        when (val type = input.readUnsignedByte()) {
                            OP_COPY -> {
                                val offset = input.readLong()
                                var remaining = input.readInt().also { check(it >= 0) }
                                check(offset >= 0L && offset + remaining <= base.length()) { "COPY outside base APK" }
                                base.seek(offset)
                                while (remaining > 0) {
                                    val count = minOf(remaining, buffer.size)
                                    base.readFully(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                    written += count
                                    onProgress((written.toDouble() / expected.targetSize.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f))
                                }
                            }
                            OP_DATA -> {
                                var remaining = input.readInt().also { check(it >= 0) }
                                while (remaining > 0) {
                                    val count = minOf(remaining, buffer.size)
                                    input.readFully(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                    written += count
                                    onProgress((written.toDouble() / expected.targetSize.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f))
                                }
                            }
                            else -> error("Unknown ALMI delta operation $type")
                        }
                    }
                    output.flush()
                    check(written == expected.targetSize) { "Patched APK length mismatch: $written / ${expected.targetSize}" }
                }
            }
        }

        check(sha256(temporary).equals(expected.targetSha256, ignoreCase = true)) { "Patched APK SHA-256 verification failed" }
        if (targetApk.exists()) targetApk.delete()
        check(temporary.renameTo(targetApk)) { "Could not finalize patched APK" }
        onProgress(1f)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().hex()
    }

    private fun DataInputStream.skipExactly(count: Long) {
        var remaining = count
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) remaining -= skipped else {
                check(read() >= 0) { "Unexpected end of patch" }
                remaining -= 1L
            }
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
