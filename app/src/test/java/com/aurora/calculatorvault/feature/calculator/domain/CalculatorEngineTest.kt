package com.aurora.calculatorvault.feature.calculator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {
    private val engine = CalculatorEngine()

    @Test
    fun `number input removes leading zero and enforces digit limit`() {
        var state = stateAfter(
            CalculatorAction.Number(0),
            CalculatorAction.Number(0),
            CalculatorAction.Number(0),
        )
        assertEquals("0", state.displayValue)

        state = input("1234567890123456")
        assertEquals("123456789012345", state.displayValue)
    }

    @Test
    fun `decimal input starts with zero and ignores repeated decimal`() {
        var state = stateAfter(CalculatorAction.Decimal, CalculatorAction.Number(5))
        assertEquals("0.5", state.displayValue)

        state = engine.reduce(state, CalculatorAction.Decimal)
        state = engine.reduce(state, CalculatorAction.Number(2))
        assertEquals("0.52", state.displayValue)
    }

    @Test
    fun `basic arithmetic produces correct values`() {
        assertEquals("5", calculate("2", CalculatorOperator.Add, "3").displayValue)
        assertEquals("5", calculate("9", CalculatorOperator.Subtract, "4").displayValue)
        assertEquals("42", calculate("6", CalculatorOperator.Multiply, "7").displayValue)
        assertEquals("4", calculate("8", CalculatorOperator.Divide, "2").displayValue)
    }

    @Test
    fun `decimal arithmetic uses exact decimal values`() {
        assertEquals("0.3", calculate("0.1", CalculatorOperator.Add, "0.2").displayValue)
        assertEquals("3", calculate("1.5", CalculatorOperator.Multiply, "2").displayValue)
    }

    @Test
    fun `result can continue into another operation`() {
        var state = calculate("2", CalculatorOperator.Add, "3")
        state = engine.reduce(state, CalculatorAction.Operator(CalculatorOperator.Multiply))
        state = reduce(state, inputActions("4") + CalculatorAction.Equals)
        assertEquals("20", state.displayValue)
    }

    @Test
    fun `operator is replaced while waiting for operand`() {
        val state = stateAfter(
            CalculatorAction.Number(5),
            CalculatorAction.Operator(CalculatorOperator.Add),
            CalculatorAction.Operator(CalculatorOperator.Multiply),
            CalculatorAction.Number(2),
            CalculatorAction.Equals,
        )
        assertEquals("10", state.displayValue)
    }

    @Test
    fun `percent divides only current display by one hundred`() {
        assertEquals(
            "0.5",
            reduce(input("50"), listOf(CalculatorAction.Percent)).displayValue,
        )
        assertEquals(
            "0.05",
            reduce(input("5"), listOf(CalculatorAction.Percent)).displayValue,
        )
    }

    @Test
    fun `toggle sign is reversible and zero remains zero`() {
        var state = reduce(input("5"), listOf(CalculatorAction.ToggleSign))
        assertEquals("-5", state.displayValue)
        state = engine.reduce(state, CalculatorAction.ToggleSign)
        assertEquals("5", state.displayValue)
        assertEquals("0", stateAfter(CalculatorAction.ToggleSign).displayValue)
    }

    @Test
    fun `delete removes one character predictably`() {
        assertEquals("12", reduce(input("123"), listOf(CalculatorAction.Delete)).displayValue)
        assertEquals("0", reduce(input("1"), listOf(CalculatorAction.Delete)).displayValue)
        assertEquals("1.", reduce(input("1.2"), listOf(CalculatorAction.Delete)).displayValue)
    }

    @Test
    fun `clear restores initial state from an active calculation`() {
        val state = stateAfter(
            CalculatorAction.Number(8),
            CalculatorAction.Operator(CalculatorOperator.Add),
            CalculatorAction.Number(4),
            CalculatorAction.Clear,
        )
        assertEquals(CalculatorState(), state)
    }

    @Test
    fun `division by zero returns recoverable error`() {
        var state = calculate("8", CalculatorOperator.Divide, "0")
        assertEquals(CalculatorError.DivisionByZero, state.error)
        assertTrue(state.displayValue != "Infinity" && state.displayValue != "NaN")

        state = engine.reduce(state, CalculatorAction.Number(4))
        assertNull(state.error)
        assertEquals("4", state.displayValue)
    }

    @Test
    fun `repeated equals repeats the last operation`() {
        var state = calculate("5", CalculatorOperator.Add, "2")
        assertEquals("7", state.displayValue)
        state = engine.reduce(state, CalculatorAction.Equals)
        assertEquals("9", state.displayValue)
        state = engine.reduce(state, CalculatorAction.Equals)
        assertEquals("11", state.displayValue)
    }

    @Test
    fun `result formatting removes zeros and uses scientific notation for long values`() {
        assertEquals("1.5", calculate("1.5000", CalculatorOperator.Add, "0").displayValue)
        assertEquals("2", calculate("2.000", CalculatorOperator.Add, "0").displayValue)
        assertEquals("0.3", calculate("0.1", CalculatorOperator.Add, "0.2").displayValue)

        val large = calculate(
            "999999999999999",
            CalculatorOperator.Multiply,
            "999999999999999",
        ).displayValue
        assertTrue(large.contains("E"))
        assertTrue(large.length <= 16)
    }

    private fun calculate(
        left: String,
        operator: CalculatorOperator,
        right: String,
    ): CalculatorState = reduce(
        CalculatorState(),
        inputActions(left) +
            CalculatorAction.Operator(operator) +
            inputActions(right) +
            CalculatorAction.Equals,
    )

    private fun input(value: String): CalculatorState = reduce(
        CalculatorState(),
        inputActions(value),
    )

    private fun inputActions(value: String): List<CalculatorAction> = value.map { character ->
        when (character) {
            '.' -> CalculatorAction.Decimal
            else -> CalculatorAction.Number(character.digitToInt())
        }
    }

    private fun stateAfter(vararg actions: CalculatorAction): CalculatorState =
        reduce(CalculatorState(), actions.toList())

    private fun reduce(
        initial: CalculatorState,
        actions: List<CalculatorAction>,
    ): CalculatorState = actions.fold(initial, engine::reduce)
}

