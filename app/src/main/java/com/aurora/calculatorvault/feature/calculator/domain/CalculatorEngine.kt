package com.aurora.calculatorvault.feature.calculator.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CalculatorEngine {

    fun reduce(
        state: CalculatorState,
        action: CalculatorAction,
    ): CalculatorState = when (action) {
        is CalculatorAction.Number -> inputNumber(state, action.value)
        CalculatorAction.Decimal -> inputDecimal(state)
        is CalculatorAction.Operator -> applyOperator(state, action.operator)
        CalculatorAction.Equals -> equals(state)
        CalculatorAction.Clear -> CalculatorState()
        CalculatorAction.Delete -> delete(state)
        CalculatorAction.ToggleSign -> toggleSign(state)
        CalculatorAction.Percent -> percent(state)
    }

    private fun inputNumber(state: CalculatorState, number: Int): CalculatorState {
        require(number in 0..9)
        val recovered = state.recoverForFreshInput()
        val startsOperand = recovered.isAwaitingOperand
        val current = if (startsOperand) "0" else recovered.displayValue
        if (!startsOperand && current.count(Char::isDigit) >= MAX_INPUT_DIGITS) return recovered

        val next = when {
            startsOperand || current == "0" -> number.toString()
            current == "-0" -> "-$number"
            else -> current + number
        }
        return recovered.copy(
            displayValue = next,
            expression = recovered.expressionForCurrent(next),
            isAwaitingOperand = false,
            isResultShown = false,
        )
    }

    private fun inputDecimal(state: CalculatorState): CalculatorState {
        val recovered = state.recoverForFreshInput()
        if (!recovered.isAwaitingOperand && '.' in recovered.displayValue) return recovered
        val next = if (recovered.isAwaitingOperand) "0." else recovered.displayValue + "."
        return recovered.copy(
            displayValue = next,
            expression = recovered.expressionForCurrent(next),
            isAwaitingOperand = false,
            isResultShown = false,
        )
    }

    private fun applyOperator(
        state: CalculatorState,
        operator: CalculatorOperator,
    ): CalculatorState {
        if (state.error != null) {
            return CalculatorState(
                previousValue = "0",
                pendingOperator = operator,
                isAwaitingOperand = true,
                expression = "0 ${operator.symbol}",
            )
        }

        if (state.pendingOperator != null && state.isAwaitingOperand) {
            val previous = state.previousValue ?: state.displayValue
            return state.copy(
                pendingOperator = operator,
                expression = "$previous ${operator.symbol}",
                repeatOperator = null,
                repeatOperand = null,
            )
        }

        if (state.pendingOperator != null && state.previousValue != null) {
            return when (
                val result = calculate(
                    leftText = state.previousValue,
                    operator = state.pendingOperator,
                    rightText = state.displayValue,
                )
            ) {
                is CalculationResult.Value -> state.copy(
                    displayValue = result.formatted,
                    previousValue = result.formatted,
                    pendingOperator = operator,
                    isAwaitingOperand = true,
                    isResultShown = false,
                    expression = "${result.formatted} ${operator.symbol}",
                    repeatOperator = null,
                    repeatOperand = null,
                )

                is CalculationResult.Failure -> state.toError(result.error)
            }
        }

        return state.copy(
            previousValue = state.displayValue,
            pendingOperator = operator,
            isAwaitingOperand = true,
            isResultShown = false,
            expression = "${state.displayValue} ${operator.symbol}",
            repeatOperator = null,
            repeatOperand = null,
        )
    }

    private fun equals(state: CalculatorState): CalculatorState {
        if (state.error != null) return state

        val pendingOperator = state.pendingOperator
        val previousValue = state.previousValue
        if (pendingOperator != null && previousValue != null) {
            if (state.isAwaitingOperand) return state
            val operand = state.displayValue
            return state.completeCalculation(
                leftText = previousValue,
                operator = pendingOperator,
                rightText = operand,
                rememberForRepeat = true,
            )
        }

        val repeatOperator = state.repeatOperator
        val repeatOperand = state.repeatOperand
        if (state.isResultShown && repeatOperator != null && repeatOperand != null) {
            return state.completeCalculation(
                leftText = state.displayValue,
                operator = repeatOperator,
                rightText = repeatOperand,
                rememberForRepeat = true,
            )
        }

        return state
    }

    private fun CalculatorState.completeCalculation(
        leftText: String,
        operator: CalculatorOperator,
        rightText: String,
        rememberForRepeat: Boolean,
    ): CalculatorState = when (val result = calculate(leftText, operator, rightText)) {
        is CalculationResult.Value -> copy(
            displayValue = result.formatted,
            previousValue = null,
            pendingOperator = null,
            isAwaitingOperand = false,
            isResultShown = true,
            expression = "$leftText ${operator.symbol} $rightText =",
            repeatOperator = operator.takeIf { rememberForRepeat },
            repeatOperand = rightText.takeIf { rememberForRepeat },
            error = null,
        )

        is CalculationResult.Failure -> toError(
            error = result.error,
            expression = "$leftText ${operator.symbol} $rightText =",
        )
    }

    private fun toggleSign(state: CalculatorState): CalculatorState {
        if (state.error != null) return CalculatorState()
        if (state.displayValue.isZero() || state.isAwaitingOperand) return state
        val next = if (state.displayValue.startsWith("-")) {
            state.displayValue.drop(1)
        } else {
            "-${state.displayValue}"
        }
        return state.copy(
            displayValue = next,
            expression = state.expressionForCurrent(next),
        )
    }

    private fun percent(state: CalculatorState): CalculatorState {
        if (state.error != null) return CalculatorState()
        return try {
            val source = state.displayValue
            val formatted = format(BigDecimal(source).movePointLeft(2))
            if (state.pendingOperator != null) {
                state.copy(
                    displayValue = formatted,
                    expression = state.expressionForCurrent(formatted),
                    isAwaitingOperand = false,
                    isResultShown = false,
                )
            } else {
                state.copy(
                    displayValue = formatted,
                    expression = "$source %",
                    isAwaitingOperand = false,
                    isResultShown = true,
                    repeatOperator = null,
                    repeatOperand = null,
                )
            }
        } catch (_: NumberFormatException) {
            state.toError(CalculatorError.InvalidOperation)
        }
    }

    private fun delete(state: CalculatorState): CalculatorState {
        if (state.error != null || state.isResultShown) return CalculatorState()
        if (state.isAwaitingOperand) return state
        val next = state.displayValue.dropLast(1).takeUnless { it.isEmpty() || it == "-" } ?: "0"
        return state.copy(
            displayValue = next,
            expression = state.expressionForCurrent(next),
        )
    }

    private fun calculate(
        leftText: String,
        operator: CalculatorOperator,
        rightText: String,
    ): CalculationResult = try {
        val left = BigDecimal(leftText)
        val right = BigDecimal(rightText)
        val value = when (operator) {
            CalculatorOperator.Add -> left.add(right)
            CalculatorOperator.Subtract -> left.subtract(right)
            CalculatorOperator.Multiply -> left.multiply(right)
            CalculatorOperator.Divide -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    return CalculationResult.Failure(CalculatorError.DivisionByZero)
                }
                left.divide(right, DIVISION_SCALE, RoundingMode.HALF_UP)
            }
        }
        CalculationResult.Value(format(value))
    } catch (_: ArithmeticException) {
        CalculationResult.Failure(CalculatorError.InvalidOperation)
    } catch (_: NumberFormatException) {
        CalculationResult.Failure(CalculatorError.InvalidOperation)
    }

    private fun format(value: BigDecimal): String {
        if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
        val normalized = value.stripTrailingZeros()
        val plain = normalized.toPlainString()
        if (plain.length <= MAX_PLAIN_DISPLAY_LENGTH) return plain

        val rounded = normalized.round(DISPLAY_MATH_CONTEXT).stripTrailingZeros()
        val exponent = rounded.precision() - rounded.scale() - 1
        val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
        return "$mantissa" + "E" + if (exponent >= 0) "+$exponent" else exponent.toString()
    }

    private fun CalculatorState.recoverForFreshInput(): CalculatorState {
        if (error != null || (isResultShown && pendingOperator == null)) return CalculatorState()
        return this
    }

    private fun CalculatorState.expressionForCurrent(current: String): String {
        val operator = pendingOperator ?: return if (isResultShown) expression else ""
        val previous = previousValue ?: return expression
        return "$previous ${operator.symbol} $current"
    }

    private fun CalculatorState.toError(
        error: CalculatorError,
        expression: String = this.expression,
    ): CalculatorState = copy(
        expression = expression,
        previousValue = null,
        pendingOperator = null,
        isAwaitingOperand = false,
        isResultShown = false,
        repeatOperator = null,
        repeatOperand = null,
        error = error,
    )

    private fun String.isZero(): Boolean = runCatching {
        BigDecimal(this).compareTo(BigDecimal.ZERO) == 0
    }.getOrDefault(false)

    private sealed interface CalculationResult {
        data class Value(val formatted: String) : CalculationResult
        data class Failure(val error: CalculatorError) : CalculationResult
    }

    private companion object {
        const val MAX_INPUT_DIGITS = 15
        const val MAX_PLAIN_DISPLAY_LENGTH = 16
        const val DIVISION_SCALE = 12
        val DISPLAY_MATH_CONTEXT = MathContext(12, RoundingMode.HALF_UP)
    }
}
