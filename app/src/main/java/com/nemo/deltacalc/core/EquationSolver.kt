package com.nemo.deltacalc.core

import java.math.BigInteger

data class Step(val title: String, val detail: String)

data class SolveResult(
    val headline: String,
    val plain: String,
    val latex: String,
    val steps: List<Step>,
)

/**
 * 方程求解：一元二次（ax²+bx+c=0）与二元一次方程组。
 * 全部基于精确有理数运算，判别式/根式都保留精确形式。
 */
object EquationSolver {

    fun solveQuadratic(latex: String): SolveResult {
        if (latex.isBlank()) throw CalcException("请先输入方程")
        val poly = PolyParser(LatexLexer(latex)).parseEquation()
        if (poly.hasY()) throw CalcException("一元二次方程里不要出现 y")
        if (poly.maxDegree() > 2) throw CalcException("最高次项是 2 次，请检查输入")

        val a = poly.coefficient(2, 0)
        val b = poly.coefficient(1, 0)
        val c = poly.coefficient(0, 0)

        val steps = mutableListOf<Step>()
        steps += Step("整理成标准形式", "${polyText(a, b, c)} = 0")
        steps += Step("读出系数", "a = ${r(a)}，b = ${r(b)}，c = ${r(c)}")

        if (a.signum() == 0) {
            if (b.signum() == 0) {
                return if (c.signum() == 0) {
                    SolveResult(
                        headline = "恒等式",
                        plain = "任意实数都是解",
                        latex = "x \\in \\mathbb{R}",
                        steps = steps,
                    )
                } else {
                    SolveResult(
                        headline = "无解",
                        plain = "方程无解",
                        latex = "\\varnothing",
                        steps = steps,
                    )
                }
            }
            val root = (-c) / b
            steps += Step("退化为一次方程", "${r(b)}x ${signed(c)} = 0")
            steps += Step("移项求解", "x = ${r(root)}")
            return SolveResult(
                headline = "一次方程，一个根",
                plain = "x = ${r(root)}",
                latex = "x = ${fracLatex(root)}",
                steps = steps,
            )
        }

        val delta = b * b - Rational.of(4) * a * c
        steps += Step(
            "计算判别式",
            "Δ = b² − 4ac = ${r(b)}² − 4·${r(a)}·${r(c)} = ${r(delta)}",
        )

        if (delta.signum() < 0) {
            val absDelta = delta.abs()
            val surd = simplifySqrt(absDelta)
            val denominator = Rational.of(2) * a
            val body = buildSurdExpression(-b, surd.outside, surd.radicand, denominator, imaginary = true)
            steps += Step("Δ < 0", "方程没有实数根，有一对共轭复根")
            steps += Step("求根公式", "x = (−b ± i√|Δ|) / (2a)")
            return SolveResult(
                headline = "两个共轭复根",
                plain = body.plain,
                latex = body.latex,
                steps = steps,
            )
        }

        if (delta.signum() == 0) {
            val root = (-b) / (Rational.of(2) * a)
            steps += Step("Δ = 0", "有两个相等的实数根")
            steps += Step("求根公式", "x = −b / (2a) = ${r(root)}")
            return SolveResult(
                headline = "重根",
                plain = "x₁ = x₂ = ${r(root)}",
                latex = "x_1 = x_2 = ${fracLatex(root)}",
                steps = steps,
            )
        }

        val exact = sqrtExact(delta)
        if (exact != null) {
            val denominator = Rational.of(2) * a
            val x1 = (-b + exact) / denominator
            val x2 = (-b - exact) / denominator
            steps += Step("Δ 是完全平方", "√Δ = ${r(exact)}，可以直接开出来")
            steps += Step("代入求根公式", "x = (−b ± √Δ) / (2a)")
            steps += Step("两个根", "x₁ = ${r(x1)}，x₂ = ${r(x2)}")
            return SolveResult(
                headline = "两个有理根",
                plain = "x₁ = ${r(x1)}，x₂ = ${r(x2)}",
                latex = "x_1 = ${fracLatex(x1)}, \\quad x_2 = ${fracLatex(x2)}",
                steps = steps,
            )
        }

        val surd = simplifySqrt(delta)
        val body = buildSurdExpression(
            -b, surd.outside, surd.radicand, Rational.of(2) * a, imaginary = false,
        )
        steps += Step("Δ 不是完全平方", "√Δ = ${surdText(surd)}，保留根号形式")
        steps += Step("代入求根公式", "x = (−b ± √Δ) / (2a)")
        steps += Step("近似值", approximateRootsText(a, b, delta))
        return SolveResult(
            headline = "两个无理根",
            plain = body.plain,
            latex = body.latex,
            steps = steps,
        )
    }

