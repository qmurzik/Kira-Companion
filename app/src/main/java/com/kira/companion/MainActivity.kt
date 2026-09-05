package com.kira.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kira.companion.model.AppTheme
import com.kira.companion.navigation.KiraApp
import com.kira.companion.navigation.KiraDestination
import com.kira.companion.ui.theme.KiraCompanionTheme

class MainActivity : ComponentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingRoute = routeFromIntent(intent)

        setContent {
            val app = applicationContext as KiraApplication
            val appTheme by app.settingsRepository.theme.collectAsStateWithLifecycle(initialValue = AppTheme.SYSTEM)

            KiraCompanionTheme(appTheme = appTheme) {
                KiraApp(
                    pendingRoute = pendingRoute,
                    onPendingRouteConsumed = { pendingRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeFromIntent(intent)
    }

    private fun routeFromIntent(intent: Intent?): String? = when {
        intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true -> KiraDestination.CHAT.route
        intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true -> KiraDestination.SETTINGS.route
        else -> null
    }

    companion object {
        const val EXTRA_OPEN_CHAT = "com.kira.companion.extra.OPEN_CHAT"
        const val EXTRA_OPEN_SETTINGS = "com.kira.companion.extra.OPEN_SETTINGS"
    }
}
