package com.kira.companion.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kira.companion.BuildConfig
import com.kira.companion.KiraApplication
import com.kira.companion.KiraViewModelFactory
import com.kira.companion.R
import com.kira.companion.model.AiProviderType
import com.kira.companion.model.AppTheme
import com.kira.companion.model.KiraSize
import com.kira.companion.model.ReactionFrequency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as KiraApplication
    val viewModel: SettingsViewModel = viewModel(factory = KiraViewModelFactory(app))

    val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
    val reactionFrequency by viewModel.reactionFrequency.collectAsStateWithLifecycle()
    val kiraSize by viewModel.kiraSize.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result intentionally ignored: NotificationHelper re-checks at show-time */ }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SectionTitle(stringResource(R.string.settings_section_overlay))
        SettingsCard {
            SwitchRow(
                label = stringResource(R.string.settings_overlay_enabled),
                checked = overlayEnabled,
                onCheckedChange = { enabled -> viewModel.setOverlayEnabled(context, enabled) },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.settings_section_general))
        SettingsCard {
            LabeledRow(stringResource(R.string.settings_reaction_frequency)) {
                ChipSelector(
                    options = ReactionFrequency.entries,
                    selected = reactionFrequency,
                    label = {
                        when (it) {
                            ReactionFrequency.OFF -> stringResource(R.string.frequency_off)
                            ReactionFrequency.LOW -> stringResource(R.string.frequency_low)
                            ReactionFrequency.NORMAL -> stringResource(R.string.frequency_normal)
                            ReactionFrequency.HIGH -> stringResource(R.string.frequency_high)
                        }
                    },
                    onSelect = viewModel::setReactionFrequency,
                )
            }
            HorizontalDivider()
            LabeledRow(stringResource(R.string.settings_kira_size)) {
                ChipSelector(
                    options = KiraSize.entries,
                    selected = kiraSize,
                    label = {
                        when (it) {
                            KiraSize.SMALL -> stringResource(R.string.size_small)
                            KiraSize.MEDIUM -> stringResource(R.string.size_medium)
                            KiraSize.LARGE -> stringResource(R.string.size_large)
                        }
                    },
                    onSelect = viewModel::setKiraSize,
                )
            }
            HorizontalDivider()
            SwitchRow(
                label = stringResource(R.string.settings_sound),
                checked = soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
            )
            HorizontalDivider()
            SwitchRow(
                label = stringResource(R.string.settings_notifications),
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setNotificationsEnabled(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            HorizontalDivider()
            LabeledRow(stringResource(R.string.settings_theme)) {
                ChipSelector(
                    options = AppTheme.entries,
                    selected = theme,
                    label = {
                        when (it) {
                            AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                            AppTheme.LIGHT -> stringResource(R.string.theme_light)
                            AppTheme.DARK -> stringResource(R.string.theme_dark)
                        }
                    },
                    onSelect = viewModel::setTheme,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.settings_section_ai))
        SettingsCard {
            LabeledRow(stringResource(R.string.settings_ai_provider)) {
                ChipSelector(
                    options = AiProviderType.entries,
                    selected = aiProvider,
                    label = {
                        when (it) {
                            AiProviderType.CHATGPT -> stringResource(R.string.provider_chatgpt)
                            AiProviderType.LOCAL_DEMO -> stringResource(R.string.provider_local_demo)
                            AiProviderType.CUSTOM_API -> stringResource(R.string.provider_custom_api)
                        }
                    },
                    onSelect = viewModel::setAiProvider,
                )
            }
            if (aiProvider == AiProviderType.CHATGPT) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_chatgpt_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (aiProvider == AiProviderType.CUSTOM_API) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.settings_api_key), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.setApiKey(apiKeyInput) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_save)) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_api_key_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.settings_section_data))
        SettingsCard {
            ActionRow(stringResource(R.string.settings_clear_history)) { showClearHistoryDialog = true }
            HorizontalDivider()
            ActionRow(stringResource(R.string.settings_reset_position)) { viewModel.resetKiraPosition() }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.settings_section_about))
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_about_text, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showClearHistoryDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.chat_clear_history)) },
            text = { Text(stringResource(R.string.chat_clear_history_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.clearChatHistory()
                    showClearHistoryDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipSelector(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
