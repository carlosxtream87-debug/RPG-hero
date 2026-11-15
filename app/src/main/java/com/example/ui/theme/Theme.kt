package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EldoriaDarkColorScheme = darkColorScheme(
  primary = HyruleEmerald,
  onPrimary = Color(0xFF022C22),
  primaryContainer = Color(0xFF064E3B),
  onPrimaryContainer = HyruleEmeraldSoft,
  secondary = ArcaneAzure,
  onSecondary = Color(0xFF082F49),
  secondaryContainer = ForestCard,
  onSecondaryContainer = ParchmentLight,
  tertiary = TreasureGold,
  onTertiary = Color(0xFF271502),
  tertiaryContainer = TreasureGoldDark,
  onTertiaryContainer = Color(0xFFFEF3C7),
  background = ForestObsidian,
  onBackground = ParchmentLight,
  surface = ForestSurface,
  onSurface = ParchmentLight,
  surfaceVariant = ForestCard,
  onSurfaceVariant = ParchmentMuted,
  outline = ForestBorder,
  error = CrimsonHeart,
  onError = Color.White,
  errorContainer = CrimsonHeartDark,
  onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
fun EldoriaTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = EldoriaDarkColorScheme,
    typography = EldoriaTypography,
    content = content,
  )
}
