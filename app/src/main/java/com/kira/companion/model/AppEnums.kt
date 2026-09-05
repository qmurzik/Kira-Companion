package com.kira.companion.model

enum class ReactionFrequency { LOW, NORMAL, HIGH }

enum class KiraSize { SMALL, MEDIUM, LARGE }

enum class AppTheme { SYSTEM, LIGHT, DARK }

enum class AiProviderType { MOCK, OPENAI }

enum class ScreenEdge { LEFT, RIGHT }

/** Normalized, density/resolution independent overlay position. */
data class KiraPosition(
    val edge: ScreenEdge = ScreenEdge.RIGHT,
    val verticalFraction: Float = 0.35f,
)
