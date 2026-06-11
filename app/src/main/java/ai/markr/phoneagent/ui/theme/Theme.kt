package ai.markr.phoneagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    secondary = Amber60,
    onSecondary = Indigo20,
    background = NeutralBg,
    onBackground = NeutralOnDark,
    surface = NeutralSurface,
    onSurface = NeutralOnDark,
    onSurfaceVariant = MutedDark,
    error = Danger,
)

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = NeutralSurfaceLight,
    secondary = Amber40,
    onSecondary = NeutralSurfaceLight,
    background = NeutralSurfaceLight,
    onBackground = NeutralOnLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnLight,
    error = Danger,
)

@Composable
fun PhoneAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
