package com.kira.companion.ui.character

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kira.companion.behavior.KiraBehaviorController
import com.kira.companion.render.ModelStatus
import com.kira.companion.render.ModelStatusRepository
import com.kira.companion.render.VrmCharacterHost
import io.github.sceneview.RenderQuality

/**
 * Shows Kira: the real 3D model once it's confirmed loadable, or an honest explanation of
 * why not (missing/invalid file) - never a fake stand-in. See [ModelStatusRepository] and
 * README.md for exactly what's required of assets/models/Kira.vrm.
 */
@Composable
fun KiraCharacterView(
    modelStatusRepository: ModelStatusRepository,
    behaviorController: KiraBehaviorController,
    modifier: Modifier = Modifier,
    renderQuality: RenderQuality = RenderQuality.Default,
    minUpdateIntervalMillis: Long = 0L,
) {
    val status by modelStatusRepository.status.collectAsStateWithLifecycle()

    LaunchedEffect(modelStatusRepository) {
        modelStatusRepository.refresh()
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val current = status) {
            ModelStatus.Checking -> CircularProgressIndicator()
            ModelStatus.NotFound -> ModelMessage(
                title = "Модель Kira.vrm не найдена",
                message = "Поместите файл Kira.vrm в assets/models/Kira.vrm перед сборкой " +
                    "приложения. Требования к модели (VRM 0.x, гуманоидный риг, " +
                    "blend shapes) описаны в README.md.",
            )
            is ModelStatus.Invalid -> ModelMessage(
                title = "Модель Kira.vrm повреждена или не поддерживается",
                message = current.reason,
            )
            is ModelStatus.Ready -> VrmCharacterHost(
                modelData = current.data,
                assetPath = current.assetPath,
                controller = behaviorController,
                modifier = Modifier.fillMaxSize(),
                renderQuality = renderQuality,
                minUpdateIntervalMillis = minUpdateIntervalMillis,
            )
        }
    }
}

@Composable
private fun ModelMessage(title: String, message: String) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
