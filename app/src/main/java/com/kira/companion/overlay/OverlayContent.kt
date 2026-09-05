package com.kira.companion.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kira.companion.R
import com.kira.companion.behavior.KiraBehaviorController
import com.kira.companion.behavior.TouchGeometry
import com.kira.companion.behavior.TouchRegion
import com.kira.companion.render.ModelStatusRepository
import com.kira.companion.ui.character.KiraCharacterView
import com.kira.companion.ui.theme.KiraCompanionTheme
import io.github.sceneview.RenderQuality
import kotlinx.coroutines.delay

@Composable
fun OverlayBubble(
    modelStatusRepository: ModelStatusRepository,
    behaviorController: KiraBehaviorController,
    sizeDp: Dp,
    onTap: (region: TouchRegion, gazeDx: Float, gazeDy: Float) -> Unit,
    onLongPress: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val bubbleSizePx = with(density) { sizeDp.toPx() }

    KiraCompanionTheme {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SpeechBubble(behaviorController = behaviorController)
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .kiraOverlayGestures(
                        onTap = { offset ->
                            val nx = (offset.x / bubbleSizePx).coerceIn(0f, 1f)
                            val ny = (offset.y / bubbleSizePx).coerceIn(0f, 1f)
                            val region = TouchGeometry.regionFor(nx, ny)
                            val (gazeDx, gazeDy) = TouchGeometry.gazeOffsetFor(nx, ny)
                            onTap(region, gazeDx, gazeDy)
                        },
                        onLongPress = onLongPress,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                    ),
            ) {
                KiraCharacterView(
                    modelStatusRepository = modelStatusRepository,
                    behaviorController = behaviorController,
                    modifier = Modifier.size(sizeDp),
                    renderQuality = RenderQuality.Performance,
                    // The bubble is on screen indefinitely, not for a deliberate viewing
                    // session - cap her behavior/physics tick to ~30fps rather than
                    // whatever the display's native refresh rate is.
                    minUpdateIntervalMillis = 33L,
                )
            }
        }
    }
}

/**
 * Polls [KiraBehaviorController.speechBubble] (a plain, frame-driven controller, not a
 * [kotlinx.coroutines.flow.Flow]) at a light 150ms interval and shows its current text in a
 * small bubble above Kira, fading in/out rather than popping instantly.
 */
@Composable
private fun SpeechBubble(behaviorController: KiraBehaviorController) {
    var state by remember { mutableStateOf(behaviorController.speechBubble.current) }
    LaunchedEffect(behaviorController) {
        while (true) {
            state = behaviorController.speechBubble.current
            delay(150L)
        }
    }

    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val text = state.text
        if (text != null) {
            Card(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 220.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun OverlayMenuOverlay(
    anchorOffsetPx: IntOffset,
    onDismiss: () -> Unit,
    onChat: () -> Unit,
    onEmotions: () -> Unit,
    onSettings: () -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
) {
    KiraCompanionTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        ) {
            Box(
                modifier = Modifier
                    .offset { anchorOffsetPx }
                    .width(216.dp),
            ) {
                MenuCard(
                    onChat = onChat,
                    onEmotions = onEmotions,
                    onSettings = onSettings,
                    onHide = onHide,
                    onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun MenuCard(
    onChat: () -> Unit,
    onEmotions: () -> Unit,
    onSettings: () -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MenuRow(Icons.Filled.Chat, stringResource(R.string.menu_chat), onChat)
            MenuRow(Icons.Filled.EmojiEmotions, stringResource(R.string.menu_emotions), onEmotions)
            MenuRow(Icons.Filled.Settings, stringResource(R.string.menu_settings), onSettings)
            MenuRow(Icons.Filled.VisibilityOff, stringResource(R.string.menu_hide), onHide)
            MenuRow(Icons.Filled.Close, stringResource(R.string.menu_close), onClose)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
