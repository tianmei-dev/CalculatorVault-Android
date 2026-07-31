package com.aurora.calculatorvault.feature.disguise.data

import androidx.room.withTransaction
import com.aurora.calculatorvault.core.database.CalculatorVaultDatabase
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestError
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

fun interface ShortcutIdGenerator {
    fun generate(): String
}

class UuidShortcutIdGenerator : ShortcutIdGenerator {
    override fun generate(): String = "cv_disguise_${UUID.randomUUID()}"
}

interface DisguiseEntryRepositoryContract {
    fun observeEntries(): Flow<List<DisguiseEntry>>
    suspend fun scanInstalledApps(): List<InstalledApp>
    suspend fun create(
        packageName: String,
        targetAppName: String,
        customName: String,
        iconId: DisguiseIconId,
    ): Long
    suspend fun update(
        id: Long,
        packageName: String,
        targetAppName: String,
        customName: String,
        iconId: DisguiseIconId,
    ): Boolean
    suspend fun delete(id: Long): Boolean
    suspend fun findById(id: Long): DisguiseEntry? = null
    suspend fun findByShortcutId(shortcutId: String): DisguiseEntry? = null
    suspend fun ensureShortcutId(id: Long): String? = null
    suspend fun updateShortcutRequest(
        id: Long,
        state: ShortcutRequestState,
        requestedAt: Long?,
        error: ShortcutRequestError?,
    ): Boolean = false
    suspend fun markShortcutAccepted(shortcutId: String, callbackAt: Long): Boolean = false
}

class DisguiseEntryRepository(
    private val database: CalculatorVaultDatabase,
    private val scanner: InstalledAppScanner,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val shortcutIdGenerator: ShortcutIdGenerator = UuidShortcutIdGenerator(),
) : DisguiseEntryRepositoryContract {
    private val dao = database.disguiseEntryDao()
    private val shortcutIdMutex = Mutex()

    override fun observeEntries(): Flow<List<DisguiseEntry>> =
        dao.observeAll().map { entries -> entries.map { it.toDomain() } }

    override suspend fun scanInstalledApps(): List<InstalledApp> = scanner.scan()

    override suspend fun create(
        packageName: String,
        targetAppName: String,
        customName: String,
        iconId: DisguiseIconId,
    ): Long {
        val now = currentTimeMillis()
        return dao.insert(
            DisguiseEntryEntity(
                packageName = packageName,
                targetAppName = targetAppName,
                customName = customName,
                iconId = iconId.name,
                createdAt = now,
                updatedAt = now,
                shortcutId = shortcutIdGenerator.generate(),
            ),
        )
    }

    override suspend fun update(
        id: Long,
        packageName: String,
        targetAppName: String,
        customName: String,
        iconId: DisguiseIconId,
    ): Boolean = database.withTransaction {
        val existing = dao.findById(id) ?: return@withTransaction false
        dao.update(
            existing.copy(
                packageName = packageName,
                targetAppName = targetAppName,
                customName = customName,
                iconId = iconId.name,
                updatedAt = currentTimeMillis(),
            ),
        ) == 1
    }

    override suspend fun delete(id: Long): Boolean = dao.deleteById(id) == 1

    override suspend fun findById(id: Long): DisguiseEntry? = dao.findById(id)?.toDomain()

    override suspend fun findByShortcutId(shortcutId: String): DisguiseEntry? =
        dao.findByShortcutId(shortcutId)?.toDomain()

    override suspend fun ensureShortcutId(id: Long): String? = shortcutIdMutex.withLock {
        val existing = dao.findById(id) ?: return@withLock null
        existing.shortcutId?.let { return@withLock it }
        val generated = shortcutIdGenerator.generate()
        dao.setShortcutIdIfMissing(id, generated)
        dao.findById(id)?.shortcutId
    }

    override suspend fun updateShortcutRequest(
        id: Long,
        state: ShortcutRequestState,
        requestedAt: Long?,
        error: ShortcutRequestError?,
    ): Boolean = dao.updateShortcutRequest(
        id = id,
        state = state.toStorageValue(),
        requestedAt = requestedAt,
        lastError = error?.toStorageValue(),
    ) == 1

    override suspend fun markShortcutAccepted(
        shortcutId: String,
        callbackAt: Long,
    ): Boolean = dao.markShortcutAccepted(shortcutId, callbackAt) == 1

    private fun DisguiseEntryEntity.toDomain() = DisguiseEntry(
        id = id,
        packageName = packageName,
        targetAppName = targetAppName,
        customName = customName,
        iconId = runCatching { DisguiseIconId.valueOf(iconId) }
            .getOrDefault(DisguiseIconId.Files),
        createdAt = createdAt,
        updatedAt = updatedAt,
        shortcutId = shortcutId,
        shortcutRequestState = shortcutRequestState.toShortcutRequestState(),
        shortcutRequestedAt = shortcutRequestedAt,
        shortcutCallbackAt = shortcutCallbackAt,
        shortcutLastError = shortcutLastError?.toShortcutRequestError(),
    )
}

private fun ShortcutRequestState.toStorageValue(): String = when (this) {
    ShortcutRequestState.NotRequested -> "NOT_REQUESTED"
    ShortcutRequestState.RequestSubmitted -> "REQUEST_SUBMITTED"
    ShortcutRequestState.LauncherAccepted -> "LAUNCHER_ACCEPTED"
    ShortcutRequestState.Unsupported -> "UNSUPPORTED"
    ShortcutRequestState.Failed -> "FAILED"
}

private fun ShortcutRequestError.toStorageValue(): String = when (this) {
    ShortcutRequestError.InvalidConfiguration -> "INVALID_CONFIGURATION"
    ShortcutRequestError.IconGenerationFailed -> "ICON_GENERATION_FAILED"
    ShortcutRequestError.RequestRejected -> "REQUEST_REJECTED"
    ShortcutRequestError.SecurityBlocked -> "SECURITY_BLOCKED"
    ShortcutRequestError.StateSaveFailed -> "STATE_SAVE_FAILED"
    ShortcutRequestError.Unknown -> "UNKNOWN"
}

private fun String.toShortcutRequestState(): ShortcutRequestState = when (this) {
    "REQUEST_SUBMITTED" -> ShortcutRequestState.RequestSubmitted
    "LAUNCHER_ACCEPTED" -> ShortcutRequestState.LauncherAccepted
    "UNSUPPORTED" -> ShortcutRequestState.Unsupported
    "FAILED" -> ShortcutRequestState.Failed
    else -> ShortcutRequestState.NotRequested
}

private fun String.toShortcutRequestError(): ShortcutRequestError = when (this) {
    "INVALID_CONFIGURATION" -> ShortcutRequestError.InvalidConfiguration
    "ICON_GENERATION_FAILED" -> ShortcutRequestError.IconGenerationFailed
    "REQUEST_REJECTED" -> ShortcutRequestError.RequestRejected
    "SECURITY_BLOCKED" -> ShortcutRequestError.SecurityBlocked
    "STATE_SAVE_FAILED" -> ShortcutRequestError.StateSaveFailed
    else -> ShortcutRequestError.Unknown
}
