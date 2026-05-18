package com.retrosprite.app.ui.theme

import androidx.compose.ui.graphics.Color

// RetroSprite palette: handheld-CRT meets late-90s cartridge.
// Anchor trio (per design brief):
//   深紫 #2D1B69  · 荧光绿 #22D680  · 琥珀 #F8B500
// We treat 深紫 as the canvas, 荧光绿 as live/active state and primary action,
// and 琥珀 as warm warning / secondary highlight.

// ---- Anchors ----
val RetroDeepPurple = Color(0xFF2D1B69)
val RetroNeonGreen = Color(0xFF22D680)
val RetroAmber = Color(0xFFF8B500)

// ---- Neutrals (cool purple-tinted) ----
val RetroInk = Color(0xFF0B0620)        // near-black canvas
val RetroSurface = Color(0xFF15103A)    // raised surfaces (cards)
val RetroSurfaceHigh = Color(0xFF1F1850) // dialogs / sheets
val RetroOutline = Color(0xFF564A8C)
val RetroOutlineVariant = Color(0xFF36306B)

// ---- Primary (neon green) ----
val RetroPrimary = RetroNeonGreen
val RetroOnPrimary = Color(0xFF00210F)
val RetroPrimaryContainer = Color(0xFF005A33)
val RetroOnPrimaryContainer = Color(0xFFA8FFCD)

// ---- Secondary (amber) ----
val RetroSecondary = RetroAmber
val RetroOnSecondary = Color(0xFF2A1A00)
val RetroSecondaryContainer = Color(0xFF5C3F00)
val RetroOnSecondaryContainer = Color(0xFFFFDFA8)

// ---- Tertiary (lavender to bridge purple/green) ----
val RetroTertiary = Color(0xFFB48EFF)
val RetroOnTertiary = Color(0xFF24114C)
val RetroTertiaryContainer = Color(0xFF3A2273)
val RetroOnTertiaryContainer = Color(0xFFE6D7FF)

// ---- Error (warm red, kept legible on deep purple) ----
val RetroError = Color(0xFFFF6B6B)
val RetroOnError = Color(0xFF3B0008)
val RetroErrorContainer = Color(0xFF7A0014)
val RetroOnErrorContainer = Color(0xFFFFDAD6)

// ---- Foreground text ----
val RetroOnSurface = Color(0xFFEDE6FF)
val RetroOnSurfaceVariant = Color(0xFFB9B0DA)

// ---- Semantic status (used by StatusIndicator) ----
val StatusRunning = RetroNeonGreen
val StatusStarting = RetroAmber
val StatusStopped = Color(0xFF8A8FA8)
val StatusError = Color(0xFFFF6B6B)
