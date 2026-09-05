package com.kira.companion.render

import android.content.Context
import com.kira.companion.vrm.GlbReader
import com.kira.companion.vrm.VrmJsonParser
import com.kira.companion.vrm.VrmModelData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Where Kira's real 3D model must be placed for the app to load it. See README.md. */
const val KIRA_VRM_ASSET_PATH = "models/Kira.vrm"

/**
 * Whether a usable Kira.vrm is available. There is deliberately no state that fakes a
 * loaded character when the file is missing or broken - see [NotFound] and [Invalid] -
 * per the explicit requirement to never substitute a fake/PNG stand-in for the real
 * VRM asset.
 */
sealed interface ModelStatus {
    /** Initial state while [ModelStatusRepository.refresh] is still running. */
    data object Checking : ModelStatus

    /** No file exists at [KIRA_VRM_ASSET_PATH]. */
    data object NotFound : ModelStatus

    /** A file exists there but isn't a usable VRM (wrong format, VRM 1.0, no humanoid rig, ...). */
    data class Invalid(val reason: String) : ModelStatus

    /** A valid, humanoid-rigged VRM 0.x file is ready to be loaded for rendering. */
    data class Ready(val assetPath: String, val data: VrmModelData) : ModelStatus
}

/**
 * Locates and validates Kira.vrm, exposing the result as a [StateFlow] so the UI/overlay
 * can honestly show a "model not found" or "model invalid" screen instead of ever
 * rendering a fake character. Parsing is pure Kotlin (see [GlbReader]/[VrmJsonParser]);
 * only the file access here touches Android.
 */
class ModelStatusRepository(private val context: Context) {

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Checking)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    suspend fun refresh() {
        _status.value = ModelStatus.Checking
        _status.value = withContext(Dispatchers.IO) { resolve() }
    }

    private fun resolve(): ModelStatus {
        val bytes = runCatching {
            context.assets.open(KIRA_VRM_ASSET_PATH).use { it.readBytes() }
        }.getOrNull() ?: return ModelStatus.NotFound

        return try {
            val json = GlbReader.extractJsonChunk(bytes)
            val data = VrmJsonParser.parse(json)
            if (data.isUsable()) {
                ModelStatus.Ready(KIRA_VRM_ASSET_PATH, data)
            } else {
                ModelStatus.Invalid(
                    "В Kira.vrm не найдена гуманоидная кость \"head\" - это не полноценный " +
                        "VRM-аватар с ригом. Переэкспортируйте модель (например, в VRoid Studio) " +
                        "с включённым Humanoid rig и замените assets/models/Kira.vrm.",
                )
            }
        } catch (e: GlbReader.InvalidGlbException) {
            ModelStatus.Invalid(e.message ?: "Файл повреждён или не является .vrm/.glb")
        } catch (e: VrmJsonParser.UnsupportedVrmException) {
            ModelStatus.Invalid(e.message ?: "Неподдерживаемый формат VRM")
        }
    }
}
