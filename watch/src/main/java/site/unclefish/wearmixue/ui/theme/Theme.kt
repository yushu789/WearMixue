package site.unclefish.wearmixue.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MixuePrimary,
    onPrimary = MixueOnPrimary,
    primaryContainer = MixuePrimaryContainer,
    onPrimaryContainer = MixueOnPrimaryContainer,
    inversePrimary = MixueInversePrimary,
    secondary = MixueSecondary,
    onSecondary = MixueOnSecondary,
    secondaryContainer = MixueSecondaryContainer,
    onSecondaryContainer = MixueOnSecondaryContainer,
    tertiary = MixueTertiary,
    onTertiary = MixueOnTertiary,
    tertiaryContainer = MixueTertiaryContainer,
    onTertiaryContainer = MixueOnTertiaryContainer,
    background = MixueBackground,
    onBackground = MixueOnBackground,
    surface = MixueSurface,
    onSurface = MixueOnSurface,
    surfaceVariant = MixueSurfaceVariant,
    onSurfaceVariant = MixueOnSurfaceVariant,
    surfaceTint = MixueSurfaceTint,
    inverseSurface = MixueInverseSurface,
    inverseOnSurface = MixueInverseOnSurface,
    error = MixueError,
    onError = MixueOnError,
    errorContainer = MixueErrorContainer,
    onErrorContainer = MixueOnErrorContainer,
    outline = MixueOutline,
    outlineVariant = MixueOutlineVariant,
    scrim = MixueScrim,
    surfaceContainerLowest = MixueSurfaceContainerLowest,
    surfaceContainerLow = MixueSurfaceContainerLow,
    surfaceContainer = MixueSurfaceContainer,
    surfaceContainerHigh = MixueSurfaceContainerHigh,
    surfaceContainerHighest = MixueSurfaceContainerHighest,
    surfaceBright = MixueSurfaceBright,
    surfaceDim = MixueSurfaceDim
)

@Composable
fun WearMixueTheme(
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Always force dark mode regardless of the system theme.
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
