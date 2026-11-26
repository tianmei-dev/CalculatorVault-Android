package com.aurora.calculatorvault.feature.legal.data

import android.content.Context
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LegalDocumentRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun read(type: LegalDocumentType): Result<String> = readAsset(type.assetPath)

    internal suspend fun readCollectedInformation(): Result<String> =
        readAsset("legal/collected_personal_information_list.txt")

    private suspend fun readAsset(assetPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
