package com.aurora.calculatorvault.feature.calculator.presentation

import com.aurora.calculatorvault.core.security.session.VaultSessionManager
import com.aurora.calculatorvault.core.security.session.VaultSessionState
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.aurora.calculatorvault.core.security.PasswordHashResult
import com.aurora.calculatorvault.core.security.recovery.PasswordRecoveryCipher
import com.aurora.calculatorvault.core.security.recovery.PasswordRecoveryMaterial
import com.aurora.calculatorvault.core.security.recovery.PasswordRecoveryRepository
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorAction
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorOperator
import com.aurora.calculatorvault.feature.calculator.domain.VaultPasswordVerifier
import com.aurora.calculatorvault.feature.calculator.domain.VaultUnlockUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelUnlockTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `correct direct password unlocks once emits effect and clears calculator`() =
        runTest(dispatcher) {
            val verifier = RecordingVerifier("4826")
            val manager = foregroundManager()
            val viewModel = viewModel(verifier, manager)
            val effect = async { viewModel.effects.first() }
            input(viewModel, "4826")

            viewModel.onAction(CalculatorAction.Equals)
            viewModel.onAction(CalculatorAction.Equals)
            advanceUntilIdle()

            assertEquals(CalculatorEffect.OpenVault, effect.await())
            assertEquals(1, verifier.calls)
            assertEquals(VaultSessionState.Unlocked, manager.state.value)
            assertEquals(CalculatorUiState(), viewModel.uiState.value)
        }

    @Test
    fun `wrong password remains normal calculator without unlock effect`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("4826")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        input(viewModel, "4827")

        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals("4827", viewModel.uiState.value.displayValue)
        assertEquals(VaultSessionState.Locked, manager.state.value)
        assertEquals(1, verifier.calls)
    }

    @Test
    fun `leading zero password 0123 unlocks while calculator displays 123`() = runTest(dispatcher) {
        assertLeadingZeroPasswordUnlocks(password = "0123", expectedDisplayBeforeEquals = "123")
    }

    @Test
    fun `all zero password 0000 unlocks while calculator displays zero`() = runTest(dispatcher) {
        assertLeadingZeroPasswordUnlocks(password = "0000", expectedDisplayBeforeEquals = "0")
    }

    @Test
    fun `two leading zero password 0012 unlocks while calculator displays 12`() =
        runTest(dispatcher) {
            assertLeadingZeroPasswordUnlocks(password = "0012", expectedDisplayBeforeEquals = "12")
        }

    @Test
    fun `omitting required leading zero cannot unlock`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("0123")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        input(viewModel, "123")

        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals(0, verifier.calls)
        assertFalse(manager.isUnlocked())
        assertEquals("123", viewModel.uiState.value.displayValue)
    }

    @Test
    fun `delete updates raw candidate including its leading zero`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("0123")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        val effect = async { viewModel.effects.first() }
        input(viewModel, "012")
        viewModel.onAction(CalculatorAction.Delete)
        input(viewModel, "23")

        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals(CalculatorEffect.OpenVault, effect.await())
        assertEquals(1, verifier.calls)
        assertTrue(manager.isUnlocked())
    }

    @Test
    fun `clear removes raw candidate`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("0123")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        input(viewModel, "0123")
        viewModel.onAction(CalculatorAction.Clear)
        input(viewModel, "123")

        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals(0, verifier.calls)
        assertFalse(manager.isUnlocked())
    }

    @Test
    fun `operator invalidates raw candidate`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("0123")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        input(viewModel, "0123")
        viewModel.onAction(CalculatorAction.Operator(CalculatorOperator.Add))
        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals(0, verifier.calls)
        assertFalse(manager.isUnlocked())
    }

    @Test
    fun `ninth raw digit invalidates candidate instead of verifying first eight`() =
        runTest(dispatcher) {
            val verifier = RecordingVerifier("01234567")
            val manager = foregroundManager()
            val viewModel = viewModel(verifier, manager)
            input(viewModel, "012345678")

            viewModel.onAction(CalculatorAction.Equals)
            advanceUntilIdle()

            assertEquals(0, verifier.calls)
            assertFalse(manager.isUnlocked())
        }

    @Test
    fun `decimal input cannot unlock leading zero password`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("0123")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        viewModel.onAction(CalculatorAction.Number(0))
        viewModel.onAction(CalculatorAction.Decimal)
        input(viewModel, "123")

        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals(0, verifier.calls)
        assertFalse(manager.isUnlocked())
        assertEquals("0.123", viewModel.uiState.value.displayValue)
    }

    @Test
    fun `expression result matching numeric password cannot unlock`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("0123")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        input(viewModel, "120")
        viewModel.onAction(CalculatorAction.Operator(CalculatorOperator.Add))
        input(viewModel, "3")

        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals("123", viewModel.uiState.value.displayValue)
        assertEquals(0, verifier.calls)
        assertFalse(manager.isUnlocked())
    }

    @Test
    fun `digit after result starts a new leading zero candidate round`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("0123")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        val effect = async { viewModel.effects.first() }
        viewModel.onAction(CalculatorAction.Number(2))
        viewModel.onAction(CalculatorAction.Operator(CalculatorOperator.Add))
        viewModel.onAction(CalculatorAction.Number(3))
        viewModel.onAction(CalculatorAction.Equals)
        input(viewModel, "0123")

        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals(CalculatorEffect.OpenVault, effect.await())
        assertTrue(manager.isUnlocked())
        assertEquals(1, verifier.calls)
    }

    @Test
    fun `ordinary expression and result equal to password never verify`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("5")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        viewModel.onAction(CalculatorAction.Number(2))
        viewModel.onAction(CalculatorAction.Operator(CalculatorOperator.Add))
        viewModel.onAction(CalculatorAction.Number(3))
        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals("5", viewModel.uiState.value.displayValue)
        assertEquals(0, verifier.calls)
        assertFalse(manager.isUnlocked())
    }

    @Test
    fun `verification finishing after background cannot unlock and calculator is cleared`() =
        runTest(dispatcher) {
            val verifier = RecordingVerifier("4826")
            val manager = foregroundManager()
            val viewModel = viewModel(verifier, manager)
            input(viewModel, "4826")
            viewModel.onAction(CalculatorAction.Equals)

            manager.onAppBackgrounded()
            advanceUntilIdle()

            assertEquals(VaultSessionState.Locked, manager.state.value)
            assertEquals(CalculatorUiState(), viewModel.uiState.value)
        }

    @Test
    fun `background clears leading zero candidate before equals`() = runTest(dispatcher) {
        val verifier = RecordingVerifier("0123")
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        input(viewModel, "0123")

        manager.onAppBackgrounded()
        advanceUntilIdle()
        manager.onAppForegrounded()
        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals(0, verifier.calls)
        assertFalse(manager.isUnlocked())
        assertEquals(CalculatorUiState(), viewModel.uiState.value)
    }

    @Test
    fun `legacy unlock backfills recovery material preserving leading zero password`() =
        runTest(dispatcher) {
            val verifier = RecordingVerifier("0123")
            val manager = foregroundManager()
            val dataSource = RecoveryDataSource()
            val recoveryRepository = PasswordRecoveryRepository(
                dataSource = dataSource,
                cipher = PlainTextTestRecoveryCipher(),
                currentTimeMillis = { 42L },
            )
            val viewModel = CalculatorViewModel(
                unlockUseCase = VaultUnlockUseCase(verifier),
                sessionManager = manager,
                passwordRecoveryRepository = recoveryRepository,
            )
            val effect = async { viewModel.effects.first() }
            input(viewModel, "0123")

            viewModel.onAction(CalculatorAction.Equals)
            advanceUntilIdle()

            assertEquals(CalculatorEffect.OpenVault, effect.await())
            assertEquals("0123", dataSource.preferences.passwordRecoveryCiphertext)
            assertEquals("iv", dataSource.preferences.passwordRecoveryIv)
            assertEquals("AES/GCM/NoPadding", dataSource.preferences.passwordRecoveryAlgorithm)
            assertEquals(1, dataSource.preferences.passwordRecoveryVersion)
            assertEquals(42L, dataSource.preferences.passwordRecoveryUpdatedAt)
        }

    @Test
    fun `password reveal exposes current password and dismiss wipes visible char array`() =
        runTest(dispatcher) {
            val dataSource = RecoveryDataSource(
                preferences = SecurityPreferences(
                    passwordRecoveryCiphertext = "0012",
                    passwordRecoveryIv = "iv",
                    passwordRecoveryAlgorithm = "AES/GCM/NoPadding",
                    passwordRecoveryVersion = 1,
                    passwordRecoveryUpdatedAt = 7L,
                ),
            )
            val viewModel = CalculatorViewModel(
                passwordRecoveryRepository = PasswordRecoveryRepository(
                    dataSource = dataSource,
                    cipher = PlainTextTestRecoveryCipher(),
                ),
            )

            viewModel.revealCurrentPassword()
            advanceUntilIdle()

            val visible = viewModel.passwordRevealState.value as CalculatorPasswordRevealState.Visible
            assertEquals("0012", visible.password.concatToString())

            viewModel.dismissPasswordReveal()

            assertTrue(visible.password.all { it == '\u0000' })
            assertEquals(CalculatorPasswordRevealState.Hidden, viewModel.passwordRevealState.value)
        }

    @Test
    fun `background dismisses visible password reveal`() = runTest(dispatcher) {
        val manager = foregroundManager()
        val dataSource = RecoveryDataSource(
            preferences = SecurityPreferences(
                passwordRecoveryCiphertext = "0012",
                passwordRecoveryIv = "iv",
                passwordRecoveryAlgorithm = "AES/GCM/NoPadding",
                passwordRecoveryVersion = 1,
                passwordRecoveryUpdatedAt = 7L,
            ),
        )
        val viewModel = CalculatorViewModel(
            sessionManager = manager,
            passwordRecoveryRepository = PasswordRecoveryRepository(
                dataSource = dataSource,
                cipher = PlainTextTestRecoveryCipher(),
            ),
        )
        viewModel.revealCurrentPassword()
        advanceUntilIdle()
        val visible = viewModel.passwordRevealState.value as CalculatorPasswordRevealState.Visible

        manager.onAppBackgrounded()
        advanceUntilIdle()

        assertTrue(visible.password.all { it == '\u0000' })
        assertEquals(CalculatorPasswordRevealState.Hidden, viewModel.passwordRevealState.value)
    }

    private fun viewModel(
        verifier: RecordingVerifier,
        manager: VaultSessionManager,
    ) = CalculatorViewModel(
        unlockUseCase = VaultUnlockUseCase(verifier),
        sessionManager = manager,
    )

    private fun foregroundManager() = VaultSessionManager().also {
        it.onAppForegrounded()
        assertTrue(!it.isUnlocked())
    }

    private fun input(viewModel: CalculatorViewModel, value: String) {
        value.forEach { digit ->
            viewModel.onAction(CalculatorAction.Number(digit.digitToInt()))
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertLeadingZeroPasswordUnlocks(
        password: String,
        expectedDisplayBeforeEquals: String,
    ) {
        val verifier = RecordingVerifier(password)
        val manager = foregroundManager()
        val viewModel = viewModel(verifier, manager)
        val effect = async { viewModel.effects.first() }
        input(viewModel, password)
        assertEquals(expectedDisplayBeforeEquals, viewModel.uiState.value.displayValue)

        viewModel.onAction(CalculatorAction.Equals)
        advanceUntilIdle()

        assertEquals(CalculatorEffect.OpenVault, effect.await())
        assertEquals(1, verifier.calls)
        assertTrue(manager.isUnlocked())
        assertEquals(CalculatorUiState(), viewModel.uiState.value)
    }

    private class RecordingVerifier(
        private val correct: String,
    ) : VaultPasswordVerifier {
        var calls = 0

        override suspend fun verify(candidate: CharArray): Boolean {
            calls += 1
            return candidate.concatToString() == correct
        }
    }

    private class PlainTextTestRecoveryCipher : PasswordRecoveryCipher {
        override suspend fun encrypt(password: CharArray, updatedAt: Long): PasswordRecoveryMaterial =
            PasswordRecoveryMaterial(
                ciphertext = password.concatToString(),
                iv = "iv",
                algorithm = "AES/GCM/NoPadding",
                version = 1,
                updatedAt = updatedAt,
            )

        override suspend fun decrypt(material: PasswordRecoveryMaterial): CharArray =
            material.ciphertext.toCharArray()
    }

    private class RecoveryDataSource(
        var preferences: SecurityPreferences = SecurityPreferences(),
    ) : SecurityPreferencesDataSource {
        override suspend fun read(): SecurityPreferences = preferences

        override suspend fun acceptPrivacy(version: String, acceptedAt: Long) = Unit

        override suspend fun savePasswordInitialization(
            result: PasswordHashResult,
            createdAt: Long,
        ) = Unit

        override suspend fun replacePassword(result: PasswordHashResult, updatedAt: Long) = Unit

        override suspend fun repairIncompletePasswordSetup() = Unit

        override suspend fun saveRecoveryMaterial(material: PasswordRecoveryMaterial) {
            preferences = preferences.copy(
                passwordRecoveryCiphertext = material.ciphertext,
                passwordRecoveryIv = material.iv,
                passwordRecoveryAlgorithm = material.algorithm,
                passwordRecoveryVersion = material.version,
                passwordRecoveryUpdatedAt = material.updatedAt,
            )
        }

        override suspend fun clearRecoveryMaterial() {
            preferences = preferences.copy(
                passwordRecoveryCiphertext = null,
                passwordRecoveryIv = null,
                passwordRecoveryAlgorithm = null,
                passwordRecoveryVersion = null,
                passwordRecoveryUpdatedAt = null,
            )
        }
    }
}
