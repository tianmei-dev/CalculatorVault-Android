package com.aurora.calculatorvault.feature.disguise.shortcut

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisguiseShortcutEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun validIntent_resolvesToPasswordWithoutExposingTarget() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.acceptIntent(DisguiseShortcutIntentResult.Valid(TEST_SHORTCUT_ID))
        runCurrent()
        assertEquals(DisguiseShortcutEntryState.AwaitingPassword(), viewModel.state.value)
    }

    @Test
    fun wrongPassword_clearsInputAndDoesNotLaunch() = runTest(dispatcher) {
        var launches = 0
        val viewModel = createViewModel(
            verifier = VaultPasswordVerification { false },
            launcher = DisguisedTargetLauncher { launches += 1; LaunchDisguisedTargetResult.Success },
        )
        enterPasswordAndConfirm(viewModel)
        runCurrent()
        assertEquals(
            DisguiseShortcutEntryState.AwaitingPassword(0, passwordIncorrect = true),
            viewModel.state.value,
        )
        assertEquals(0, launches)
    }

    @Test
    fun correctPassword_launchesOnceAndFinishes() = runTest(dispatcher) {
        var launches = 0
        val viewModel = createViewModel(
            launcher = DisguisedTargetLauncher { launches += 1; LaunchDisguisedTargetResult.Success },
        )
        val effect = async { viewModel.effects.firstForTest() }
        enterPasswordAndConfirm(viewModel)
        viewModel.confirmPassword()
        runCurrent()
        assertEquals(1, launches)
        assertEquals(DisguiseShortcutEntryEffect.Finish, effect.await())
    }

    @Test
    fun newIntent_cancelsOldFlowAndUsesNewestEntry() = runTest(dispatcher) {
        val seen = mutableListOf<String>()
        val viewModel = createViewModel(
            resolver = DisguiseShortcutResolver { id -> seen += id; ResolveDisguiseShortcutResult.Ready },
        )
        val second = "cv_disguise_223e4567-e89b-42d3-a456-426614174000"
        viewModel.acceptIntent(DisguiseShortcutIntentResult.Valid(TEST_SHORTCUT_ID))
        viewModel.acceptIntent(DisguiseShortcutIntentResult.Valid(second))
        runCurrent()
        assertEquals(listOf(second), seen)
        assertEquals(DisguiseShortcutEntryState.AwaitingPassword(), viewModel.state.value)
    }

    @Test
    fun backgroundExpiry_clearsAuthorizationAndBlocksConfirm() = runTest(dispatcher) {
        var launches = 0
        val viewModel = createViewModel(
            launcher = DisguisedTargetLauncher { launches += 1; LaunchDisguisedTargetResult.Success },
        )
        viewModel.acceptIntent(DisguiseShortcutIntentResult.Valid(TEST_SHORTCUT_ID))
        runCurrent()
        repeat(4) { viewModel.inputDigit(it + 1) }
        viewModel.expire()
        viewModel.confirmPassword()
        runCurrent()
        assertEquals(DisguiseShortcutEntryState.SessionExpired, viewModel.state.value)
        assertEquals(0, launches)
    }

    @Test
    fun cancel_emitsFinishAndClearsSession() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val effect = async { viewModel.effects.firstForTest() }
        viewModel.acceptIntent(DisguiseShortcutIntentResult.Valid(TEST_SHORTCUT_ID))
        advanceUntilIdle()
        viewModel.cancel()
        advanceUntilIdle()
        assertEquals(DisguiseShortcutEntryEffect.Finish, effect.await())
    }

    @Test
    fun entrySession_expiresAfterTwoMinutes() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.acceptIntent(DisguiseShortcutIntentResult.Valid(TEST_SHORTCUT_ID))
        runCurrent()
        advanceTimeBy(120_001L)
        runCurrent()
        assertEquals(DisguiseShortcutEntryState.SessionExpired, viewModel.state.value)
    }

    private fun createViewModel(
        resolver: DisguiseShortcutResolver = DisguiseShortcutResolver { ResolveDisguiseShortcutResult.Ready },
        verifier: VaultPasswordVerification = VaultPasswordVerification { true },
        launcher: DisguisedTargetLauncher = DisguisedTargetLauncher { LaunchDisguisedTargetResult.Success },
    ) = DisguiseShortcutEntryViewModel(resolver, verifier, launcher)

    private suspend fun enterPasswordAndConfirm(viewModel: DisguiseShortcutEntryViewModel) {
        viewModel.acceptIntent(DisguiseShortcutIntentResult.Valid(TEST_SHORTCUT_ID))
        kotlinx.coroutines.yield()
        repeat(4) { viewModel.inputDigit(it + 1) }
        viewModel.confirmPassword()
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstForTest(): T =
    first()
