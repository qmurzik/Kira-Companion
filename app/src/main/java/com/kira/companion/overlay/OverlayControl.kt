package com.kira.companion.overlay

import android.content.Context
import com.kira.companion.KiraApplication

/** Shared logic for turning Kira's overlay on/off, used by both the Home and Settings screens. */
object OverlayControl {
    suspend fun setEnabled(context: Context, app: KiraApplication, enabled: Boolean) {
        app.settingsRepository.setOverlayEnabled(enabled)
        if (enabled) {
            OverlayService.start(context)
        } else {
            OverlayService.stop(context)
        }
    }
}
