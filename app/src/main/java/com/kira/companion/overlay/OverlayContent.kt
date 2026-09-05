package com.kira.companion.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kira.companion.R
import com.kira.companion.model.KiraEmotion
import com.kira.companion.ui.components.KiraAvatar
import com.kira.companion.ui.theme.KiraCompanionTheme

@Composable
fun OverlayBubble(
    emotion: KiraEmotion,
    sizeDp: Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    KiraCompanionTheme {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .kiraOverlayGestures(onTap, onLongPress, onDrag, onDragEnd),
        ) {
            KiraAvatar(emotion = emotion, sizeDp = sizeDp)
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
