package com.aurora.calculatorvault.feature.settings.presentation

import com.aurora.calculatorvault.feature.settings.data.ChangePasswordFailure
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordRepositoryContract
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordResult
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
class ChangePasswordViewModelTest {
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
    fun `short current password does not trigger verification`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = ChangePasswordViewModel(repository)
        enter(viewModel, "123")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.verifyCalls)
        assertEquals(ChangePasswordStep.VerifyCurrent, viewModel.uiState.value.step)
    }

    @Test
    fun `wrong current password clears input and retry can succeed`() = runTest(dispatcher) {
        val repository = FakeRepository(expectedCurrent = "4826")
        val viewModel = ChangePasswordViewModel(repository)
        enter(viewModel, "4827")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(ChangePasswordError.CurrentPasswordIncorrect, viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.currentPasswordLength)

        enter(viewModel, "4826")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(ChangePasswordStep.CreateNew, viewModel.uiState.value.step)
        assertEquals(2, repository.verifyCalls)
    }

    @Test
    fun `new password accepts four to eight digits and ignores ninth`() = runTest(dispatcher) {
        val viewModel = verifiedViewModel()
        advanceUntilIdle()

        enter(viewModel, "123")
        assertFalse(viewModel.uiState.value.canSubmit)
        enter(viewModel, "456789")

        assertEquals(8, viewModel.uiState.value.newPasswordLength)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `same password requires explicit confirmation before continuing`() = runTest(dispatcher) {
        val viewModel = verifiedViewModel(currentPassword = "4826")
        advanceUntilIdle()
        enter(viewModel, "4826")

        viewModel.submit()
        assertTrue(viewModel.uiState.value.showSamePasswordPrompt)
        assertEquals(ChangePasswordStep.CreateNew, viewModel.uiState.value.step)

        viewModel.acceptSamePassword()
        assertEquals(ChangePasswordStep.ConfirmNew, viewModel.uiState.value.step)
    }

    @Test
    fun `new password mismatch clears confirmation and retry succeeds`() = runTest(dispatcher) {
        val repository = FakeRepository(expectedCurrent = "4826")
        val viewModel = verifiedViewModel(repository = repository)
        advanceUntilIdle()
        enter(viewModel, "7319")
        viewModel.submit()
        enter(viewModel, "7318")
        viewModel.submit()

        assertEquals(ChangePasswordError.PasswordMismatch, viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.confirmPasswordLength)
        assertEquals(0, repository.replaceCalls)

        enter(viewModel, "7319")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(ChangePasswordStep.Completed, viewModel.uiState.value.step)
        assertEquals(1, repository.replaceCalls)
    }

    @Test
    fun `save failure keeps flow retryable and does not complete`() = runTest(dispatcher) {
        val repository = FakeRepository(
            expectedCurrent = "4826",
            replaceResult = ChangePasswordResult.Failure(ChangePasswordFailure.SaveFailed),
        )
        val viewModel = verifiedViewModel(repository = repository)
        advanceUntilIdle()
        enter(viewModel, "7319")
        viewModel.submit()
        enter(viewModel, "7319")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(ChangePasswordStep.ConfirmNew, viewModel.uiState.value.step)
        assertEquals(ChangePasswordError.SaveFailed, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `exiting flow revokes verification and clears all lengths`() = runTest(dispatcher) {
        val viewModel = verifiedViewModel()
        advanceUntilIdle()
        enter(viewModel, "7319")

        viewModel.cancelFlow()

        assertEquals(ChangePasswordStep.VerifyCurrent, viewModel.uiState.value.step)
        assertEquals(0, viewModel.uiState.value.currentPasswordLength)
        assertEquals(0, viewModel.uiState.value.newPasswordLength)
        assertEquals(0, viewModel.uiState.value.confirmPasswordLength)
        enter(viewModel, "7319")
        assertEquals(4, viewModel.uiState.value.currentPasswordLength)
        assertEquals(0, viewModel.uiState.value.newPasswordLength)
    }

    @Test
    fun `back from confirmation clears new password and requires creation again`() =
        runTest(dispatcher) {
            val viewModel = verifiedViewModel()
            advanceUntilIdle()
            enter(viewModel, "7319")
            viewModel.submit()
            enter(viewModel, "73")

            assertTrue(viewModel.returnToPreviousStep())

            assertEquals(ChangePasswordStep.CreateNew, viewModel.uiState.value.step)
            assertEquals(0, viewModel.uiState.value.newPasswordLength)
            assertEquals(0, viewModel.uiState.value.confirmPasswordLength)
        }

    private fun verifiedViewModel(
        currentPassword: String = "4826",
        repository: FakeRepository = FakeRepository(expectedCurrent = currentPassword),
    ): ChangePasswordViewModel {
        val viewModel = ChangePasswordViewModel(repository)
        enter(viewModel, currentPassword)
        viewModel.submit()
        return viewModel
    }

    private fun enter(viewModel: ChangePasswordViewModel, digits: String) {
        digits.forEach { viewModel.addDigit(it.digitToInt()) }
    }

    private class FakeRepository(
        private val expectedCurrent: String = "4826",
        private val replaceResult: ChangePasswordResult<Unit> =
            ChangePasswordResult.Success(Unit),
    ) : ChangePasswordRepositoryContract {
        var verifyCalls = 0
        var replaceCalls = 0

        override suspend fun verifyCurrentPassword(
            password: CharArray,
        ): ChangePasswordResult<Unit> {
            verifyCalls++
            return try {
                if (password.concatToString() == expectedCurrent) {
                    ChangePasswordResult.Success(Unit)
                } else {
                    ChangePasswordResult.Failure(ChangePasswordFailure.CurrentPasswordIncorrect)
                }
            } finally {
                password.fill('\u0000')
            }
        }

        override suspend fun replacePassword(
            newPassword: CharArray,
        ): ChangePasswordResult<Unit> {
            replaceCalls++
            newPassword.fill('\u0000')
            return replaceResult
        }
    }
}
