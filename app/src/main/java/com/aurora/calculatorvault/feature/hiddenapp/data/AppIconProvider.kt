package com.aurora.calculatorvault.feature.hiddenapp.data

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppIconProvider {
    suspend fun load(packageName: String): Drawable?
}

class CachedPackageManagerIconProvider(
    private val packageManager: PackageManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppIconProvider {
    private val cache = LruCache<String, Drawable.ConstantState>(MAX_CACHE_ENTRIES)

    override suspend fun load(packageName: String): Drawable? = withContext(dispatcher) {
        cache.get(packageName)?.newDrawable()?.let { return@withContext it }
        runCatching { packageManager.getApplicationIcon(packageName) }
            .getOrNull()
            ?.also { drawable ->
                drawable.constantState?.let { cache.put(packageName, it) }
            }
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 48
    }
}
