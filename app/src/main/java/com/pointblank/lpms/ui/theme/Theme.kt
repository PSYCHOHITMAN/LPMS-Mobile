package com.pointblank.lpms.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Colours taken from the LPMS web portal stylesheet
val Cream = Color(0xFFFCF7F2)
val Sand = Color(0xFFFFFBF7)
val Line = Color(0xFFF0E4D8)
val Track = Color(0xFFF0E8E0)
val Brand = Color(0xFFD94F4A)
val BrandDark = Color(0xFFB13A36)
val BrandTint = Color(0xFFFCE9E7)
val Teal = Color(0xFF2A9D8F)
val TealDark = Color(0xFF1B6B62)
val TealTint = Color(0xFFE6F4F0)
val Amber = Color(0xFFF4A261)
val Purple = Color(0xFF7B4A8C)
val PurpleTint = Color(0xFFF0E8F5)
val Blue = Color(0xFF1A6BB0)
val BlueTint = Color(0xFFE3F0FF)
val Ink = Color(0xFF2D2A24)
val Muted = Color(0xFF6B5F51)

private val Colors = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = BrandTint, onPrimaryContainer = BrandDark,
    secondary = Teal, onSecondary = Color.White,
    tertiary = Amber,
    background = Cream, onBackground = Ink,
    surface = Sand, onSurface = Ink,
    surfaceVariant = Track, onSurfaceVariant = Muted,
    outline = Line, error = BrandDark, errorContainer = BrandTint
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun LpmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = AppShapes, content = content)
}
