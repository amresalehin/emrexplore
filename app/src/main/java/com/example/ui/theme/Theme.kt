package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FossifyPrimaryDark,
    onPrimary = FossifyOnPrimaryDark,
    primaryContainer = FossifyPrimaryContainerDark,
    onPrimaryContainer = FossifyOnPrimaryContainerDark,
    secondary = FossifySecondaryDark,
    onSecondary = FossifyOnSecondaryDark,
    secondaryContainer = FossifySecondaryContainerDark,
    onSecondaryContainer = FossifyOnSecondaryContainerDark,
    tertiary = FossifyTertiaryDark,
    onTertiary = FossifyOnTertiaryDark,
    background = FossifyBackgroundDark,
    onBackground = FossifyOnBackgroundDark,
    surface = FossifySurfaceDark,
    onSurface = FossifyOnSurfaceDark,
    surfaceVariant = FossifySurfaceVariantDark,
    onSurfaceVariant = FossifyOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = FossifyPrimaryLight,
    onPrimary = FossifyOnPrimaryLight,
    primaryContainer = FossifyPrimaryContainerLight,
    onPrimaryContainer = FossifyOnPrimaryContainerLight,
    secondary = FossifySecondaryLight,
    onSecondary = FossifyOnSecondaryLight,
    secondaryContainer = FossifySecondaryContainerLight,
    onSecondaryContainer = FossifyOnSecondaryContainerLight,
    tertiary = FossifyTertiaryLight,
    onTertiary = FossifyOnTertiaryLight,
    background = FossifyBackgroundLight,
    onBackground = FossifyOnBackgroundLight,
    surface = FossifySurfaceLight,
    onSurface = FossifyOnSurfaceLight,
    surfaceVariant = FossifySurfaceVariantLight,
    onSurfaceVariant = FossifyOnSurfaceVariantLight
)

@Composable
fun FossifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
