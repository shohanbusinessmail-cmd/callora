package com.shohankhan.callora.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohankhan.callora.core.model.AppThemeMode

object CalloraColors {
    val MidnightNavyDeep = Color(0xFF071226)
    val MidnightNavySurface = Color(0xFF0E1D38)
    val MidnightNavyCard = Color(0xFF132647)
    val NavyPrimaryLight = Color(0xFF0B2545)
    val ElectricTealDark = Color(0xFF22E1D3)
    val ElectricTealLight = Color(0xFF007971)
    val CyanAccent = Color(0xFF00B4D8)

    val CleanLightBackground = Color(0xFFF5F8FC)
    val CleanLightSurface = Color(0xFFFFFFFF)
    val CleanLightSurfaceVariant = Color(0xFFE8EFF7)

    val StatusVerifiedBgDark = Color(0xFF0E3B36)
    val StatusVerifiedFgDark = Color(0xFF6FF7E8)
    val StatusVerifiedBgLight = Color(0xFFDDF7F3)
    val StatusVerifiedFgLight = Color(0xFF005952)

    val StatusUnverifiedBgDark = Color(0xFF3A2E12)
    val StatusUnverifiedFgDark = Color(0xFFFFD97D)
    val StatusUnverifiedBgLight = Color(0xFFFFF3D6)
    val StatusUnverifiedFgLight = Color(0xFF6B4C00)

    val StatusErrorBgDark = Color(0xFF421820)
    val StatusErrorFgDark = Color(0xFFFFB3BC)
    val StatusErrorBgLight = Color(0xFFFDE7EA)
    val StatusErrorFgLight = Color(0xFF8C1D2C)

    val HeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF091B38),
            Color(0xFF0C3554),
            Color(0xFF0A4F5C)
        )
    )
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = CalloraColors.NavyPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E4F7),
    onPrimaryContainer = Color(0xFF071A33),
    secondary = CalloraColors.ElectricTealLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF5F1),
    onSecondaryContainer = Color(0xFF003733),
    tertiary = Color(0xFF0F4C81),
    onTertiary = Color.White,
    background = CalloraColors.CleanLightBackground,
    onBackground = Color(0xFF0D1B2A),
    surface = CalloraColors.CleanLightSurface,
    onSurface = Color(0xFF0D1B2A),
    surfaceVariant = CalloraColors.CleanLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF3B4D63),
    outline = Color(0xFF718399),
    error = Color(0xFFB00020),
    onError = Color.White
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = CalloraColors.ElectricTealDark,
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFF9CF7EE),
    secondary = Color(0xFF8ECAE6),
    onSecondary = Color(0xFF06293D),
    secondaryContainer = CalloraColors.MidnightNavyCard,
    onSecondaryContainer = Color(0xFFD7E9F7),
    tertiary = CalloraColors.CyanAccent,
    onTertiary = Color(0xFF003543),
    background = CalloraColors.MidnightNavyDeep,
    onBackground = Color(0xFFEAF1FA),
    surface = CalloraColors.MidnightNavySurface,
    onSurface = Color(0xFFEAF1FA),
    surfaceVariant = CalloraColors.MidnightNavyCard,
    onSurfaceVariant = Color(0xFFB8C7DC),
    outline = Color(0xFF5C7290),
    error = Color(0xFFFF8A98),
    onError = Color(0xFF49000E)
)

object CalloraSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

val CalloraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val BengaliSafePlatformStyle = PlatformTextStyle(includeFontPadding = true)

/**
 * Generous line heights ensure Bengali vowel signs and conjuncts (া ি ী ু ূ ে ৈ ো ৌ ঁ)
 * never clip even under large system font scaling.
 */
val CalloraTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 38.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 28.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        platformStyle = BengaliSafePlatformStyle
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        platformStyle = BengaliSafePlatformStyle
    )
)

@Composable
fun CalloraTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CalloraTypography,
        shapes = CalloraShapes,
        content = content
    )
}
