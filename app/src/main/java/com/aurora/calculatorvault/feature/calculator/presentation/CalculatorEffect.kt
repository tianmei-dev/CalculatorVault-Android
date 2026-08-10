package com.aurora.calculatorvault.feature.calculator.presentation

sealed interface CalculatorEffect {
    data object OpenVault : CalculatorEffect
}

sealed interface CalculatorPasswordRevealState {
    data object Hidden : CalculatorPasswordRevealState
    data class Visible(val password: CharArray) : CalculatorPasswordRevealState
    data object Unavailable : CalculatorPasswordRevealState
    data object Failed : CalculatorPasswordRevealState
}
