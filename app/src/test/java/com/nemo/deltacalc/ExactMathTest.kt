package com.nemo.deltacalc

import com.nemo.deltacalc.core.EquationSolver
import com.nemo.deltacalc.core.ExpressionEngine
import com.nemo.deltacalc.core.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内核测试：保证「分数精确」与「方程求解」这两条主线不被改坏。
 * 本地跑：gradle :app:testDebugUnitTest
 */
class ExactMathTest {

    @Test
    fun rationalNormalizes() {
        assertEquals("5/6", (Rational.of(1, 2) + Rational.of(1, 3)).toString())
        assertEquals("1/2", Rational.of(2, 4).toString())
        assertEquals("-1/2", Rational.of(1, -2).toString())
        assertEquals("0", Rational.of(0, 7).toString())
    }

    @Test
    fun decimalStaysExact() {
        // 浮点会得到 0.30000000000000004，这里必须是 3/10
        assertEquals("3/10", ExpressionEngine().evaluate("0.1+0.2").primaryText())
    }

    @Test
    fun fractionExpression() {
        assertEquals("3/4", ExpressionEngine().evaluate("\\frac{1}{2}+\\frac{1}{4}").primaryText())
        assertEquals("4/9", ExpressionEngine().evaluate("\\frac{2}{3}^{2}").primaryText())
    }

    @Test
    fun exactSquareRoots() {
        assertEquals("9/2", ExpressionEngine().evaluate("\\sqrt{16}+\\sqrt{\\frac{1}{4}}").primaryText())
    }

    @Test
    fun quadraticWithRationalRoots() {
        val result = EquationSolver.solveQuadratic("x^2-5x+6=0")
        assertTrue(result.plain.contains("2"))
        assertTrue(result.plain.contains("3"))
    }

    @Test
    fun quadraticWithSurd() {
        val result = EquationSolver.solveQuadratic("x^2-2=0")
        assertTrue(result.plain.contains("√2"))
    }

    @Test
    fun quadraticFractionCoefficients() {
        // 2x² − 3x + 1 = 0 → x = 1 或 1/2
        val result = EquationSolver.solveQuadratic("2x^2-3x+1=0")
        assertTrue(result.plain.contains("1/2"))
    }

    @Test
    fun linearSystemExact() {
        val result = EquationSolver.solveLinearSystem("2x+3y=8", "x-y=1")
        assertTrue(result.plain.contains("11/5"))
        assertTrue(result.plain.contains("6/5"))
    }

    @Test
    fun linearSystemParallelIsNoSolution() {
        val result = EquationSolver.solveLinearSystem("x+y=2", "2x+2y=5")
        assertTrue(result.plain.contains("无解"))
    }
}
