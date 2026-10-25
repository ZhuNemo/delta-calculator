package com.nemo.deltacalc.core

/**
 * 把 LaTeX 简化成 Unicode，用于历史列表、步骤说明这类不需要完整排版的地方。
 * 真正的公式排版交给 MathLive。
 */
object LatexPretty {

    private val fracRegex = Regex("""\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}""")
    private val sqrtRegex = Regex("""\\sqrt\{([^{}]*)\}""")

    fun toPlain(latex: String): String {
        var s = latex

        // \frac 可能嵌套，反复替换直到稳定
        var guard = 0
        while (guard++ < 16) {
            val next = fracRegex.replace(s) { m ->
                val n = m.groupValues[1].trim()
                val d = m.groupValues[2].trim()
                val simple = Regex("""^[0-9a-zA-Z.]+$""")
                if (simple.matches(n) && simple.matches(d)) "$n/$d" else "($n)/($d)"
            }
            if (next == s) break
            s = next
        }

        s = sqrtRegex.replace(s) { m -> "√(${m.groupValues[1]})" }

        val map = listOf(
            "\\left" to "",
            "\\right" to "",
            "\\times" to "×",
            "\\cdot" to "·",
            "\\div" to "÷",
            "\\pm" to "±",
            "\\pi" to "π",
            "\\placeholder{}" to "▢",
            "\\placeholder" to "▢",
            "\\," to " ",
            "\\;" to " ",
            "\\!" to "",
            "\\ " to " ",
        )
        for ((from, to) in map) s = s.replace(from, to)

        val sup = mapOf(
            "^{2}" to "²", "^{3}" to "³", "^{4}" to "⁴",
            "^2" to "²", "^3" to "³", "^4" to "⁴",
            "^{−1}" to "⁻¹", "^{-1}" to "⁻¹",
        )
        for ((from, to) in sup) s = s.replace(from, to)

        val sub = mapOf(
            "_{1}" to "₁", "_{2}" to "₂", "_1" to "₁", "_2" to "₂",
            "_{1,2}" to "₁,₂",
        )
        for ((from, to) in sub) s = s.replace(from, to)

        // 去掉没被识别的命令，保留命令名本身
        s = s.replace(Regex("""\\([a-zA-Z]+)""")) { it.groupValues[1] }
        s = s.replace("{", "").replace("}", "")
        s = s.replace("\\", "")
        return s.trim()
    }

    /** 键盘上显示用的短标签（例如 \frac{}{} → a/b）。 */
    fun isRenderable(latex: String): Boolean = latex.isNotBlank()
}
