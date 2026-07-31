package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepositoryContract
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestError
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestPinShortcutUseCaseTest {
    @Test
    fun `unsupported launcher does not submit request`() = runTest {
        val repository = FakeRepository()
        val creator = FakeCreator(supported = false)
        val result = RequestPinShortcutUseCase(repository, creator)(1)

        assertEquals(PinShortcutRequestResult.Unsupported, result)
        assertEquals(0, creator.requestCount)
        assertEquals(ShortcutRequestState.Unsupported, repository.state)
    }

    @Test
    fun `valid configuration submits and records request without sensitive target data`() = runTest {
        val repository = FakeRepository()
        val creator = FakeCreator()
        val result = RequestPinShortcutUseCase(repository, creator) { 1234 }(1)

        assertEquals(PinShortcutRequestResult.RequestSubmitted, result)
        assertEquals(1, creator.requestCount)
        assertEquals("cv_disguise_random", creator.lastRequest?.shortcutId)
        assertEquals("Work", creator.lastRequest?.displayName)
        assertEquals(ShortcutRequestState.RequestSubmitted, repository.state)
        assertEquals(1234L, repository.requestedAt)
        assertFalse(creator.lastRequest.toString().contains("target.package"))
    }

    @Test
    fun `empty name is invalid and never submitted`() = runTest {
        val repository = FakeRepository(entry = entry(customName = "   "))
        val creator = FakeCreator()

        assertEquals(
            PinShortcutRequestResult.InvalidConfiguration,
            RequestPinShortcutUseCase(repository, creator)(1),
        )
        assertEquals(0, creator.requestCount)
    }

    @Test
    fun `immediate rejection records failed state`() = runTest {
        val repository = FakeRepository()
        val creator = FakeCreator(
            result = PinShortcutRequestResult.RequestRejectedImmediately,
        )

        assertEquals(
            PinShortcutRequestResult.RequestRejectedImmediately,
            RequestPinShortcutUseCase(repository, creator)(1),
        )
        assertEquals(ShortcutRequestState.Failed, repository.state)
        assertEquals(ShortcutRequestError.RequestRejected, repository.error)
    }

    @Test
    fun `submitted request remains submitted when no callback arrives`() = runTest {
        val repository = FakeRepository()
        RequestPinShortcutUseCase(repository, FakeCreator())(1)

        assertEquals(ShortcutRequestState.RequestSubmitted, repository.state)
        assertEquals(null, repository.entry.shortcutCallbackAt)
    }

    @Test
    fun `state write failure does not retract submitted system request`() = runTest {
        val repository = FakeRepository(saveSucceeds = false)
        val creator = FakeCreator()

        assertEquals(
            PinShortcutRequestResult.RequestSubmittedStateSaveFailed,
            RequestPinShortcutUseCase(repository, creator)(1),
        )
        assertEquals(1, creator.requestCount)
    }

    @Test
    fun `concurrent clicks only submit one request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val creator = FakeCreator(gate = gate)
        val useCase = RequestPinShortcutUseCase(FakeRepository(), creator)

        val first = async { useCase(1) }
        val second = async { useCase(1) }
        assertEquals(PinShortcutRequestResult.AlreadyRequesting, second.await())
        gate.complete(Unit)
        assertEquals(PinShortcutRequestResult.RequestSubmitted, first.await())
        assertEquals(1, creator.requestCount)
    }

    @Test
    fun `uuid shortcut ids use opaque prefix and are unique`() {
        val generator = com.aurora.calculatorvault.feature.disguise.data.UuidShortcutIdGenerator()
        val first = generator.generate()
        val second = generator.generate()

        assertTrue(first.startsWith("cv_disguise_"))
        assertTrue(second.startsWith("cv_disguise_"))
        assertNotEquals(first, second)
        assertFalse(first.contains("target.package"))
        assertFalse(first.contains("Work"))
    }

    private class FakeCreator(
        private val supported: Boolean = true,
        private val result: PinShortcutRequestResult =
            PinShortcutRequestResult.RequestSubmitted,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : PinnedShortcutCreator {
        var requestCount = 0
        var lastRequest: PinShortcutRequest? = null

        override fun isSupported() = supported

        override suspend fun requestPinShortcut(
            request: PinShortcutRequest,
        ): PinShortcutRequestResult {
            requestCount += 1
            lastRequest = request
            gate?.await()
            return result
        }
    }

    private class FakeRepository(
        val entry: DisguiseEntry = entry(),
        private val saveSucceeds: Boolean = true,
    ) : DisguiseEntryRepositoryContract {
        var state = entry.shortcutRequestState
        var requestedAt: Long? = null
        var error: ShortcutRequestError? = null

        override fun observeEntries() = MutableStateFlow(listOf(entry))
        override suspend fun scanInstalledApps() = emptyList<com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp>()
        override suspend fun create(
            packageName: String,
            targetAppName: String,
            customName: String,
            iconId: DisguiseIconId,
        ) = 1L
        override suspend fun update(
            id: Long,
            packageName: String,
            targetAppName: String,
            customName: String,
            iconId: DisguiseIconId,
        ) = true
        override suspend fun delete(id: Long) = true
        override suspend fun findById(id: Long) = entry.takeIf { id == entry.id }
        override suspend fun ensureShortcutId(id: Long) = "cv_disguise_random"
        override suspend fun updateShortcutRequest(
            id: Long,
            state: ShortcutRequestState,
            requestedAt: Long?,
            error: ShortcutRequestError?,
        ): Boolean {
            this.state = state
            this.requestedAt = requestedAt
            this.error = error
            return saveSucceeds
        }
    }

    private companion object {
        fun entry(customName: String = "Work") = DisguiseEntry(
            id = 1,
            packageName = "target.package",
            targetAppName = "Target",
            customName = customName,
            iconId = DisguiseIconId.Files,
            createdAt = 1,
            updatedAt = 1,
        )
    }
}
