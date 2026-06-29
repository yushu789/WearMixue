package site.unclefish.wearmixue.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material You dark color scheme derived from the Mixue brand red (#E80819).
 *
 * Every role is produced by the HCT/CAM16 tonal-palette pipeline (Material
 * "Vibrant" variant), so surfaces, containers and outlines all stay in the
 * same warm-red family rather than falling back to Material's default purple
 * baseline — which is what made the old hand-picked scheme look off.
 *
 * Regenerate with:
 *   material_color_utilities.theme_from_argb_color('#e80819', variant=VIBRANT)
 */

// --- Primary ---
val MixuePrimary = Color(0xFFFFB4AB)
val MixueOnPrimary = Color(0xFF520003)
val MixuePrimaryContainer = Color(0xFFDB0015) // ≈ brand red
val MixueOnPrimaryContainer = Color(0xFFFFFFFF) // white-on-red, the Mixue combo
val MixueInversePrimary = Color(0xFFAD000E)

// --- Secondary ---
val MixueSecondary = Color(0xFFF4B9A0)
val MixueOnSecondary = Color(0xFF3C1B0A)
val MixueSecondaryContainer = Color(0xFF91614C)
val MixueOnSecondaryContainer = Color(0xFFFFFFFF)

// --- Tertiary ---
val MixueTertiary = Color(0xFFFAB982)
val MixueOnTertiary = Color(0xFF3B1D00)
val MixueTertiaryContainer = Color(0xFF956132)
val MixueOnTertiaryContainer = Color(0xFFFFFFFF)

// --- Surfaces ---
val MixueBackground = Color(0xFF1E100E)
val MixueOnBackground = Color(0xFFFFE9E6)
val MixueSurface = Color(0xFF1E100E)
val MixueOnSurface = Color(0xFFFFE9E6)
val MixueSurfaceVariant = Color(0xFF58413E)
val MixueOnSurfaceVariant = Color(0xFFDFBFBB)
val MixueSurfaceTint = Color(0xFFFFB4AB)
val MixueInverseSurface = Color(0xFFF9DCD8)
val MixueInverseOnSurface = Color(0xFF3D2C2A)

// --- Surface containers ---
val MixueSurfaceContainerLowest = Color(0xFF140806)
val MixueSurfaceContainerLow = Color(0xFF281917)
val MixueSurfaceContainer = Color(0xFF30201E)
val MixueSurfaceContainerHigh = Color(0xFF3B2A28)
val MixueSurfaceContainerHighest = Color(0xFF473533)
val MixueSurfaceBright = Color(0xFF4D3A38)
val MixueSurfaceDim = Color(0xFF1E100E)

// --- Error (Material 3 canonical dark-red tones, kept distinct from primary) ---
val MixueError = Color(0xFFF2B8B5)
val MixueOnError = Color(0xFF601410)
val MixueErrorContainer = Color(0xFF8C1D18)
val MixueOnErrorContainer = Color(0xFFF9DEDC)

// --- Outlines & misc ---
val MixueOutline = Color(0xFFB29491)
val MixueOutlineVariant = Color(0xFF816663)
val MixueScrim = Color(0xFF000000)