    fun solveLinearSystem(eq1: String, eq2: String): SolveResult {
        if (eq1.isBlank() || eq2.isBlank()) throw CalcException("两个方程都要填写")
        val p1 = PolyParser(LatexLexer(eq1)).parseEquation()
        val p2 = PolyParser(LatexLexer(eq2)).parseEquation()
        if (p1.maxDegree() > 1) throw CalcException("第一个方程不是一次方程")
        if (p2.maxDegree() > 1) throw CalcException("第二个方程不是一次方程")

        val a1 = p1.coefficient(1, 0)
        val b1 = p1.coefficient(0, 1)
        val c1 = -p1.coefficient(0, 0)
        val a2 = p2.coefficient(1, 0)
        val b2 = p2.coefficient(0, 1)
        val c2 = -p2.coefficient(0, 0)

        val steps = mutableListOf<Step>()
        steps += Step("方程 ①", linearText(a1, b1, c1))
        steps += Step("方程 ②", linearText(a2, b2, c2))

        val d = a1 * b2 - a2 * b1
        val dx = c1 * b2 - c2 * b1
        val dy = a1 * c2 - a2 * c1
        steps += Step("系数行列式", "D = a₁b₂ − a₂b₁ = ${r(d)}")
        steps += Step("Dx、Dy", "Dx = ${r(dx)}，Dy = ${r(dy)}")

        if (d.signum() == 0) {
            return if (dx.signum() == 0 && dy.signum() == 0) {
                steps += Step("D = 0 且 Dx = Dy = 0", "两个方程同解，有无穷多组解")
                SolveResult(
                    headline = "无穷多组解",
                    plain = "两个方程描述同一条直线，有无穷多组解",
                    latex = "\\text{无穷多组解}",
                    steps = steps,
                )
            } else {
                steps += Step("D = 0 但 Dx 或 Dy ≠ 0", "两条直线平行，方程组无解")
                SolveResult(
                    headline = "无解",
                    plain = "方程组无解",
                    latex = "\\varnothing",
                    steps = steps,
                )
            }
        }

        val x = dx / d
        val y = dy / d
        steps += Step("克拉默法则", "x = Dx / D，y = Dy / D")
        return SolveResult(
            headline = "唯一解",
            plain = "x = ${r(x)}，y = ${r(y)}",
            latex = "x = ${fracLatex(x)}, \\quad y = ${fracLatex(y)}",
            steps = steps,
        )
    }

    // ---------- 文本与 LaTeX 拼装 ----------

    private fun r(value: Rational): String = value.toString().replace('-', '−')

    private fun signed(value: Rational): String =
        if (value.signum() < 0) "− ${r(value.abs())}" else "+ ${r(value)}"

    private fun term(coef: Rational, body: String, first: Boolean): String {
        if (coef.signum() == 0) return ""
        val sign = when {
            coef.signum() < 0 -> if (first) "−" else " − "
            else -> if (first) "" else " + "
        }
        val abs = coef.abs()
        val coefText = if (body.isNotEmpty() && abs == Rational.ONE) "" else r(abs)
        return sign + coefText + body
    }

