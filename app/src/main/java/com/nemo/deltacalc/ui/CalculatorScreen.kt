package com.nemo.deltacalc.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nemo.deltacalc.data.AppSettings
import com.nemo.deltacalc.ui.components.KeySpec
import com.nemo.deltacalc.ui.components.Keypads
import com.nemo.deltacalc.ui.components.MathFormulaView
import com.nemo.deltacalc.ui.components.MathKeypad
import com.nemo.deltacalc.vm.CalcMode
import com.nemo.deltacalc.vm.CalcUiState
import com.nemo.deltacalc.vm.CalculatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    vm: CalculatorViewModel,
    settings: AppSettings,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val haptic = LocalHapticFeedback.current

    val onKey: (KeySpec) -> Unit = { spec ->
        if (settings.haptic) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.onKey(spec)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Delta 草稿纸", fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.List, contentDescription = "历史记录")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { inner ->
        if (landscape) {
            Row(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeSelector(state.mode, vm::switchMode)
                    DisplayPane(
                        state = state,
                        settings = settings,
                        vm = vm,
                        modifier = Modifier.weight(1f),
                    )
                }
                MathKeypad(
                    rows = Keypads.landscape,
                    style = settings.keypadStyle,
                    onKey = onKey,
                    modifier = Modifier.weight(1.25f).fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            ) {
                ModeSelector(state.mode, vm::switchMode)
                DisplayPane(
                    state = state,
                    settings = settings,
                    vm = vm,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp),
                )
                ShortcutBar(onKey)
                MathKeypad(
                    rows = Keypads.portrait,
                    style = settings.keypadStyle,
                    onKey = onKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.05f)
                        .padding(bottom = 8.dp),
                )
            }
        }
    }

    if (state.showSteps && state.steps.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = vm::toggleSteps) {
            StepsContent(state)
        }
    }
}

@Composable
private fun ModeSelector(mode: CalcMode, onSelect: (CalcMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = mode == CalcMode.CALC,
            onClick = { onSelect(CalcMode.CALC) },
            label = { Text("计算") },
        )
        FilterChip(
            selected = mode == CalcMode.QUADRATIC,
            onClick = { onSelect(CalcMode.QUADRATIC) },
            label = { Text("一元二次") },
        )
        FilterChip(
            selected = mode == CalcMode.SYSTEM,
            onClick = { onSelect(CalcMode.SYSTEM) },
            label = { Text("方程组") },
        )
    }
}

@Composable
private fun ShortcutBar(onKey: (KeySpec) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(Keypads.shortcutBar) { spec ->
            AssistChip(
                onClick = { onKey(spec) },
                label = { Text(spec.label, fontSize = 13.sp) },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun DisplayPane(
    state: CalcUiState,
    settings: AppSettings,
    vm: CalculatorViewModel,
    modifier: Modifier = Modifier,
) {
    val compact = LocalConfiguration.current.screenHeightDp < 480
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        val errorText = state.error
        val approxText = state.approx
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.mode == CalcMode.SYSTEM) {
                EquationLine(
                    latex = state.input,
                    active = state.activeLine == 0,
                    placeholder = "方程 ①，例如 2x+3y=8",
                    onClick = { vm.focusLine(0) },
                )
                Spacer(Modifier.height(4.dp))
                EquationLine(
                    latex = state.input2,
                    active = state.activeLine == 1,
                    placeholder = "方程 ②，例如 x−y=1",
                    onClick = { vm.focusLine(1) },
                )
            } else {
                MathFormulaView(
                    latex = state.input,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 56.dp else 72.dp),
                    fontSizeSp = when {
                        compact -> 24f
                        state.input.length > 40 -> 24f
                        else -> 30f
                    },
                    textColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            when {
                errorText != null -> {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                    )
                }

                state.result.isBlank() -> {
                    Text(
                        text = when (state.mode) {
                            CalcMode.CALC -> "输入算式后按 ="
                            CalcMode.QUADRATIC -> "输入 ax²+bx+c=0 后按 = 求解"
                            CalcMode.SYSTEM -> "填写两条方程后按 = 求解"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    Text(
                        text = state.result,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = if (compact) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (approxText != null && settings.showDecimalApprox) {
                        Text(
                            text = approxText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.steps.isNotEmpty()) {
                        TextButton(
                            onClick = vm::toggleSteps,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text("查看求解步骤", fontSize = 13.sp)
                        }
                    }
                }
            }

            if (state.busy) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }
        }
    }
}

@Composable
private fun EquationLine(
    latex: String,
    active: Boolean,
    placeholder: String,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            color = if (active) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = if (active) {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        ) {
            if (latex.isBlank()) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                MathFormulaView(
                    latex = latex,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    fontSizeSp = 22f,
                    textColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // WebView 会吞掉点击，这里盖一层透明热区用来切换当前编辑行
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
        )
    }
}

@Composable
private fun StepsContent(state: CalcUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("求解步骤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        state.steps.forEachIndexed { index, step ->
            Row {
                Text(
                    text = "${index + 1}.",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(22.dp),
                )
                Column {
                    Text(step.title, style = MaterialTheme.typography.labelLarge)
                    Text(
                        step.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "所有中间结果均为精确有理数运算，分数不会被四舍五入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
