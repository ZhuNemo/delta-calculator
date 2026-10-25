package com.nemo.deltacalc.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nemo.deltacalc.data.AppSettings
import com.nemo.deltacalc.data.KeypadStyle
import com.nemo.deltacalc.data.SettingsRepository
import com.nemo.deltacalc.data.ThemeMode
import com.nemo.deltacalc.ui.theme.PresetSeeds
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    repository: SettingsRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var radius by remember(settings.cornerRadius) { mutableFloatStateOf(settings.cornerRadius) }
    var fontScale by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 19.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            SectionTitle("外观")

            Text(
                "主题模式",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { scope.launch { repository.setThemeMode(mode) } },
                        label = { Text(mode.label()) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            if (dynamicSupported) {
                SwitchRow(
                    title = "动态取色",
                    subtitle = "跟随系统壁纸取色（Material You），会覆盖下方主色",
                    checked = settings.dynamicColor,
                    onCheckedChange = { scope.launch { repository.setDynamicColor(it) } },
                )
            }

            Text(
                "主色",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(PresetSeeds) { seed ->
                    val selected = settings.seedColor == seed
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(seed), CircleShape)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { scope.launch { repository.setSeedColor(seed) } },
                    )
                }
            }

            SliderRow(
                title = "圆角",
                valueText = "${radius.toInt()} dp",
                value = radius,
                range = 0f..32f,
                onValueChange = { radius = it },
                onValueChangeFinished = { scope.launch { repository.setCornerRadius(radius) } },
            )

            SliderRow(
                title = "字体大小",
                valueText = "${(fontScale * 100).toInt()}%",
                value = fontScale,
                range = 0.85f..1.3f,
                onValueChange = { fontScale = it },
                onValueChangeFinished = { scope.launch { repository.setFontScale(fontScale) } },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SectionTitle("键盘")

            Text(
                "按键样式",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeypadStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.keypadStyle == style,
                        onClick = { scope.launch { repository.setKeypadStyle(style) } },
                        label = { Text(style.label()) },
                    )
                }
            }

            SwitchRow(
                title = "按键震动",
                subtitle = "每次按键给一次轻触反馈",
                checked = settings.haptic,
                onCheckedChange = { scope.launch { repository.setHaptic(it) } },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SectionTitle("结果")

            SwitchRow(
                title = "显示小数近似",
                subtitle = "分数结果下方额外显示 ≈ 小数，便于校核",
                checked = settings.showDecimalApprox,
                onCheckedChange = { scope.launch { repository.setShowDecimalApprox(it) } },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SectionTitle("关于")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Delta 草稿纸 0.1.0 · MVP", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "完全离线：没有 INTERNET 权限，没有登录、广告和统计 SDK。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "公式渲染 MathLive（随 APK 打包）· 历史记录 Room（仅本机）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "计算内核：精确分数 + 一元二次 / 二元一次方程求解",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun KeypadStyle.label(): String = when (this) {
    KeypadStyle.FILLED -> "填充"
    KeypadStyle.OUTLINED -> "描边"
    KeypadStyle.FLAT -> "扁平"
}
