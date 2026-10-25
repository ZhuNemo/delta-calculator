package com.nemo.deltacalc.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/** 计算过程中的可预期错误，直接展示给用户。 */
class CalcException(message: String) : RuntimeException(message)

/**
 * 精确有理数。所有四则运算保持分数精度，避免 0.1 + 0.2 != 0.3 这类浮点问题。
 * 内部始终保证：分母为正、分子分母互质。
 */
class Rational private constructor(
    val num: BigInteger,
    val den: BigInteger,
) : Comparable<Rational> {

    val isInteger: Boolean get() = den == BigInteger.ONE

    fun signum(): Int = num.signum()

    fun abs(): Rational = if (num.signum() < 0) Rational(num.negate(), den) else this

    operator fun unaryMinus(): Rational = Rational(num.negate(), den)

    operator fun plus(other: Rational): Rational =
        of(num * other.den + other.num * den, den * other.den)

    operator fun minus(other: Rational): Rational =
        of(num * other.den - other.num * den, den * other.den)

    operator fun times(other: Rational): Rational = of(num * other.num, den * other.den)

    operator fun div(other: Rational): Rational {
        if (other.num.signum() == 0) throw CalcException("除数不能为 0")
        return of(num * other.den, den * other.num)
    }

    fun pow(exponent: Int): Rational {
        if (exponent == 0) return ONE
        if (exponent > 0) {
            if (exponent > 100_000) throw CalcException("指数过大")
            return of(num.pow(exponent), den.pow(exponent))
        }
        if (num.signum() == 0) throw CalcException("0 不能取负指数")
        val e = -exponent
        if (e > 100_000) throw CalcException("指数过大")
        return of(den.pow(e), num.pow(e))
    }

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    fun toBigDecimal(scale: Int = 12): BigDecimal =
        BigDecimal(num).divide(BigDecimal(den), MathContext(scale + 2, RoundingMode.HALF_UP))

    /** 人类可读写法：整数不带分母，分数写成 a/b。 */
    override fun toString(): String = if (isInteger) num.toString() else "$num/$den"

    override fun equals(other: Any?): Boolean =
        other is Rational && num == other.num && den == other.den

    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()

    override fun compareTo(other: Rational): Int =
        (num * other.den).compareTo(other.num * den)

    companion object {
        val ZERO: Rational = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE: Rational = Rational(BigInteger.ONE, BigInteger.ONE)
        val TWO: Rational = Rational(BigInteger.valueOf(2), BigInteger.ONE)
        val TEN: Rational = Rational(BigInteger.TEN, BigInteger.ONE)

        fun of(n: Long, d: Long = 1L): Rational =
            of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): Rational {
            require(d.signum() != 0) { "分母不能为 0" }
            var nn = n
            var dd = d
            if (dd.signum() < 0) {
                nn = nn.negate()
                dd = dd.negate()
            }
            if (nn.signum() == 0) return ZERO
            val g = nn.gcd(dd)
            return if (g > BigInteger.ONE) Rational(nn.divide(g), dd.divide(g)) else Rational(nn, dd)
        }

        /** 解析 "12"、"-3.25"、"7/8" 这类字面量。 */
        fun parse(text: String): Rational? {
            val s = text.trim()
            if (s.isEmpty()) return null
            val slash = s.indexOf('/')
            if (slash > 0) {
                val n = s.substring(0, slash).toBigIntegerOrNull() ?: return null
                val d = s.substring(slash + 1).toBigIntegerOrNull() ?: return null
                if (d.signum() == 0) return null
                return of(n, d)
            }
            return parseDecimalOrNull(s)
        }

        /** 把 "3.14" 精确解析成 314/100。 */
        fun parseDecimalOrNull(text: String): Rational? {
            var s = text.trim()
            if (s.isEmpty()) return null
            var negative = false
            if (s.startsWith("-")) {
                negative = true
                s = s.substring(1)
            } else if (s.startsWith("+")) {
                s = s.substring(1)
            }
            val dot = s.indexOf('.')
            val result = if (dot < 0) {
                val bi = s.toBigIntegerOrNull() ?: return null
                of(bi)
            } else {
                val intPart = s.substring(0, dot).ifEmpty { "0" }
                val fracPart = s.substring(dot + 1)
                if (fracPart.any { !it.isDigit() }) return null
                val digits = (intPart + fracPart).toBigIntegerOrNull() ?: return null
                of(digits, BigInteger.TEN.pow(fracPart.length))
            }
            return if (negative) -result else result
        }
    }
}

