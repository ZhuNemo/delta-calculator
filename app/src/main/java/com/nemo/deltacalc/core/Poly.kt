package com.nemo.deltacalc.core

/**
 * 二元多项式（次数上限 32），用于精确求解一元二次 / 二元一次方程。
 * key 由两个指数打包而成：高 32 位 x 的次数，低 32 位 y 的次数。
 */
class Poly private constructor(private val terms: Map<Long, Rational>) {

    companion object {
        val ZERO = Poly(emptyMap())

        fun constant(value: Rational): Poly =
            if (value.signum() == 0) ZERO else Poly(mapOf(0L to value))

        fun x(): Poly = Poly(mapOf(key(1, 0) to Rational.ONE))
        fun y(): Poly = Poly(mapOf(key(0, 1) to Rational.ONE))

        fun key(ex: Int, ey: Int): Long = (ex.toLong() shl 32) or (ey.toLong() and 0xFFFFFFFFL)
        fun expX(k: Long): Int = (k ushr 32).toInt()
        fun expY(k: Long): Int = (k and 0xFFFFFFFFL).toInt()
    }

    val isZero: Boolean get() = terms.isEmpty()

    fun coefficient(ex: Int, ey: Int): Rational = terms[key(ex, ey)] ?: Rational.ZERO

    fun constantValue(): Rational? {
        var sum = Rational.ZERO
        for ((k, v) in terms) {
            if (expX(k) != 0 || expY(k) != 0) return null
            sum += v
        }
        return sum
    }

    fun hasVariable(): Boolean = terms.keys.any { expX(it) != 0 || expY(it) != 0 }

    fun hasY(): Boolean = terms.keys.any { expY(it) != 0 }

    fun degreeInX(): Int = terms.keys.maxOfOrNull { expX(it) } ?: 0

    fun degreeInY(): Int = terms.keys.maxOfOrNull { expY(it) } ?: 0

    fun maxDegree(): Int = terms.keys.maxOfOrNull { expX(it) + expY(it) } ?: 0

    operator fun plus(other: Poly): Poly {
        if (isZero) return other
        if (other.isZero) return this
        val merged = HashMap<Long, Rational>(terms)
        for ((k, v) in other.terms) {
            val current = merged[k]
            val next = if (current == null) v else current + v
            if (next.signum() == 0) merged.remove(k) else merged[k] = next
        }
        return Poly(merged)
    }

    operator fun minus(other: Poly): Poly = this + (-other)

    operator fun unaryMinus(): Poly = Poly(terms.mapValues { it.value.negate() })

    operator fun times(other: Poly): Poly {
        if (isZero || other.isZero) return ZERO
        val merged = HashMap<Long, Rational>()
        for ((k1, v1) in terms) {
            for ((k2, v2) in other.terms) {
                val ex = expX(k1) + expX(k2)
                val ey = expY(k1) + expY(k2)
                if (ex > 32 || ey > 32) throw CalcException("方程次数过高")
                val k = key(ex, ey)
                val next = (merged[k] ?: Rational.ZERO) + v1 * v2
                if (next.signum() == 0) merged.remove(k) else merged[k] = next
            }
        }
        return Poly(merged)
    }

    /** 只允许除以非零常数。 */
    operator fun div(other: Poly): Poly {
        val c = other.constantValue()
        if (c == null) throw CalcException("方程里不支持用变量作除数")
        if (c.signum() == 0) throw CalcException("除数不能为 0")
        return Poly(terms.mapValues { it.value.div(c) })
    }

    fun scale(factor: Rational): Poly =
        if (factor.signum() == 0) ZERO else Poly(terms.mapValues { it.value * factor })

    fun pow(exponent: Int): Poly {
        if (exponent < 0) throw CalcException("方程里不支持负指数")
        if (exponent > 32) throw CalcException("次数过高")
        var result = constant(Rational.ONE)
        var base = this
        var e = exponent
        while (e > 0) {
            if (e and 1 == 1) result *= base
            e = e shr 1
            if (e > 0) base *= base
        }
        return result
    }

    override fun toString(): String =
        if (isZero) "0" else terms.entries.joinToString(" + ") { (k, v) ->
            "($v)·x^${expX(k)}·y^${expY(k)}"
        }
}

/** 把方程字符串（可含一个 =）解析为「左边 − 右边」的多项式。 */
internal class PolyParser(private val lex: LatexLexer) {

