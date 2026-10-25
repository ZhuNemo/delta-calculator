package com.nemo.deltacalc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nemo.deltacalc.data.AppSettings
import com.nemo.deltacalc.data.ThemeMode

/**
 * 主题核心：种子色 → 整套 ColorScheme（色相派生，不依赖壁纸）。
 * 打开「动态取色」后改用系统 Material You 壁纸取色（Android 12+）。
 */
@Composable
fun DeltaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = remember(settings.seedColor, settings.dynamicColor, dark) {
        if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            seedScheme(settings.seedColor, dark)
        }
    }
    val radius = settings.cornerRadius
    val shapes = remember(radius) {
        Shapes(
            extraSmall = RoundedCornerShape(radius * 0.35f),
            small = RoundedCornerShape(radius * 0.55f),
            medium = RoundedCornerShape(radius * 0.8f),
            large = RoundedCornerShape(radius.dp),
            extraLarge = RoundedCornerShape((radius * 1.4f).dp),
        )
    }
    val density = LocalDensity.current
    val scaledDensity = remember(density, settings.fontScale) {
        Density(density.density, density.fontScale * settings.fontScale)
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
    }
}

/** 由单个种子色派生明/暗两套配色。 */
fun seedScheme(seedArgb: Long, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    android.graphics.ColorUtils.colorToHSL(seedArgb.toInt(), hsl)
    val h = hsl[0]
    val s = if (hsl[1] < 0.05f) 0.05f else hsl[1]

    fun c(hue: Float, sat: Float, light: Float): Color {
        val hh = ((hue % 360f) + 360f) % 360f
        return Color.hsl(hh, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f))
    }

    return if (dark) {
        darkColorScheme(
            primary = c(h, s * 0.68f, 0.78f),
            onPrimary = c(h, s * 0.60f, 0.16f),
            primaryContainer = c(h, s * 0.52f, 0.30f),
            onPrimaryContainer = c(h, s * 0.75f, 0.92f),
            secondary = c(h + 28f, s * 0.42f, 0.76f),
            onSecondary = c(h + 28f, s * 0.45f, 0.18f),
            secondaryContainer = c(h + 28f, s * 0.38f, 0.28f),
            onSecondaryContainer = c(h + 28f, s * 0.60f, 0.91f),
            tertiary = c(h - 40f, s * 0.42f, 0.74f),
            background = c(h, 0.10f, 0.055f),
            onBackground = c(h, 0.06f, 0.93f),
            surface = c(h, 0.10f, 0.065f),
            onSurface = c(h, 0.06f, 0.93f),
            surfaceVariant = c(h, 0.11f, 0.16f),
            onSurfaceVariant = c(h, 0.08f, 0.72f),
            surfaceContainer = c(h, 0.10f, 0.11f),
            surfaceContainerHigh = c(h, 0.10f, 0.15f),
            outline = c(h, 0.08f, 0.42f),
            outlineVariant = c(h, 0.08f, 0.25f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = c(h, s * 0.80f, 0.40f),
            onPrimary = Color.White,
            primaryContainer = c(h, s * 0.90f, 0.90f),
            onPrimaryContainer = c(h, s * 0.80f, 0.16f),
            secondary = c(h + 28f, s * 0.40f, 0.44f),
            onSecondary = Color.White,
            secondaryContainer = c(h + 28f, s * 0.55f, 0.91f),
            onSecondaryContainer = c(h + 28f, s * 0.60f, 0.18f),
            tertiary = c(h - 40f, s * 0.40f, 0.44f),
            background = c(h, 0.30f, 0.985f),
            onBackground = c(h, 0.18f, 0.11f),
            surface = c(h, 0.30f, 0.985f),
            onSurface = c(h, 0.18f, 0.11f),
            surfaceVariant = c(h, 0.26f, 0.93f),
            onSurfaceVariant = c(h, 0.12f, 0.40f),
            surfaceContainer = c(h, 0.28f, 0.96f),
            surfaceContainerHigh = c(h, 0.26f, 0.93f),
            outline = c(h, 0.10f, 0.60f),
            outlineVariant = c(h, 0.12f, 0.82f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}

/** 设置页的预设色板。 */
val PresetSeeds: List<Long> = listOf(
    0xFF6750A4, // Material 紫
    0xFF2F6FED, // 蓝
    0xFF0F9D58, // 绿
    0xFFE8710A, // 橙
    0xFFD93025, // 红
    0xFF00838F, // 青
    0xFF7B4DFF, // 靛紫
    0xFFAD1457, // 玫红
    0xFF37474F, // 石墨
)
