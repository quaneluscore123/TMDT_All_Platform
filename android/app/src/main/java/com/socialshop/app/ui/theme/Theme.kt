package com.socialshop.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

val Orange500 = Color(0xFFF97316)
val Orange600 = Color(0xFFEA580C)
val Stone900 = Color(0xFF1C1917)
val Stone50 = Color(0xFFFAFAF9)
val SaleRed = Color(0xFFDC2626)
val StarYellow = Color(0xFFF59E0B)
val SuccessGreen = Color(0xFF059669)

private val LightColors = lightColorScheme(
    primary = Stone900,
    onPrimary = Color.White,
    secondary = Orange500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF9A3412),
    tertiary = Orange600,
    background = Stone50,
    surface = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFCFB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF5F5F4),
    surfaceContainerHighest = Color(0xFFE7E5E4),
    surfaceVariant = Color(0xFFF5F5F4),
    onSurfaceVariant = Color(0xFF57534E),
    outline = Color(0xFFD6D3D1),
    outlineVariant = Color(0xFFE7E5E4),
    error = SaleRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F4),
    onPrimary = Stone900,
    secondary = Orange500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF7C2D12),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = Orange500,
    background = Color(0xFF0C0A09),
    surface = Color(0xFF1C1917),
    surfaceContainerLowest = Color(0xFF0C0A09),
    surfaceContainerLow = Color(0xFF161412),
    surfaceContainer = Color(0xFF1C1917),
    surfaceContainerHigh = Color(0xFF292524),
    surfaceContainerHighest = Color(0xFF44403C),
    surfaceVariant = Color(0xFF292524),
    onSurfaceVariant = Color(0xFFA8A29E),
    outline = Color(0xFF57534E),
    outlineVariant = Color(0xFF44403C),
    error = Color(0xFFF87171),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun SocialShopTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
