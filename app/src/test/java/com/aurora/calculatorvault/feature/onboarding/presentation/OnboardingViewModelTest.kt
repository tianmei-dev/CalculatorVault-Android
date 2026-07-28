package com.aurora.calculatorvault.feature.onboarding.presentation

import com.aurora.calculatorvault.feature.onboarding.data.OnboardingFailure
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingRepositoryContract
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingResult
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class OnboardingViewModelTest {
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
    fun `startup destination updates visible step`() = runTest(dispatcher) {
        val repository = FakeOnboardingRepository(
            startupResult = OnboardingResult.Success(StartupDestination.CreatePassword),
        )
        val viewModel = OnboardingViewModel(repository)

        advanceUntilIdle()

        assertEquals(OnboardingStep.CreatePassword, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `privacy submit is protected from duplicate clicks`() = runTest(dispatcher) {
        val repository = FakeOnboardingRepository()
        val viewModel = OnboardingViewModel(repository)
        advanceUntilIdle()

        viewModel.acceptPrivacy()
        viewModel.acceptPrivacy()
        advanceUntilIdle()

        assertEquals(1, repository.privacyCalls)
        assertEquals(OnboardingStep.CreatePassword, viewModel.uiState.value.step)
    }

    @Test
    fun `three digits cannot continue while four digits can`() = runTest(dispatcher) {
        val viewModel = createPasswordViewModel()
        advanceUntilIdle()

        enter(viewModel, "123")
        assertFalse(viewModel.uiState.value.canContinuePassword)
        viewModel.continueToConfirmation()
        assertEquals(OnboardingStep.CreatePassword, viewModel.uiState.value.step)

        viewModel.addPasswordDigit(4)
        assertTrue(viewModel.uiState.value.canContinuePassword)
        viewModel.continueToConfirmation()
        assertEquals(OnboardingStep.ConfirmPassword, viewModel.uiState.value.step)
    }

    @Test
    fun `password input stops at eight digits`() = runTest(dispatcher) {
        val viewModel = createPasswordViewModel()
        advanceUntilIdle()

        enter(viewModel, "123456789")

        assertEquals(8, viewModel.uiState.value.passwordLength)
    }

    @Test
    fun `mismatch restarts password creation and new password can complete setup`() = runTest(dispatcher) {
        val repository = FakeOnboardingRepository(
            startupResult = OnboardingResult.Success(StartupDestination.CreatePassword),
        )
        val viewModel = OnboardingViewModel(repository)
        advanceUntilIdle()
        enter(viewModel, "4826")
        viewModel.continueToConfirmation()

        enterConfirmation(viewModel, "4827")
        viewModel.confirmPassword()
        assertEquals(OnboardingError.PasswordMismatch, viewModel.uiState.value.error)
        assertEquals(OnboardingStep.CreatePassword, viewModel.uiState.value.step)
        assertEquals(0, viewModel.uiState.value.passwordLength)
        assertEquals(0, viewModel.uiState.value.confirmPasswordLength)
        assertEquals(0, repository.passwordCalls)

        enter(viewModel, "7319")
        viewModel.continueToConfirmation()
        enterConfirmation(viewModel, "7319")
        viewModel.confirmPassword()
        advanceUntilIdle()

        assertEquals(1, repository.passwordCalls)
        assertEquals(OnboardingStep.Calculator, viewModel.uiState.value.step)
        assertEquals(0, viewModel.uiState.value.passwordLength)
        assertEquals(0, viewModel.uiState.value.confirmPasswordLength)
    }

    @Test
    fun `back from confirmation clears all temporary input`() = runTest(dispatcher) {
        val viewModel = createPasswordViewModel()
        advanceUntilIdle()
        enter(viewModel, "4826")
        viewModel.continueToConfirmation()
        enterConfirmation(viewModel, "48")

        viewModel.returnToCreatePassword()

        assertEquals(OnboardingStep.CreatePassword, viewModel.uiState.value.step)
        assertEquals(0, viewModel.uiState.value.passwordLength)
        assertEquals(0, viewModel.uiState.value.confirmPasswordLength)
    }

    @Test
    fun `password save failure stays on confirmation and allows retry`() = runTest(dispatcher) {
        val repository = FakeOnboardingRepository(
            startupResult = OnboardingResult.Success(StartupDestination.CreatePassword),
            passwordResult = OnboardingResult.Failure(OnboardingFailure.PasswordSaveFailed),
        )
        val viewModel = OnboardingViewModel(repository)
        advanceUntilIdle()
        enter(viewModel, "4826")
        viewModel.continueToConfirmation()
        enterConfirmation(viewModel, "4826")

        viewModel.confirmPassword()
        advanceUntilIdle()

        assertEquals(OnboardingStep.ConfirmPassword, viewModel.uiState.value.step)
        assertEquals(OnboardingError.PasswordSaveFailed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.canConfirmPassword)
    }

    private fun enter(viewModel: OnboardingViewModel, digits: String) {
        digits.forEach { viewModel.addPasswordDigit(it.digitToInt()) }
    }

    private fun enterConfirmation(viewModel: OnboardingViewModel, digits: String) {
        digits.forEach { viewModel.addConfirmationDigit(it.digitToInt()) }
    }

    private fun createPasswordViewModel() = OnboardingViewModel(
        FakeOnboardingRepository(
            startupResult = OnboardingResult.Success(StartupDestination.CreatePassword),
        ),
    )

    private class FakeOnboardingRepository(
        private val startupResult: OnboardingResult<StartupDestination> =
            OnboardingResult.Success(StartupDestination.PrivacyConsent),
        private val privacyResult: OnboardingResult<Unit> = OnboardingResult.Success(Unit),
        private val passwordResult: OnboardingResult<Unit> = OnboardingResult.Success(Unit),
    ) : OnboardingRepositoryContract {
        var privacyCalls = 0
        var passwordCalls = 0

        override suspend fun resolveStartupDestination(): OnboardingResult<StartupDestination> =
            startupResult

        override suspend fun acceptPrivacy(): OnboardingResult<Unit> {
            privacyCalls++
            return privacyResult
        }

        override suspend fun configurePassword(password: CharArray): OnboardingResult<Unit> {
            passwordCalls++
            password.fill('\u0000')
            return passwordResult
        }
    }
}
