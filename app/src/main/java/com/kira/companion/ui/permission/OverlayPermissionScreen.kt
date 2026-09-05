package com.kira.companion.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kira.companion.R
import com.kira.companion.model.KiraEmotion
import com.kira.companion.ui.components.KiraAvatar

@Composable
fun OverlayPermissionScreen(onOpenSettings: () -> Unit, onRecheck: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 32.dp, vertical = 48.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KiraAvatar(emotion = KiraEmotion.CONFUSED, sizeDp = 120.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_explanation),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_open_settings))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRecheck, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_back))
        }
    }
}
