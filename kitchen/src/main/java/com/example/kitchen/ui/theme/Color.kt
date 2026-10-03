package com.example.kitchen.ui.theme

import androidx.compose.ui.graphics.Color

// Shadcn Minimalist Black & White Palette
val ShadcnBlack = Color(0xFF09090B)
val ShadcnWhite = Color(0xFFFFFFFF)
val ShadcnMutedBg = Color(0xFFF4F4F5)
val ShadcnBorder = Color(0xFFE4E4E7)
val ShadcnMutedText = Color(0xFF71717A)

// Canvas & Surfaces
val CreamBackground = Color(0xFFFAFAFA)
val CreamSurface = ShadcnWhite

// Semantic Status Accents
val StatusGreen = Color(0xFF16A34A)
val StatusAmber = Color(0xFFD97706)
val StatusRed = Color(0xFFDC2626)
val StatusBlue = Color(0xFF2563EB)

// Kitchen KDS Urgency Colors (Wait times)
val UrgencyNormal = Color(0xFFF0FDF4)      // < 3 min: subtle green tint
val UrgencyWarning = Color(0xFFFFFBEB)     // 3-6 min: subtle amber tint
val UrgencyUrgent = Color(0xFFFEF2F2)      // > 6 min: subtle red tint
val UrgencyBorderNormal = Color(0xFFBBF7D0)
val UrgencyBorderWarning = Color(0xFFFDE68A)
val UrgencyBorderUrgent = Color(0xFFFECACA)