    private fun polyText(a: Rational, b: Rational, c: Rational): String {
        val sb = StringBuilder()
        sb.append(term(a, "x²", true))
        sb.append(term(b, "x", sb.isEmpty()))
        sb.append(term(c, "", sb.isEmpty()))
        return if (sb.isEmpty()) "0" else sb.toString()
    }

    private fun linearText(a: Rational, b: Rational, c: Rational): String {
        val sb = StringBuilder()
        sb.append(term(a, "x", true))
        sb.append(term(b, "y", sb.isEmpty()))
        val left = if (sb.isEmpty()) "0" else sb.toString()
        return "$left = ${r(c)}"
    }

    private fun fracLatex(value: Rational): String =
        if (value.isInteger) value.num.toString() else "\\frac{${value.num}}{${value.den}}"

    private fun surdText(surd: Surd): String {
        if (surd.isRational) return r(surd.outside)
        val outside = if (surd.outside == Rational.ONE) "" else r(surd.outside)
        return "${outside}√${r(surd.radicand)}"
    }

    private class SurdBody(val plain: String, val latex: String)

    /** 拼装 (−b ± k√m) / 2a，并尽量约分。 */
    private fun buildSurdExpression(
        minusB: Rational,
        outside: Rational,
        radicand: Rational,
        denominator: Rational,
        imaginary: Boolean,
    ): SurdBody {
        val l1 = lcm(minusB.den, outside.den)
        val l2 = lcm(l1, denominator.den)
        val bb = minusB.num * (l2 / minusB.den)
        val kk = outside.num * (l2 / outside.den)
        val dd = denominator.num * (l2 / denominator.den)
        var g = bb.abs().gcd(kk.abs()).gcd(dd.abs())
        if (g.signum() == 0) g = BigInteger.ONE

        val b2 = bb / g
        val k2 = kk / g
        var d2 = dd / g
        if (d2.signum() < 0) d2 = d2.negate()

        val iText = if (imaginary) "i" else ""
        val coefText = when {
            k2 == BigInteger.ONE -> ""
            k2 == BigInteger.ONE.negate() -> "−"
            else -> k2.toString().replace('-', '−')
        }
        val rootText = "√${r(radicand)}"
        val bStr = b2.toString().replace('-', '−')
        val hasLead = b2.signum() != 0

        val plus = if (hasLead) "$bStr + $coefText$iText$rootText" else "$coefText$iText$rootText"
        val minus = if (hasLead) "$bStr − $coefText$iText$rootText" else "−$coefText$iText$rootText"

        val dStr = d2.toString().replace('-', '−')
        val plain = if (d2 == BigInteger.ONE) {
            "x = $plus  或  $minus"
        } else {
            "x = ($plus) / $dStr  或  ($minus) / $dStr"
        }

        val numerator = if (hasLead) {
            "$bStr \\pm $coefText$iText\\sqrt{${radicand.num}}"
        } else {
            "\\pm $coefText$iText\\sqrt{${radicand.num}}"
        }
        val latex = if (d2 == BigInteger.ONE) {
            "x = $numerator"
        } else {
            "x = \\frac{$numerator}{${d2.toString()}}"
        }
        return SurdBody(plain, latex)
    }

    private fun approximateRootsText(a: Rational, b: Rational, delta: Rational): String {
        val d = Math.sqrt(delta.toDouble())
        val den = 2.0 * a.toDouble()
        val x1 = (-b.toDouble() + d) / den
        val x2 = (-b.toDouble() - d) / den
        return "x₁ ≈ ${formatDouble(x1)}，x₂ ≈ ${formatDouble(x2)}"
    }

    private fun lcm(a: BigInteger, b: BigInteger): BigInteger {
        if (a.signum() == 0 || b.signum() == 0) return BigInteger.ZERO
        return a.divide(a.gcd(b)).multiply(b).abs()
    }
}
