package com.kira.companion.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kira.companion.KiraApplication
import com.kira.companion.KiraViewModelFactory
import com.kira.companion.R
import com.kira.companion.ui.components.KiraAvatar
import com.kira.companion.ui.permission.OverlayPermissionScreen

@Composable
fun HomeScreen(onNavigateChat: () -> Unit, onNavigateSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KiraApplication
    val viewModel: HomeViewModel = viewModel(factory = KiraViewModelFactory(app))

    val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
    val emotion by viewModel.emotion.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasPermission) {
        OverlayPermissionScreen(
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            },
            onRecheck = { hasPermission = Settings.canDrawOverlays(context) },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        KiraAvatar(emotion = emotion, sizeDp = 150.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (overlayEnabled) R.string.home_status_active else R.string.home_status_inactive,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.home_overlay_toggle), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = { viewModel.setOverlayEnabled(context, it) },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onNavigateChat, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Chat, contentDescription = null)
            Text(text = stringResource(R.string.home_open_chat), modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onNavigateSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Settings, contentDescription = null)
            Text(text = stringResource(R.string.home_open_settings), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
