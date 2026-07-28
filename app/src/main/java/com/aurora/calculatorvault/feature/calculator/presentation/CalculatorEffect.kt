package com.aurora.calculatorvault.feature.calculator.presentation

sealed interface CalculatorEffect {
    data object OpenVault : CalculatorEffect
}
