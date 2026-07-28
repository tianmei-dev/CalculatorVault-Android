package com.aurora.calculatorvault.feature.calculator.domain

sealed interface CalculatorAction {
    data class Number(val value: Int) : CalculatorAction
    data object Decimal : CalculatorAction
    data class Operator(val operator: CalculatorOperator) : CalculatorAction
    data object Equals : CalculatorAction
    data object Clear : CalculatorAction
    data object Delete : CalculatorAction
    data object ToggleSign : CalculatorAction
    data object Percent : CalculatorAction
}

