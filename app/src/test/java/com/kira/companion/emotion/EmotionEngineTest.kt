package com.kira.companion.emotion

import com.kira.companion.model.KiraEmotion
import org.junit.Assert.assertEquals
import org.junit.Test

class EmotionEngineTest {

    @Test
    fun `greeting is happy`() {
        assertEquals(KiraEmotion.HAPPY, EmotionEngine.classify("привет", KiraEmotion.IDLE))
    }

    @Test
    fun `how are you is happy`() {
        assertEquals(KiraEmotion.HAPPY, EmotionEngine.classify("как дела?", KiraEmotion.IDLE))
    }

    @Test
    fun `thanks is happy`() {
        assertEquals(KiraEmotion.HAPPY, EmotionEngine.classify("спасибо", KiraEmotion.IDLE))
    }

    @Test
    fun `love phrase is love`() {
        assertEquals(KiraEmotion.LOVE, EmotionEngine.classify("я тебя люблю", KiraEmotion.IDLE))
    }

    @Test
    fun `compliment about kira is shy`() {
        assertEquals(KiraEmotion.SHY, EmotionEngine.classify("ты такая милая", KiraEmotion.IDLE))
    }

    @Test
    fun `sadness keyword is sad`() {
        assertEquals(KiraEmotion.SAD, EmotionEngine.classify("мне сегодня грустно", KiraEmotion.IDLE))
    }

    @Test
    fun `severe sadness phrase is crying`() {
        assertEquals(KiraEmotion.CRYING, EmotionEngine.classify("мне очень плохо", KiraEmotion.IDLE))
    }

    @Test
    fun `excitement keyword is excited`() {
        assertEquals(KiraEmotion.EXCITED, EmotionEngine.classify("урааа", KiraEmotion.IDLE))
    }

    @Test
    fun `question about an object is confused`() {
        assertEquals(KiraEmotion.CONFUSED, EmotionEngine.classify("что это вообще?!", KiraEmotion.IDLE))
    }

    @Test
    fun `not understanding is confused`() {
        assertEquals(KiraEmotion.CONFUSED, EmotionEngine.classify("не понимаю", KiraEmotion.IDLE))
    }

    @Test
    fun `wait keyword is thinking`() {
        assertEquals(KiraEmotion.THINKING, EmotionEngine.classify("подожди немного", KiraEmotion.IDLE))
    }

    @Test
    fun `shouted wow is surprised`() {
        assertEquals(KiraEmotion.SURPRISED, EmotionEngine.classify("ВАУ!!!", KiraEmotion.IDLE))
    }

    @Test
    fun `tired keyword is sleepy`() {
        assertEquals(KiraEmotion.SLEEPY, EmotionEngine.classify("я устал", KiraEmotion.IDLE))
    }

    @Test
    fun `angry keyword is angry`() {
        assertEquals(KiraEmotion.ANGRY, EmotionEngine.classify("это бесит", KiraEmotion.IDLE))
    }

    @Test
    fun `repeated ha is laughing`() {
        assertEquals(KiraEmotion.LAUGHING, EmotionEngine.classify("ахахаха", KiraEmotion.IDLE))
    }

    @Test
    fun `all caps gibberish without keywords reads as surprised`() {
        assertEquals(KiraEmotion.SURPRISED, EmotionEngine.classify("БЛАБЛАБЛА", KiraEmotion.IDLE))
    }

    @Test
    fun `single exclamation without keywords reads as happy`() {
        assertEquals(KiraEmotion.HAPPY, EmotionEngine.classify("Пока!", KiraEmotion.IDLE))
    }

    @Test
    fun `question mark without keywords reads as confused`() {
        assertEquals(KiraEmotion.CONFUSED, EmotionEngine.classify("Куда идти?", KiraEmotion.IDLE))
    }

    @Test
    fun `plain statement keeps previous emotion`() {
        assertEquals(KiraEmotion.IDLE, EmotionEngine.classify("просто обычный текст", KiraEmotion.IDLE))
        assertEquals(KiraEmotion.HAPPY, EmotionEngine.classify("просто обычный текст", KiraEmotion.HAPPY))
    }

    @Test
    fun `blank message keeps previous emotion`() {
        assertEquals(KiraEmotion.SAD, EmotionEngine.classify("   ", KiraEmotion.SAD))
        assertEquals(KiraEmotion.LOVE, EmotionEngine.classify("", KiraEmotion.LOVE))
    }
}
