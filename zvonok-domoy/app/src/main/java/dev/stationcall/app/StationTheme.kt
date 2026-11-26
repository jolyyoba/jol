package dev.stationcall.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DayColors = lightColorScheme(
    primary = Color(0xFF485D00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9FA68),
    onPrimaryContainer = Color(0xFF222D00),
    inversePrimary = Color(0xFFD0EF71),
    secondary = Color(0xFF604880),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF28163D),
    tertiary = Color(0xFF6C5278),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4DAFF),
    onTertiaryContainer = Color(0xFF291335),
    background = Color(0xFFFCF9FF),
    onBackground = Color(0xFF201B25),
    surface = Color(0xFFFCF9FF),
    onSurface = Color(0xFF201B25),
    surfaceVariant = Color(0xFFE8DFEC),
    onSurfaceVariant = Color(0xFF514958),
    inverseSurface = Color(0xFF352F3A),
    inverseOnSurface = Color(0xFFF7EFFA),
    outline = Color(0xFF756C7D),
    outlineVariant = Color(0xFFCBBFD1),
    surfaceBright = Color(0xFFFCF9FF),
    surfaceDim = Color(0xFFE0D8E5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F0FA),
    surfaceContainer = Color(0xFFF1EAF5),
    surfaceContainerHigh = Color(0xFFECE4F0),
    surfaceContainerHighest = Color(0xFFE6DDEA),
)

private val NightColors = darkColorScheme(
    primary = Color(0xFFD0EF71),
    onPrimary = Color(0xFF283500),
    primaryContainer = Color(0xFFC9EC60),
    onPrimaryContainer = Color(0xFF1D2A00),
    inversePrimary = Color(0xFF485D00),
    secondary = Color(0xFFCFB8F3),
    onSecondary = Color(0xFF361F52),
    secondaryContainer = Color(0xFF3C2E52),
    onSecondaryContainer = Color(0xFFECDDFF),
    tertiary = Color(0xFFD9B9E8),
    onTertiary = Color(0xFF3E284A),
    tertiaryContainer = Color(0xFF553D60),
    onTertiaryContainer = Color(0xFFF4DAFF),
    background = Color(0xFF16121A),
    onBackground = Color(0xFFEAE1EE),
    surface = Color(0xFF16121A),
    onSurface = Color(0xFFEAE1EE),
    surfaceVariant = Color(0xFF4C4253),
    onSurfaceVariant = Color(0xFFCFC2D3),
    inverseSurface = Color(0xFFEAE1EE),
    inverseOnSurface = Color(0xFF352F3A),
    outline = Color(0xFF9A8F9F),
    outlineVariant = Color(0xFF4F4555),
    surfaceBright = Color(0xFF3C3541),
    surfaceDim = Color(0xFF16121A),
    surfaceContainerLowest = Color(0xFF110D15),
    surfaceContainerLow = Color(0xFF201B24),
    surfaceContainer = Color(0xFF25202A),
    surfaceContainerHigh = Color(0xFF302A35),
    surfaceContainerHighest = Color(0xFF3C3541),
)

private val StationShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val DefaultTypography = Typography()
private val StationTypography = DefaultTypography.copy(
    displayLarge = DefaultTypography.displayLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-2.2).sp,
    ),
    displayMedium = DefaultTypography.displayMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.7).sp,
    ),
    displaySmall = DefaultTypography.displaySmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.1).sp,
    ),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = DefaultTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun StationTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NightColors
        else -> DayColors
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = StationShapes,
        typography = StationTypography,
        content = content,
    )
}
