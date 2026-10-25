package com.nemo.deltacalc.core

import java.math.BigInteger
import kotlin.math.abs

/**
 * 表达式求值器：把 LaTeX 子集直接递归下降求值，返回精确分数或近似值。
 * 不解析 "="，方程请交给 EquationSolver。
 */
class ExpressionEngine {

    fun evaluate(latex: String): Num {
        if (latex.isBlank()) throw CalcException("还没有输入算式")
        val parser = ExprParser(LatexLexer(latex))
        val value = parser.parseExpression()
        parser.expectEnd()
        return value
    }
}

private class ExprParser(private val lex: LatexLexer) {

    fun parseExpression(): Num {
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

    private fun parseTerm(): Num {
        var value = parseUnary()
        while (true) {
            val t = lex.peek()
            when {
                t.type == TokType.OP && (t.text == "*" || t.text == "/") -> {
                    lex.next()
                    val rhs = parseUnary()
                    value = if (t.text == "*") value * rhs else value / rhs
                }
                // 隐式乘法：2\pi、3(4)、2\sqrt{2}
                t.type == TokType.NUMBER || t.type == TokType.IDENT ||
                    t.type == TokType.LPAREN || t.type == TokType.LBRACE -> {
                    value = value * parseUnary()
                }
                else -> return value
            }
        }
    }

    private fun parseUnary(): Num {
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

    private fun parsePower(): Num {
        val base = parsePrimary()
        val t = lex.peek()
        if (t.type == TokType.OP && t.text == "^") {
            lex.next()
            val exponent = parseUnary()
            return base.pow(exponent)
        }
        return base
    }

    private fun parsePrimary(): Num {
        val t = lex.next()
        return when (t.type) {
            TokType.NUMBER -> Num.of(
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
            TokType.IDENT -> parseIdentifier(t.text)
            TokType.END -> throw CalcException("公式不完整")
            else -> throw CalcException("这里不该出现：${t.text}")
        }
    }

    private fun parseIdentifier(name: String): Num = when (name) {
        "frac" -> {
            expect(TokType.LBRACE, "{")
            val numerator = parseExpression()
            expect(TokType.RBRACE, "}")
            expect(TokType.LBRACE, "{")
            val denominator = parseExpression()
            expect(TokType.RBRACE, "}")
            numerator / denominator
        }

        "sqrt" -> {
            var index = 2
            if (lex.peek().type == TokType.LBRACKET) {
                lex.next()
                val n = parseExpression()
                index = requireSmallInt(n, "根指数")
                expect(TokType.RBRACKET, "]")
            }
            expect(TokType.LBRACE, "{")
            val v = parseExpression()
            expect(TokType.RBRACE, "}")
            v.nthRoot(index)
        }

        "placeholder" -> throw CalcException("公式还有空位没填")

        "pi" -> Num.approx(Math.PI)
        "e" -> Num.approx(Math.E)

        "sin", "cos", "tan", "cot", "sec", "csc",
        "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh",
        "ln", "log", "lg", "exp", "abs" -> applyFunction(name, parseFunctionArg())

        "operatorname" -> {
            expect(TokType.LBRACE, "{")
            while (lex.peek().type != TokType.RBRACE && lex.peek().type != TokType.END) lex.next()
            expect(TokType.RBRACE, "}")
            parseFunctionArg()
        }

        else -> throw CalcException("未知符号：$name")
    }

    private fun parseFunctionArg(): Num {
        return when (lex.peek().type) {
            TokType.LPAREN -> {
                lex.next()
                val v = parseExpression()
                expect(TokType.RPAREN, ")")
                v
            }
            TokType.LBRACE -> {
                lex.next()
                val v = parseExpression()
                expect(TokType.RBRACE, "}")
                v
            }
            else -> parseUnary()
        }
    }

    private fun applyFunction(name: String, arg: Num): Num {
        if (name == "abs") {
            val e = arg.exact
            return if (e != null) Num.of(e.abs()) else Num.approx(abs(arg.approx))
        }
        val x = arg.approx
        return when (name) {
            "sin" -> Num.approx(Math.sin(x))
            "cos" -> Num.approx(Math.cos(x))
            "tan" -> Num.approx(Math.tan(x))
            "cot" -> {
                val t = Math.tan(x)
                if (abs(t) < 1e-15) throw CalcException("cot 在该点无定义")
                Num.approx(1.0 / t)
            }
            "sec" -> {
                val c = Math.cos(x)
                if (abs(c) < 1e-15) throw CalcException("sec 在该点无定义")
                Num.approx(1.0 / c)
            }
            "csc" -> {
                val s = Math.sin(x)
                if (abs(s) < 1e-15) throw CalcException("csc 在该点无定义")
                Num.approx(1.0 / s)
            }
            "arcsin" -> {
                if (x < -1 || x > 1) throw CalcException("arcsin 的定义域是 [−1, 1]")
                Num.approx(Math.asin(x))
            }
            "arccos" -> {
                if (x < -1 || x > 1) throw CalcException("arccos 的定义域是 [−1, 1]")
                Num.approx(Math.acos(x))
            }
            "arctan" -> Num.approx(Math.atan(x))
            "sinh" -> Num.approx(Math.sinh(x))
            "cosh" -> Num.approx(Math.cosh(x))
            "tanh" -> Num.approx(Math.tanh(x))
            "ln" -> {
                if (x <= 0) throw CalcException("ln 要求真数大于 0")
                Num.approx(Math.log(x))
            }
            "log", "lg" -> {
                if (x <= 0) throw CalcException("log 要求真数大于 0")
                Num.approx(Math.log10(x))
            }
            "exp" -> Num.approx(Math.exp(x))
            else -> throw CalcException("未知函数：$name")
        }
    }

    private fun requireSmallInt(value: Num, what: String): Int {
        val e = value.exact ?: throw CalcException("$what 必须是整数")
        if (!e.isInteger || e.num.abs() > BigInteger.valueOf(64)) {
            throw CalcException("$what 超出范围")
        }
        return e.num.toInt()
    }

    fun expectEnd() {
        val t = lex.peek()
        if (t.type == TokType.EQUALS) throw CalcException("等号请在方程模式里使用")
        if (t.type != TokType.END) throw CalcException("多余的内容：${t.text}")
    }

    private fun expect(type: TokType, display: String) {
        val t = lex.next()
        if (t.type != type) throw CalcException("缺少 $display")
    }
}
