package com.nemo.deltacalc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nemo.deltacalc.data.KeypadStyle

enum class KeyAction { Insert, Evaluate, Backspace, Clear }

enum class KeyKind { Number, Operator, Function, Action, Equals }

data class KeySpec(
    val label: String,
    val insert: String = "",
    val action: KeyAction = KeyAction.Insert,
    val kind: KeyKind = KeyKind.Number,
    val span: Float = 1f,
)

private val PH = "\\placeholder{}"

private fun num(label: String) = KeySpec(label, label, KeyAction.Insert, KeyKind.Number)
private fun op(label: String, insert: String) = KeySpec(label, insert, KeyAction.Insert, KeyKind.Operator)
private fun fn(label: String, insert: String) = KeySpec(label, insert, KeyAction.Insert, KeyKind.Function)

/**
 * 键盘布局。竖屏是经典 4×5 数字键盘，横屏是 7×5 科学键盘。
 * 所有按键都直接产出 LaTeX 片段，公式所见即所得。
 */
object Keypads {

    /** 竖屏：4 列 5 行 */
    val portrait: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("AC", action = KeyAction.Clear, kind = KeyKind.Action),
            fn("(", "("),
            fn(")", ")"),
            op("÷", "\\div "),
        ),
        listOf(num("7"), num("8"), num("9"), op("×", "\\times ")),
        listOf(num("4"), num("5"), num("6"), op("−", "-")),
        listOf(num("1"), num("2"), num("3"), op("+", "+")),
        listOf(
            num("0"),
            num("."),
            KeySpec("⌫", action = KeyAction.Backspace, kind = KeyKind.Action),
            KeySpec("=", action = KeyAction.Evaluate, kind = KeyKind.Equals),
        ),
    )

    /** 横屏：7 列 5 行，科学函数一应俱全 */
    val landscape: List<List<KeySpec>> = listOf(
        listOf(
            fn("sin", "\\sin\\left($PH\\right)"),
            fn("cos", "\\cos\\left($PH\\right)"),
            fn("tan", "\\tan\\left($PH\\right)"),
            fn("ln", "\\ln\\left($PH\\right)"),
            fn("log", "\\log\\left($PH\\right)"),
            fn("√", "\\sqrt{$PH}"),
            KeySpec("AC", action = KeyAction.Clear, kind = KeyKind.Action),
        ),
        listOf(
            fn("(", "("),
            fn(")", ")"),
            fn("xʸ", "^{$PH}"),
            fn("π", "\\pi "),
            fn("e", "e"),
            fn("a/b", "\\frac{$PH}{$PH}"),
            KeySpec("⌫", action = KeyAction.Backspace, kind = KeyKind.Action),
        ),
        listOf(num("7"), num("8"), num("9"), op("÷", "\\div "), fn("x", "x"), fn("y", "y"), op("×", "\\times ")),
        listOf(num("4"), num("5"), num("6"), op("−", "-"), num("."), fn(",", ","), op("+", "+")),
        listOf(
            num("1"), num("2"), num("3"), num("0"),
            KeySpec("=", action = KeyAction.Evaluate, kind = KeyKind.Equals, span = 3f),
        ),
    )

    /** 竖屏顶部快捷条（方程模式需要 x/y/=） */
    val shortcutBar: List<KeySpec> = listOf(
        fn("x", "x"),
        fn("y", "y"),
        fn("xʸ", "^{$PH}"),
        fn("√", "\\sqrt{$PH}"),
        fn("π", "\\pi "),
        fn("a/b", "\\frac{$PH}{$PH}"),
        op("=", "="),
    )
}

@Composable
fun MathKeypad(
    rows: List<List<KeySpec>>,
    style: KeypadStyle,
    onKey: (KeySpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                row.forEach { spec ->
                    KeyCell(
                        spec = spec,
                        style = style,
                        modifier = Modifier
                            .weight(spec.span)
                            .fillMaxSize(),
                        onClick = { onKey(spec) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyCell(
    spec: KeySpec,
    style: KeypadStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val baseColor = when (spec.kind) {
        KeyKind.Equals -> scheme.primary
        KeyKind.Operator -> scheme.primaryContainer
        KeyKind.Function -> scheme.secondaryContainer
        KeyKind.Action -> scheme.surfaceContainerHigh
        KeyKind.Number -> scheme.surfaceContainer
    }
    val contentColor = when (spec.kind) {
        KeyKind.Equals -> scheme.onPrimary
        KeyKind.Operator -> scheme.onPrimaryContainer
        KeyKind.Function -> scheme.onSecondaryContainer
        KeyKind.Action -> scheme.onSurfaceVariant
        KeyKind.Number -> scheme.onSurface
    }
    val outlined = style == KeypadStyle.OUTLINED
    Surface(
        onClick = onClick,
        modifier = modifier.padding(2.dp).heightIn(min = 44.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (outlined) Color.Transparent else baseColor,
        contentColor = contentColor,
        tonalElevation = if (style == KeypadStyle.FILLED) 1.dp else 0.dp,
        border = if (outlined) BorderStroke(1.dp, scheme.outlineVariant) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = spec.label,
                fontSize = if (spec.label.length >= 3) 15.sp else 21.sp,
                fontWeight = if (spec.kind == KeyKind.Equals) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
