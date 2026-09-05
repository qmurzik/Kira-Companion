package com.kira.companion.emotion

import com.kira.companion.model.KiraEmotion
import java.util.Locale

/**
 * Local, rule-based text -> emotion classifier. No network, no ML model — just
 * keyword/regex matching over Russian and English phrases, plus simple punctuation
 * heuristics (lots of "!" -> excitement, "?" -> confusion/curiosity).
 *
 * This is deliberately a single pure function behind a small interface so it can be
 * swapped later for an on-device or remote AI-based classifier without touching
 * callers (see [EmotionClassifier]).
 */
interface EmotionClassifier {
    fun classify(message: String, previousEmotion: KiraEmotion): KiraEmotion
}

object EmotionEngine : EmotionClassifier {

    private data class Rule(val emotion: KiraEmotion, val patterns: List<Regex>)

    private fun words(vararg words: String): List<Regex> =
        words.map { Regex("(?i)\\b${Regex.escape(it)}\\w*") }

    private val rules: List<Rule> = listOf(
        Rule(
            KiraEmotion.LOVE,
            words(
                "люблю", "любимая", "любимый", "обожаю", "милая", "милый", "скучаю",
                "love", "adore", "miss you", "cutie", "sweetheart",
            ),
        ),
        Rule(
            KiraEmotion.EXCITED,
            words(
                "ура", "круто", "класс", "потрясающе", "вперед", "вперёд", "awesome",
                "yay", "woohoo", "let's go", "лучший день",
            ),
        ),
        Rule(
            KiraEmotion.SAD,
            words(
                "грустно", "грущу", "плохо", "плачу", "тоскливо", "одиноко", "устала морально",
                "sad", "unhappy", "cry", "crying", "depressed", "lonely", "heartbroken",
            ),
        ),
        Rule(
            KiraEmotion.ANGRY,
            words(
                "бесит", "злюсь", "ненавижу", "раздражает", "достало", "злой",
                "angry", "furious", "hate", "mad", "annoyed",
            ),
        ),
        Rule(
            KiraEmotion.SLEEPY,
            words(
                "устал", "устала", "хочу спать", "спать", "сонная", "сонный", "вымоталась",
                "tired", "sleepy", "exhausted", "yawn", "gonna sleep",
            ),
        ),
        Rule(
            KiraEmotion.SURPRISED,
            words("вау", "ого", "оба", "невероятно", "wow", "whoa", "omg", "no way"),
        ),
        Rule(
            KiraEmotion.THINKING,
            words(
                "подожди", "думаю", "хм", "хмм", "надо подумать", "погоди",
                "wait", "hmm", "let me think", "thinking",
            ),
        ),
        Rule(
            KiraEmotion.CONFUSED,
            words(
                "что это", "не понимаю", "непонятно", "запуталась", "запутался", "как это",
                "confused", "i don't understand", "huh", "what do you mean",
            ),
        ),
        Rule(
            KiraEmotion.HAPPY,
            words(
                "привет", "здравствуй", "рада", "рад", "спасибо", "хорошо", "отлично", "хех",
                "hello", "hi", "hey", "thanks", "thank you", "great", "good", "glad",
            ),
        ),
    )

    override fun classify(message: String, previousEmotion: KiraEmotion): KiraEmotion {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return previousEmotion

        val normalized = trimmed.lowercase(Locale.getDefault())

        for (rule in rules) {
            if (rule.patterns.any { it.containsMatchIn(normalized) }) {
                return rule.emotion
            }
        }

        val exclamations = trimmed.count { it == '!' }
        val questions = trimmed.count { it == '?' }
        val isShouting = trimmed.length > 3 && trimmed == trimmed.uppercase(Locale.getDefault()) &&
            trimmed.any { it.isLetter() }

        return when {
            isShouting || exclamations >= 2 -> KiraEmotion.SURPRISED
            exclamations == 1 -> KiraEmotion.HAPPY
            questions >= 1 -> KiraEmotion.CONFUSED
            else -> KiraEmotion.HAPPY
        }
    }
}
