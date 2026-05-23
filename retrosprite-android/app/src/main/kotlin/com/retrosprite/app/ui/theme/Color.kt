package com.retrosprite.app.ui.theme

import androidx.compose.ui.graphics.Color

// RetroSprite palette: low-interruption neon assistant HUD.
// The in-game overlay is the brand source: cyan linework on a near-black
// translucent surface. Blue/purple/orange are reserved for waveform energy.

// ---- HUD anchors ----
val HudCyan = Color(0xFF2DEBEB)
val HudCyanSoft = Color(0xFF22D3EE)
val HudBlue = Color(0xFF38BDF8)
val HudViolet = Color(0xFF8B5CF6)
val HudPink = Color(0xFFF472B6)
val HudOrange = Color(0xFFFB923C)
val HudAmber = Color(0xFFF8B500)

// ---- Near-black control surfaces ----
val RetroInk = Color(0xFF02060D)
val RetroSurface = Color(0xE6050912)
val RetroSurfaceHigh = Color(0xF00A111E)
val RetroSurfaceGlass = Color(0xCC030A13)
val RetroOutline = Color(0x992DEBEB)
val RetroOutlineVariant = Color(0x3338E7E7)

// ---- Primary (HUD cyan) ----
val RetroPrimary = HudCyan
val RetroOnPrimary = Color(0xFF001618)
val RetroPrimaryContainer = Color(0xFF07343A)
val RetroOnPrimaryContainer = Color(0xFFB9FFFF)

// ---- Secondary (waveform blue) ----
val RetroSecondary = HudBlue
val RetroOnSecondary = Color(0xFF001527)
val RetroSecondaryContainer = Color(0xFF0B2840)
val RetroOnSecondaryContainer = Color(0xFFC8F3FF)

// ---- Tertiary (waveform violet/pink bridge) ----
val RetroTertiary = HudViolet
val RetroOnTertiary = Color(0xFFF7F2FF)
val RetroTertiaryContainer = Color(0xFF241744)
val RetroOnTertiaryContainer = Color(0xFFE9DDFF)

// ---- Error and warning ----
val RetroError = Color(0xFFFF6B6B)
val RetroOnError = Color(0xFF330006)
val RetroErrorContainer = Color(0xFF4A0C14)
val RetroOnErrorContainer = Color(0xFFFFDAD6)

// ---- Foreground text ----
val RetroOnSurface = Color(0xFFF5FAFF)
val RetroOnSurfaceVariant = Color(0xFFB4C6CC)

// ---- Semantic status (used by StatusIndicator) ----
val StatusRunning = HudCyan
val StatusStarting = HudAmber
val StatusStopped = Color(0xFF7E9098)
val StatusError = RetroError
