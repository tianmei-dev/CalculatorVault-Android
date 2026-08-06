package com.aurora.calculatorvault.feature.disguise.shortcut

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShortcutUpdateRequest(
    val shortcutId: String,
    val displayName: String,
    val iconId: DisguiseIconId,
)

interface ShortcutRepository {
    fun isPinRequestSupported(): Boolean
    suspend fun isShortcutPresent(shortcutId: String): Boolean
    suspend fun update(request: ShortcutUpdateRequest): ShortcutOperationResult
    suspend fun remove(shortcutId: String): ShortcutOperationResult
}

class AndroidShortcutRepository(
    private val context: Context,
    private val iconFactory: DisguiseShortcutIconFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShortcutRepository {
    override fun isPinRequestSupported(): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    override suspend fun isShortcutPresent(shortcutId: String): Boolean =
        withContext(ioDispatcher) {
            findShortcut(shortcutId) != null
        }

    override suspend fun update(request: ShortcutUpdateRequest): ShortcutOperationResult =
        withContext(ioDispatcher) {
            val label = request.displayName.trim()
            if (label.isEmpty()) return@withContext ShortcutOperationResult.Failed
            val icon = iconFactory.create(request.iconId)
                ?: return@withContext ShortcutOperationResult.IconGenerationFailed
            val info = ShortcutInfoCompat.Builder(context, request.shortcutId)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(DisguiseShortcutContract.createEntryIntent(context, request.shortcutId))
                .build()
            try {
                if (ShortcutManagerCompat.updateShortcuts(context, listOf(info))) {
                    ShortcutOperationResult.Success
                } else {
                    ShortcutOperationResult.Failed
                }
            } catch (_: IllegalArgumentException) {
                ShortcutOperationResult.NotFound
            } catch (_: SecurityException) {
                ShortcutOperationResult.SecurityBlocked
            } catch (_: RuntimeException) {
                ShortcutOperationResult.Failed
            }
        }

    override suspend fun remove(shortcutId: String): ShortcutOperationResult =
        withContext(ioDispatcher) {
            try {
                if (!isShortcutPresent(shortcutId)) return@withContext ShortcutOperationResult.Success
                ShortcutManagerCompat.disableShortcuts(context, listOf(shortcutId), null)
                ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
                ShortcutOperationResult.ManualRemovalRequired
            } catch (_: IllegalArgumentException) {
                ShortcutOperationResult.Success
            } catch (_: SecurityException) {
                ShortcutOperationResult.SecurityBlocked
            } catch (_: RuntimeException) {
                ShortcutOperationResult.Failed
            }
        }

    private fun findShortcut(shortcutId: String): ShortcutInfoCompat? {
        val flags = ShortcutManagerCompat.FLAG_MATCH_PINNED or
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
        return try {
            ShortcutManagerCompat.getShortcuts(context, flags)
                .firstOrNull { it.id == shortcutId }
        } catch (_: RuntimeException) {
            null
        }
    }
}
