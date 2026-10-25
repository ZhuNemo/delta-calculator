package com.nemo.deltacalc.core

internal enum class TokType {
    NUMBER, IDENT, OP, LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET, EQUALS, END
}

internal data class Tok(val type: TokType, val text: String)

/**
 * 极简 LaTeX 词法分析器，只覆盖计算器/方程需要的子集。
 * 支持：数字、x/y、\frac、\sqrt、^、括号、\cdot \times \div、常用函数。
 */
internal class LatexLexer(private val src: String) {

    private var pos = 0
    private var lookahead: Tok? = null

    fun peek(): Tok = lookahead ?: readToken().also { lookahead = it }

    fun next(): Tok {
        val t = lookahead
        if (t != null) {
            lookahead = null
            return t
        }
        return readToken()
    }

    private fun skipSpace() {
        while (pos < src.length) {
            val c = src[pos]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '~') pos++ else break
        }
    }

    private fun readToken(): Tok {
        skipSpace()
        if (pos >= src.length) return Tok(TokType.END, "")
        val c = src[pos]
        if (c.isDigit() || c == '.') return readNumber()
        if (c == '\\') return readCommand()
        if (c.isLetter()) {
            pos++
            return Tok(TokType.IDENT, c.toString())
        }
        pos++
        return when (c) {
            '(' -> Tok(TokType.LPAREN, "(")
            ')' -> Tok(TokType.RPAREN, ")")
            '[' -> Tok(TokType.LBRACKET, "[")
            ']' -> Tok(TokType.RBRACKET, "]")
            '{' -> Tok(TokType.LBRACE, "{")
            '}' -> Tok(TokType.RBRACE, "}")
            '+', '-', '*', '/' -> Tok(TokType.OP, c.toString())
            '^' -> Tok(TokType.OP, "^")
            '=' -> Tok(TokType.EQUALS, "=")
            '×', '·', '⋅', '∙' -> Tok(TokType.OP, "*")
            '÷' -> Tok(TokType.OP, "/")
            '−', '–' -> Tok(TokType.OP, "-")
            'π' -> Tok(TokType.IDENT, "pi")
            '√' -> Tok(TokType.IDENT, "sqrt")
            'ⅇ' -> Tok(TokType.IDENT, "e")
            else -> throw CalcException("无法识别的符号：$c")
        }
    }

    private fun readNumber(): Tok {
        val start = pos
        var hasDot = false
        while (pos < src.length) {
            val c = src[pos]
            when {
                c.isDigit() -> pos++
                c == '.' && !hasDot -> {
                    hasDot = true
                    pos++
                }
                else -> break
            }
        }
        return Tok(TokType.NUMBER, src.substring(start, pos))
    }

    private fun readCommand(): Tok {
        pos++ // 跳过反斜杠
        val start = pos
        while (pos < src.length && (src[pos].isLetter() || src[pos] == '@')) pos++
        if (pos == start) {
            // \, \; \! 之类间距命令，直接忽略
            if (pos < src.length) pos++
            return readToken()
        }
        val name = src.substring(start, pos)
        return when (name) {
            "frac", "dfrac", "tfrac" -> Tok(TokType.IDENT, "frac")
            "sqrt" -> Tok(TokType.IDENT, "sqrt")
            "cdot", "times", "ast" -> Tok(TokType.OP, "*")
            "div" -> Tok(TokType.OP, "/")
            "left", "right", "displaystyle", "textstyle" -> readToken()
            "placeholder" -> Tok(TokType.IDENT, "placeholder")
            "pi" -> Tok(TokType.IDENT, "pi")
            "exponentialE" -> Tok(TokType.IDENT, "e")
            "sin", "cos", "tan", "cot", "sec", "csc",
            "arcsin", "arccos", "arctan",
            "sinh", "cosh", "tanh",
            "ln", "log", "lg", "exp", "abs" -> Tok(TokType.IDENT, name)
            "operatorname" -> Tok(TokType.IDENT, "operatorname")
            else -> throw CalcException("暂不支持的符号：\\$name")
        }
    }
}
