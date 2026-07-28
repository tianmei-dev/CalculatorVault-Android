package com.aurora.calculatorvault.feature.calculator.presentation

import com.aurora.calculatorvault.feature.calculator.domain.CalculatorAction
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorOperator
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorViewModelTest {

    @Test
    fun `actions update the exposed immutable state flow`() {
        val viewModel = CalculatorViewModel()

        viewModel.onAction(CalculatorAction.Number(2))
        viewModel.onAction(CalculatorAction.Operator(CalculatorOperator.Add))
        viewModel.onAction(CalculatorAction.Number(3))
        viewModel.onAction(CalculatorAction.Equals)

        assertEquals("5", viewModel.uiState.value.displayValue)
        assertEquals("2 + 3 =", viewModel.uiState.value.expression)
    }

    @Test
    fun `clear resets view model state`() {
        val viewModel = CalculatorViewModel()
        viewModel.onAction(CalculatorAction.Number(9))
        viewModel.onAction(CalculatorAction.ToggleSign)
        viewModel.onAction(CalculatorAction.Clear)

        assertEquals(CalculatorUiState(), viewModel.uiState.value)
    }
}
