package com.aurora.calculatorvault.feature.calculator.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultUnlockUseCaseTest {

    @Test
    fun `candidate accepts only direct four to eight digit input`() {
        assertTrue(isUnlockCandidate(CalculatorState(displayValue = "1234"), "1234".toCharArray()))
        assertTrue(
            isUnlockCandidate(
                CalculatorState(displayValue = "12345678"),
                "12345678".toCharArray(),
            ),
        )
        assertTrue(isUnlockCandidate(CalculatorState(displayValue = "123"), "0123".toCharArray()))
        assertFalse(isUnlockCandidate(CalculatorState(displayValue = "123"), "123".toCharArray()))
        assertFalse(
            isUnlockCandidate(
                CalculatorState(displayValue = "123456789"),
                "123456789".toCharArray(),
            ),
        )
        assertFalse(
            isUnlockCandidate(
                CalculatorState(displayValue = "9.527"),
                "9527".toCharArray(),
            ),
        )
        assertFalse(
            isUnlockCandidate(
                CalculatorState(displayValue = "-9527"),
                "9527".toCharArray(),
            ),
        )
        assertFalse(
            isUnlockCandidate(
                CalculatorState(
                    displayValue = "9527",
                    expression = "952700 %",
                    isResultShown = true,
                ),
                "9527".toCharArray(),
            ),
        )
        assertFalse(
            isUnlockCandidate(
                CalculatorState(
                    displayValue = "9527",
                    expression = "9500 + 27 =",
                    isResultShown = true,
                ),
                "9527".toCharArray(),
            ),
        )
        assertFalse(
            isUnlockCandidate(
                CalculatorState(displayValue = "9527", isResultShown = true),
                "9527".toCharArray(),
            ),
        )
    }

    @Test
    fun `use case delegates valid input and rejects invalid input`() = runBlocking {
        var calls = 0
        val useCase = VaultUnlockUseCase(
            verifier = object : VaultPasswordVerifier {
                override suspend fun verify(candidate: CharArray): Boolean {
                    calls += 1
                    return candidate.concatToString() == "4826"
                }
            },
        )

        assertTrue(useCase.verify("4826".toCharArray()))
        assertFalse(useCase.verify("482".toCharArray()))
        assertFalse(useCase.verify("48.26".toCharArray()))
        assertTrue(calls == 1)
    }

    @Test
    fun `verification exception is indistinguishable from a wrong candidate`() = runBlocking {
        val useCase = VaultUnlockUseCase(
            verifier = object : VaultPasswordVerifier {
                override suspend fun verify(candidate: CharArray): Boolean {
                    throw IllegalStateException("simulated storage failure")
                }
            },
        )

        assertFalse(useCase.verify("4826".toCharArray()))
    }
}