/** 判断 s 是否为纯整数（用于指数判断）。 */
internal fun BigInteger.isIntInRange(min: Long, max: Long): Boolean =
    this >= BigInteger.valueOf(min) && this <= BigInteger.valueOf(max)

/** 整数平方根：完全平方返回根，否则返回 null。 */
fun bigSqrt(n: BigInteger): BigInteger? {
    if (n.signum() < 0) return null
    if (n.signum() == 0) return BigInteger.ZERO
    if (n == BigInteger.ONE) return BigInteger.ONE
    var x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
    while (true) {
        val y = x.add(n.divide(x)).shiftRight(1)
        if (y >= x) break
        x = y
    }
    return if (x.multiply(x) == n) x else null
}

/** 精确开平方（分子分母都是完全平方时）。 */
fun sqrtExact(r: Rational): Rational? {
    if (r.signum() < 0) return null
    val n = bigSqrt(r.num) ?: return null
    val d = bigSqrt(r.den) ?: return null
    return Rational.of(n, d)
}

/** √m 化简结果：outside * √radicand。 */
data class Surd(val outside: Rational, val radicand: Rational) {
    /** radicand == 1 表示其实是有理数。 */
    val isRational: Boolean get() = radicand == Rational.ONE
}

/** 把 √r 化成 k√m（m 为无平方因子的整数，且分母已有理化）。 */
fun simplifySqrt(r: Rational): Surd {
    if (r.signum() < 0) throw CalcException("负数不能开平方")
    if (r == Rational.ZERO) return Surd(Rational.ZERO, Rational.ONE)
    val (outNum, inNum) = factorOutSquares(r.num)
    val (outDen, inDen) = factorOutSquares(r.den)
    // √(a/b) = √a / √b = (oa/(od*ib)) * √(ia*ib)
    val outside = Rational.of(outNum, outDen.multiply(inDen))
    val inside = Rational.of(inNum.multiply(inDen), BigInteger.ONE)
    return Surd(outside, inside)
}

/** n = outside² * inside，返回 (outside, inside)。 */
private fun factorOutSquares(n: BigInteger): Pair<BigInteger, BigInteger> {
    if (n.signum() == 0) return BigInteger.ZERO to BigInteger.ONE
    var rest = n.abs()
    var outside = BigInteger.ONE
    var inside = BigInteger.ONE
    var p = BigInteger.valueOf(2)
    val limit = BigInteger.valueOf(1_000_000)
    while (p.multiply(p) <= rest && p <= limit) {
        var e = 0
        while (rest.mod(p).signum() == 0) {
            rest = rest.divide(p)
            e++
        }
        if (e > 0) {
            if (e / 2 > 0) outside = outside.multiply(p.pow(e / 2))
            if (e % 2 == 1) inside = inside.multiply(p)
        }
        p = if (p == BigInteger.valueOf(2)) BigInteger.valueOf(3) else p.add(BigInteger.valueOf(2))
    }
    inside = inside.multiply(rest)
    return outside to inside
}

/** 数字的展示格式。 */
fun formatDouble(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "∞" else "−∞"
    val abs = kotlin.math.abs(value)
    if (abs != 0.0 && (abs >= 1e12 || abs < 1e-6)) {
        return BigDecimal(value).round(MathContext(10, RoundingMode.HALF_UP))
            .stripTrailingZeros().toString()
    }
    val bd = BigDecimal(value).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
    var s = bd.toPlainString()
    if (s.endsWith(".0")) s = s.dropLast(2)
    return s.replace('-', '−')
}
