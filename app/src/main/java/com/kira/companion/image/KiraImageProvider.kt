package com.kira.companion.image

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kira.companion.model.KiraEmotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Looks for the shipped (or user-replaceable) artwork under assets/kira/emotions/<emotion>.png
 * and decodes it if present. Returns null when no art exists for that emotion, so callers
 * fall back to the built-in procedural [com.kira.companion.ui.components.KiraFace] drawing.
 *
 * This is the whole "asset swap system": drop a PNG in assets/kira/emotions/ named after
 * the lowercase enum value and it's picked up automatically, no code changes required.
 */
object KiraImageProvider {

    suspend fun loadOverrideBitmap(context: Context, emotion: KiraEmotion): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val fileName = "kira/emotions/${emotion.name.lowercase(Locale.ROOT)}.png"
            runCatching {
                context.assets.open(fileName).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
}