    fun parseEquation(): Poly {
        val left = parseExpression()
        val t = lex.peek()
        if (t.type == TokType.EQUALS) {
            lex.next()
            val right = parseExpression()
            expectEnd()
            return left - right
        }
        expectEnd()
        return left
    }

    private fun parseExpression(): Poly {
        var value = parseTerm()
        while (true) {
            val t = lex.peek()
            if (t.type == TokType.OP && (t.text == "+" || t.text == "-")) {
                lex.next()
                val rhs = parseTerm()
                value = if (t.text == "+") value + rhs else value - rhs
            } else {
                return value
            }
        }
    }

    private fun parseTerm(): Poly {
        var value = parseUnary()
        while (true) {
            val t = lex.peek()
            when {
                t.type == TokType.OP && (t.text == "*" || t.text == "/") -> {
                    lex.next()
                    val rhs = parseUnary()
                    value = if (t.text == "*") value * rhs else value / rhs
                }
                t.type == TokType.NUMBER || t.type == TokType.IDENT ||
                    t.type == TokType.LPAREN || t.type == TokType.LBRACE -> {
                    value *= parseUnary()
                }
                else -> return value
            }
        }
    }

    private fun parseUnary(): Poly {
        val t = lex.peek()
        if (t.type == TokType.OP && t.text == "-") {
            lex.next()
            return -parseUnary()
        }
        if (t.type == TokType.OP && t.text == "+") {
            lex.next()
            return parseUnary()
        }
        return parsePower()
    }

    private fun parsePower(): Poly {
        val base = parsePrimary()
        val t = lex.peek()
        if (t.type == TokType.OP && t.text == "^") {
            lex.next()
            val exponent = if (lex.peek().type == TokType.LBRACE) {
                lex.next()
                val inner = parseExpression()
                expect(TokType.RBRACE, "}")
                inner
            } else {
                val tok = lex.next()
                if (tok.type != TokType.NUMBER) throw CalcException("指数必须是非负整数")
                Poly.constant(
                    Rational.parseDecimalOrNull(tok.text)
                        ?: throw CalcException("指数格式错误")
                )
            }
            return base.pow(requireConstInt(exponent, "指数"))
        }
        return base
    }

    private fun parsePrimary(): Poly {
        val t = lex.next()
        return when (t.type) {
            TokType.NUMBER -> Poly.constant(
                Rational.parseDecimalOrNull(t.text) ?: throw CalcException("数字格式错误：${t.text}")
            )

            TokType.LPAREN -> {
                val v = parseExpression()
                expect(TokType.RPAREN, ")")
                v
            }

            TokType.LBRACE -> {
                val v = parseExpression()
                expect(TokType.RBRACE, "}")
                v
            }

            TokType.IDENT -> when (t.text) {
                "x" -> Poly.x()
                "y" -> Poly.y()
                "frac" -> {
                    expect(TokType.LBRACE, "{")
                    val numerator = parseExpression()
                    expect(TokType.RBRACE, "}")
                    expect(TokType.LBRACE, "{")
                    val denominator = parseExpression()
                    expect(TokType.RBRACE, "}")
                    numerator / denominator
                }

                "placeholder" -> throw CalcException("公式还有空位没填")
                "pi", "e" -> throw CalcException("方程请使用精确系数，不要用 π 或 e")
                else -> throw CalcException("方程只支持 x、y 的多项式")
            }

            TokType.END -> throw CalcException("公式不完整")
            else -> throw CalcException("这里不该出现：${t.text}")
        }
    }

    private fun requireConstInt(value: Poly, what: String): Int {
        val c = value.constantValue() ?: throw CalcException("$what 必须是常数")
        if (!c.isInteger) throw CalcException("$what 必须是整数")
        if (c.num.compareTo(java.math.BigInteger.valueOf(32)) > 0 ||
            c.num.compareTo(java.math.BigInteger.valueOf(-32)) < 0
        ) {
            throw CalcException("$what 超出范围（−32…32）")
        }
        return c.num.toInt()
    }

    private fun expect(type: TokType, display: String) {
        val t = lex.next()
        if (t.type != type) throw CalcException("缺少 $display")
    }

    private fun expectEnd() {
        val t = lex.peek()
        if (t.type != TokType.END) throw CalcException("多余的内容：${t.text}")
    }
}
