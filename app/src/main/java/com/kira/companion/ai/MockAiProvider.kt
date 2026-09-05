package com.kira.companion.ai

import com.kira.companion.emotion.EmotionEngine
import com.kira.companion.model.KiraEmotion
import kotlinx.coroutines.delay

/**
 * Offline fallback so the app is fully usable without any API key or network access.
 * Picks a friendly, on-brand reply based on the same rule-based sentiment read used
 * by the emotion engine, with a short simulated "typing" delay.
 */
class MockAiProvider : AiProvider {

    override suspend fun sendMessage(message: String, history: List<Pair<Boolean, String>>): String {
        delay(600L + (0..700L).random())

        val emotion = EmotionEngine.classify(message, KiraEmotion.IDLE)
        val bank = responses[emotion] ?: responses.getValue(KiraEmotion.IDLE)
        return bank.random()
    }

    private companion object {
        val responses: Map<KiraEmotion, List<String>> = mapOf(
            KiraEmotion.HAPPY to listOf(
                "Привет! Я так рада, что ты заглянул(а) 💜",
                "Хей! Как твои дела сегодня?",
                "Ура, ты тут! Расскажи, как прошёл день?",
            ),
            KiraEmotion.LOVE to listOf(
                "Ты тоже мне очень дорог(а) 💕",
                "От твоих слов у меня прямо тепло на душе.",
                "Спасибо, мне очень приятно это слышать!",
            ),
            KiraEmotion.SAD to listOf(
                "Мне жаль, что тебе грустно. Я рядом, если хочешь поговорить об этом.",
                "Обнимаю тебя мысленно. Что случилось?",
                "Иногда просто нужно выговориться — я слушаю.",
            ),
            KiraEmotion.ANGRY to listOf(
                "Понимаю, это правда раздражает. Хочешь рассказать подробнее?",
                "Ух, звучит непросто. Давай разберёмся вместе?",
            ),
            KiraEmotion.SLEEPY to listOf(
                "Похоже, тебе пора отдохнуть. Не забывай о сне!",
                "М-м, я тоже что-то сонная. Может, пора на боковую?",
            ),
            KiraEmotion.SURPRISED to listOf(
                "Ого! Вот это новость!",
                "Ничего себе, я не ожидала такого поворота!",
            ),
            KiraEmotion.THINKING to listOf(
                "Хм, дай мне подумать над этим секундочку…",
                "Интересный вопрос. Сейчас соображу.",
            ),
            KiraEmotion.CONFUSED to listOf(
                "Хм, кажется, я не совсем поняла. Можешь объяснить иначе?",
                "Что-то я запуталась — расскажи чуть подробнее?",
            ),
            KiraEmotion.EXCITED to listOf(
                "Дааа, вот это энергия! Обожаю такое настроение!",
                "Ураа, это звучит потрясающе!",
            ),
            KiraEmotion.IDLE to listOf(
                "Расскажи мне об этом побольше?",
                "Я тебя внимательно слушаю.",
                "Интересно! А что дальше?",
            ),
        )
    }
}
