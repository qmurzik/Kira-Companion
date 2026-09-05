package com.kira.companion.emotion

import com.kira.companion.model.KiraEmotion
import java.util.Locale

/**
 * Local, rule-based text -> emotion classifier. No network, no ML model — just
 * keyword/regex/emoji matching over Russian and English phrases, plus punctuation and
 * repeated-character heuristics (lots of "!" -> excitement, "?" -> confusion/curiosity,
 * "хаха"/"lol" -> laughing). Rules are checked in a fixed priority order so a single
 * phrase never produces a random/conflicting result.
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

    /** Whole-word matches (with optional Russian/English suffixes), for one or more phrases. */
    private fun words(vararg phrases: String): List<Regex> =
        phrases.map { Regex("(?i)\\b${Regex.escape(it)}\\w*") }

    /** Exact substring matches - for emoji or short glyphs where word-boundary rules don't apply. */
    private fun literal(vararg strings: String): List<Regex> =
        strings.map { Regex(Regex.escape(it)) }

    // Checked top to bottom: the first rule whose pattern matches wins. Order encodes
    // priority, e.g. an explicit "люблю" should win over a generic "!!!" heuristic, and
    // a compliment about Kira ("милая") should read as SHY rather than LOVE.
    private val rules: List<Rule> = listOf(
        Rule(
            KiraEmotion.CRYING,
            words("рыдаю", "навзрыд", "плачу", "sobbing", "рыдаешь") +
                literal("мне очень плохо", "😭", "😢") +
                words("crying"),
        ),
        Rule(
            KiraEmotion.LOVE,
            words("люблю", "любимая", "любимый", "обожаю", "скучаю", "love", "adore", "miss you", "sweetheart") +
                literal("❤️", "💜", "💕", "😍", "🥰"),
        ),
        Rule(
            KiraEmotion.SHY,
            words(
                "милая", "милый", "красивая", "красивый", "симпатичная", "прелесть",
                "cutie", "gorgeous", "beautiful", "adorable",
            ) + literal("ты такая", "you're so cute", "😳", "☺️"),
        ),
        Rule(
            KiraEmotion.LAUGHING,
            listOf(
                Regex("(?i)(ха){2,}"),
                Regex("(?i)(хи){2,}"),
                Regex("(?i)ахах\\w*"),
                Regex("(?i)\\bhaha\\w*"),
                Regex("(?i)\\blol\\b"),
                Regex("(?i)рофл\\w*"),
                Regex("(?i)смешно"),
            ) + literal("😂", "🤣"),
        ),
        Rule(
            KiraEmotion.ANGRY,
            words("бесит", "злюсь", "ненавижу", "раздражает", "достало", "злой", "angry", "furious", "hate", "mad", "annoyed") +
                literal("😡", "🤬"),
        ),
        Rule(
            KiraEmotion.SAD,
            words("грустно", "грущу", "плохо", "тоскливо", "одиноко", "sad", "unhappy", "depressed", "lonely", "heartbroken"),
        ),
        Rule(
            KiraEmotion.SLEEPY,
            words("устал", "устала", "спать", "сонная", "сонный", "вымоталась", "tired", "sleepy", "exhausted", "yawn") +
                literal("😴", "💤") +
                words("gonna sleep"),
        ),
        Rule(
            KiraEmotion.EXCITED,
            words("ура", "круто", "класс", "потрясающе", "вперед", "вперёд", "awesome", "yay", "woohoo", "лучший день") +
                literal("🎉", "✨"),
        ),
        Rule(
            KiraEmotion.SURPRISED,
            words("вау", "ого", "оба", "невероятно", "wow", "whoa", "omg", "no way") +
                literal("😮", "😲"),
        ),
        Rule(
            KiraEmotion.THINKING,
            words("подожди", "думаю", "хм", "хмм", "надо подумать", "погоди", "wait", "hmm", "let me think", "thinking") +
                literal("🤔"),
        ),
        Rule(
            KiraEmotion.WINK,
            words("подмигиваю", "подмигни", "шучу", "wink") + literal(";)", "😉"),
        ),
        Rule(
            KiraEmotion.CONFUSED,
            words("что это", "не понимаю", "непонятно", "запуталась", "запутался", "как это", "confused", "huh", "what do you mean") +
                literal("i don't understand", "😕"),
        ),
        Rule(
            KiraEmotion.HAPPY,
            words("привет", "здравствуй", "как дела", "рада", "рад", "спасибо", "хорошо", "отлично", "хех") +
                words("hello", "hi", "hey", "thanks", "thank you", "great", "good", "glad") +
                literal("😊", "🙂", "😄", "😃"),
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
            else -> previousEmotion
        }
    }
}
