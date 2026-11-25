package fr.btssio.voxresto.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.btssio.voxresto.R

/*
 * Thème repris de la maquette Figma « Commande vocale pizzeria » (Il Forno) :
 * fond crème, cartes blanches à fine bordure, rouge four à pizza, vert « prêt », encre presque noire.
 * Le mode sombre n'existe pas dans la maquette : c'est une déclinaison chaude des mêmes teintes.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFFD8472C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF9DDD7),
    onPrimaryContainer = Color(0xFF8E2A18),
    secondary = Color(0xFF4B8A5B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EFE7),
    onSecondaryContainer = Color(0xFF40774D),
    tertiary = Color(0xFFAA6A11),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBECD0),
    onTertiaryContainer = Color(0xFF7A4A0A),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF252823),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF252823),
    surfaceVariant = Color(0xFFF1EEE7),
    onSurfaceVariant = Color(0xFF777A71),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF7),
    outline = Color(0xFFBDB9AE),
    outlineVariant = Color(0xFFDEDBD2),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFADBD8),
    onErrorContainer = Color(0xFF7A1410),
    inverseSurface = Color(0xFF252823),
    inverseOnSurface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2705A),
    onPrimary = Color(0xFF3A0D04),
    primaryContainer = Color(0xFF5C1F12),
    onPrimaryContainer = Color(0xFFFADDD6),
    secondary = Color(0xFF7DBE8D),
    onSecondary = Color(0xFF0F2A16),
    secondaryContainer = Color(0xFF24402B),
    onSecondaryContainer = Color(0xFFD3EBD9),
    tertiary = Color(0xFFE3AE5E),
    onTertiary = Color(0xFF3B2504),
    tertiaryContainer = Color(0xFF4A3210),
    onTertiaryContainer = Color(0xFFFBECD0),
    background = Color(0xFF1B1D19),
    onBackground = Color(0xFFEDEBE4),
    surface = Color(0xFF1B1D19),
    onSurface = Color(0xFFEDEBE4),
    surfaceVariant = Color(0xFF2A2D27),
    onSurfaceVariant = Color(0xFFB3B6AC),
    surfaceContainerLowest = Color(0xFF242722),
    surfaceContainerLow = Color(0xFF20231E),
    outline = Color(0xFF6F7268),
    outlineVariant = Color(0xFF3B3E37),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE8E4DA),
    inverseOnSurface = Color(0xFF252823),
)

// Polices de la maquette : Manrope pour les titres, DM Sans pour le texte (fichiers dans res/font, licence OFL).
private val Display = FontFamily(
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val Body = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

private val BaseTypography = Typography()

private val VoxTypography = Typography(
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = Body),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = Body),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = Body),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
)

private val VoxShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun VoxRestoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = VoxTypography,
        shapes = VoxShapes,
        content = content,
    )
}
