package com.nemo.deltacalc.core

import java.math.BigInteger
import kotlin.math.abs

/**
 * 计算值：要么是精确有理数，要么退化为双精度近似。
 * 只要参与运算的双方都精确，结果就保持精确（分数）。
 */
data class Num(val exact: Rational?, val approx: Double) {

    val isExact: Boolean get() = exact != null

    private inline fun binary(
        other: Num,
        exactOp: (Rational, Rational) -> Rational,
        approxOp: (Double, Double) -> Double,
    ): Num {
        val a = exact
        val b = other.exact
        return if (a != null && b != null) of(exactOp(a, b)) else approx(approxOp(approx, other.approx))
    }

    operator fun plus(other: Num): Num = binary(other, Rational::plus) { a, b -> a + b }
    operator fun minus(other: Num): Num = binary(other, Rational::minus) { a, b -> a - b }
    operator fun times(other: Num): Num = binary(other, Rational::times) { a, b -> a * b }
    operator fun div(other: Num): Num = binary(other, Rational::div) { a, b -> a / b }

    operator fun unaryMinus(): Num =
        if (exact != null) of(-exact) else approx(-approx)

    fun pow(other: Num): Num {
        val base = exact
        val exp = other.exact
        if (base != null && exp != null && exp.isInteger && exp.num.abs() <= BigInteger.valueOf(4096)) {
            return of(base.pow(exp.num.toInt()))
        }
        if (approx == 0.0 && other.approx < 0) throw CalcException("0 不能取负指数")
        return approx(Math.pow(approx, other.approx))
    }

    fun sqrt(): Num {
        val e = exact
        if (e != null) {
            if (e.signum() < 0) throw CalcException("负数不能开平方（实数范围）")
            val r = sqrtExact(e)
            if (r != null) return of(r)
        }
        if (approx < 0) throw CalcException("负数不能开平方（实数范围）")
        return approx(kotlin.math.sqrt(approx))
    }

    fun nthRoot(n: Int): Num {
        if (n < 1) throw CalcException("根指数必须为正整数")
        if (n == 1) return this
        if (n == 2) return sqrt()
        val e = exact
        if (e != null && e.signum() >= 0) {
            val numRoot = bigNthRoot(e.num, n)
            val denRoot = bigNthRoot(e.den, n)
            if (numRoot != null && denRoot != null) return of(Rational.of(numRoot, denRoot))
        }
        if (approx < 0 && n % 2 == 0) throw CalcException("负数不能开偶次方")
        val v = if (approx < 0) -Math.pow(-approx, 1.0 / n) else Math.pow(approx, 1.0 / n)
        return approx(v)
    }

    /** 主显示文本：整数或分数，退化时是小数。 */
    fun primaryText(): String {
        val e = exact
        val raw = when {
            e == null -> formatDouble(approx)
            e.isInteger -> e.num.toString()
            else -> "${e.num}/${e.den}"
        }
        return raw.replace('-', '−')
    }

    /** 分数时的近似小数，便于校核。 */
    fun secondaryText(): String? {
        val e = exact ?: return null
        if (e.isInteger) return null
        return "≈ " + formatDouble(approx)
    }

    fun asLatex(): String {
        val e = exact ?: return formatDouble(approx)
        return if (e.isInteger) e.num.toString() else "\\frac{${e.num}}{${e.den}}"
    }

    companion object {
        fun of(value: Rational): Num = Num(value, value.toDouble())
        fun of(value: Long): Num = of(Rational.of(value))
        fun approx(value: Double): Num = Num(null, value)
    }
}

/** 整数的 n 次根，完全 n 次方时返回精确根。 */
fun bigNthRoot(n: BigInteger, k: Int): BigInteger? {
    if (k <= 0) return null
    if (k == 1) return n
    if (n.signum() < 0) return null
    if (n < BigInteger.valueOf(2)) return n
    var lo = BigInteger.ZERO
    var hi = BigInteger.ONE.shiftLeft(n.bitLength() / k + 2)
    while (lo <= hi) {
        val mid = lo.add(hi).shiftRight(1)
        val p = mid.pow(k)
        val cmp = p.compareTo(n)
        when {
            cmp == 0 -> return mid
            cmp < 0 -> lo = mid.add(BigInteger.ONE)
            else -> hi = mid.subtract(BigInteger.ONE)
        }
    }
    return null
}

/** 数值工具：判断是否近似整数（用于从近似回推整数结果）。 */
internal fun Double.nearlyInteger(tolerance: Double = 1e-10): Boolean {
    val r = Math.rint(this)
    return abs(this - r) < tolerance && abs(r) < 1e15
}
