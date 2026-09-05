package com.kira.companion.ai

import com.kira.companion.emotion.EmotionEngine
import com.kira.companion.model.KiraEmotion
import kotlinx.coroutines.delay

/**
 * Fully offline reply generator so the whole app - chat UI, history, and the emotion
 * engine reacting to Kira's own "replies" - can be tried and tested with zero setup,
 * no network access and no API key of any kind. This is what the app uses whenever the
 * user hasn't opted into a real backend; it is not meant to be a serious AI.
 */
class LocalDemoAiProvider : AiProvider {

    override suspend fun sendMessage(message: String, history: List<Pair<Boolean, String>>): String {
        delay(500L + (0..600L).random())

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
                "Привет! ♡",
                "Я здесь~",
                "Хихи, рада тебя видеть!",
            ),
            KiraEmotion.LOVE to listOf(
                "Ты тоже мне очень дорог(а) 💕",
                "От твоих слов у меня прямо тепло на душе.",
                "Спасибо, мне очень приятно это слышать!",
                "У меня всё хорошо 💜",
            ),
            KiraEmotion.SHY to listOf(
                "Ой... не говори так, я сейчас засмущаюсь~",
                "Хи, спасибо... мне немного неловко от комплиментов.",
            ),
            KiraEmotion.SAD to listOf(
                "Мне жаль, что тебе грустно. Я рядом, если хочешь поговорить об этом.",
                "Обнимаю тебя мысленно. Что случилось?",
                "Иногда просто нужно выговориться — я слушаю.",
            ),
            KiraEmotion.CRYING to listOf(
                "Мне очень жаль... иди сюда, я обниму тебя (мысленно) 🤍",
                "Всё будет хорошо. Расскажи мне, что случилось?",
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
            KiraEmotion.WINK to listOf(
                "Хи-хи, а вот и секрет~ 😉",
                "Ну ты и шутник(ца)!",
            ),
            KiraEmotion.LAUGHING to listOf(
                "Ахаха, вот это ты сказал(а)!",
                "Хихи, очень смешно!",
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
