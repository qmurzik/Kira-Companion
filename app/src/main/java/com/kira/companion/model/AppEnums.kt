package com.kira.companion.model

enum class ReactionFrequency { OFF, LOW, NORMAL, HIGH }

enum class KiraSize { SMALL, MEDIUM, LARGE }

enum class AppTheme { SYSTEM, LIGHT, DARK }

/**
 * How Kira's chat "brain" is backed:
 *  - [CHATGPT]: no local AI at all. The chat screen just offers a button that opens the
 *    official ChatGPT app/website via a normal Android Intent. No API key, no session
 *    scraping - this app never touches ChatGPT's login state.
 *  - [LOCAL_DEMO]: a fully offline, canned-but-on-brand response generator so the whole
 *    app (chat UI + emotion engine) can be tried/tested with zero setup.
 *  - [CUSTOM_API]: optional, user-supplied OpenAI-compatible API key for real AI replies
 *    inside the app. Entirely opt-in, never required to use Kira.
 */
enum class AiProviderType { CHATGPT, LOCAL_DEMO, CUSTOM_API }

enum class ScreenEdge { LEFT, RIGHT }

/** Normalized, density/resolution independent overlay position. */
data class KiraPosition(
    val edge: ScreenEdge = ScreenEdge.RIGHT,
    val verticalFraction: Float = 0.35f,
)
