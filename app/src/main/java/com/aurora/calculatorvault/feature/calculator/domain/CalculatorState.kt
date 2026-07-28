package com.aurora.calculatorvault.feature.calculator.domain

data class CalculatorState(
    val expression: String = "",
    val displayValue: String = "0",
    val previousValue: String? = null,
    val pendingOperator: CalculatorOperator? = null,
    val isAwaitingOperand: Boolean = false,
    val isResultShown: Boolean = false,
    val repeatOperator: CalculatorOperator? = null,
    val repeatOperand: String? = null,
    val error: CalculatorError? = null,
)
