package com.farmcare.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Forest = Color(0xFF1B4D3E)
val Success = Color(0xFF2E7D4F)
val Warning = Color(0xFFB26A00)
val Danger = Color(0xFFB3261E)

private val Scheme = lightColorScheme(
    primary = Forest, onPrimary = Color.White, primaryContainer = Color(0xFFD9E9E0), onPrimaryContainer = Color(0xFF0E2D24),
    secondary = Color(0xFF52625A), background = Color(0xFFF5F7F4), onBackground = Color(0xFF1E2421),
    surface = Color.White, onSurface = Color(0xFF1E2421), surfaceVariant = Color(0xFFECEFEB), onSurfaceVariant = Color(0xFF4A524D),
    outline = Color(0xFFCFD5CF), outlineVariant = Color(0xFFE2E6E1), error = Danger)

@Composable
fun FarmCareTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = Scheme,
    shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
    content = content)
