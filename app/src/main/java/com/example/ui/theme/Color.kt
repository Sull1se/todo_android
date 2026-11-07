package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 品牌色调：由品牌蓝 #0B57D0 展开的固定色阶（不采用动态配色，见 UI 与 UX 规范 3.1 与决定 2）。
 * 浅色与 OLED 深色两套方案在 Theme.kt 中完整定义 M3 色彩角色，避免回退到 M3 基线紫色。
 * 桌面小卡片的 widget_colors.xml 与 WidgetColors 使用与此一致的色值。
 */

// 浅色方案
internal val LightPrimary = Color(0xFF0B57D0)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFD3E3FD)
internal val LightOnPrimaryContainer = Color(0xFF041E49)
internal val LightInversePrimary = Color(0xFFA8C7FA)
internal val LightSecondary = Color(0xFF575E71)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFDCE3F4)
internal val LightOnSecondaryContainer = Color(0xFF141B2C)
internal val LightTertiary = Color(0xFF146C2E)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFC4EED0)
internal val LightOnTertiaryContainer = Color(0xFF072711)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)
internal val LightOnErrorContainer = Color(0xFF410E0B)
internal val LightBackground = Color(0xFFF0F4F9)
internal val LightOnBackground = Color(0xFF1B1B1F)
internal val LightSurface = Color(0xFFF0F4F9)
internal val LightOnSurface = Color(0xFF1B1B1F)
internal val LightSurfaceVariant = Color(0xFFE1E3E1)
internal val LightOnSurfaceVariant = Color(0xFF444746)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF7F9FC)
internal val LightSurfaceContainer = Color(0xFFEEF2F8)
internal val LightSurfaceContainerHigh = Color(0xFFE8EDF5)
internal val LightSurfaceContainerHighest = Color(0xFFE1E7EF)
internal val LightSurfaceDim = Color(0xFFD8DEE6)
internal val LightSurfaceBright = Color(0xFFF7F9FC)
internal val LightOutline = Color(0xFF747775)
internal val LightOutlineVariant = Color(0xFFC4C7C5)
internal val LightInverseSurface = Color(0xFF303033)
internal val LightInverseOnSurface = Color(0xFFF2F2F5)

// OLED 深色方案：background / surface 为纯黑，容器层级用深灰色阶
internal val DarkPrimary = Color(0xFF8AB4F8)
internal val DarkOnPrimary = Color(0xFF002D6C)
internal val DarkPrimaryContainer = Color(0xFF042964)
internal val DarkOnPrimaryContainer = Color(0xFFD3E3FD)
internal val DarkInversePrimary = Color(0xFF0B57D0)
internal val DarkSecondary = Color(0xFFC0C7D9)
internal val DarkOnSecondary = Color(0xFF293041)
internal val DarkSecondaryContainer = Color(0xFF353C4D)
internal val DarkOnSecondaryContainer = Color(0xFFDCE3F4)
internal val DarkTertiary = Color(0xFF6DD58C)
internal val DarkOnTertiary = Color(0xFF0A3818)
internal val DarkTertiaryContainer = Color(0xFF0F5223)
internal val DarkOnTertiaryContainer = Color(0xFFC4EED0)
internal val DarkError = Color(0xFFF2B8B5)
internal val DarkOnError = Color(0xFF601410)
internal val DarkErrorContainer = Color(0xFF8C1D18)
internal val DarkOnErrorContainer = Color(0xFFF9DEDC)
internal val DarkBackground = Color(0xFF000000)
internal val DarkOnBackground = Color(0xFFE2E2E6)
internal val DarkSurface = Color(0xFF000000)
internal val DarkOnSurface = Color(0xFFE2E2E6)
internal val DarkSurfaceVariant = Color(0xFF2E2E30)
internal val DarkOnSurfaceVariant = Color(0xFFC4C7C5)
internal val DarkSurfaceContainerLowest = Color(0xFF000000)
internal val DarkSurfaceContainerLow = Color(0xFF161616)
internal val DarkSurfaceContainer = Color(0xFF1C1C1E)
internal val DarkSurfaceContainerHigh = Color(0xFF242426)
internal val DarkSurfaceContainerHighest = Color(0xFF2E2E30)
internal val DarkSurfaceDim = Color(0xFF000000)
internal val DarkSurfaceBright = Color(0xFF2E2E30)
internal val DarkOutline = Color(0xFF8E918F)
internal val DarkOutlineVariant = Color(0xFF444746)
internal val DarkInverseSurface = Color(0xFFE3E2E6)
internal val DarkInverseOnSurface = Color(0xFF1A1B1E)

/** 清单可选主题色（新建与设置共用），仅作色条与圆点标识，不承载文字。 */
val ListThemePalette: List<Long> = listOf(
    0xFFE53935L, 0xFF1E88E5L, 0xFF43A047L, 0xFFFFB300L, 0xFF8E24AAL, 0xFF00ACC1L
)

/** 清单不存在时的色条兜底色。 */
const val ListColorFallback: Long = 0xFFD3E3FDL
