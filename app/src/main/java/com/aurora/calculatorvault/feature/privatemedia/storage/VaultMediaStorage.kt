package com.aurora.calculatorvault.feature.privatemedia.storage

import android.content.Context
import java.io.File

class VaultMediaStorage(
    context: Context,
    private val directoryName: String = DIRECTORY_NAME,
) {
    private val appContext = context.applicationContext

    fun ensureRoot(): File? {
        val root = File(appContext.filesDir, directoryName)
        return if (root.exists() || root.mkdirs()) root else null
    }

    fun privateFile(fileName: String): File? =
        ensureRoot()?.let { File(it, fileName) }

    fun tempFile(fileName: String): File? =
        ensureRoot()?.let { File(it, "$fileName.tmp") }

    fun deletePrivateFile(fileName: String): Boolean {
        val file = privateFile(fileName) ?: return false
        return !file.exists() || file.delete()
    }

    fun deleteQuietly(fileName: String) {
        runCatching { privateFile(fileName)?.delete() }
        runCatching { tempFile(fileName)?.delete() }
    }

    companion object {
        const val DIRECTORY_NAME = "vault_media"
    }
}
