package com.aurora.calculatorvault.feature.legal.data

import android.content.Context
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LegalDocumentRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun read(type: LegalDocumentType): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            appContext.assets.open(type.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
