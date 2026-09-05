package com.kira.companion.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kira.companion.KiraApplication
import com.kira.companion.MainActivity
import com.kira.companion.ai.openChatGpt
import com.kira.companion.behavior.TouchRegion
import com.kira.companion.model.KiraEmotion
import com.kira.companion.model.KiraPosition
import com.kira.companion.model.KiraSize
import com.kira.companion.model.ScreenEdge
import com.kira.companion.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The overlay service is what makes Kira appear "on top of" other apps. It owns two
 * WindowManager windows:
 *  - the small draggable bubble (always visible while the overlay is enabled), and
 *  - a full-screen, mostly-transparent window that only exists while the long-press
 *    menu is open (so its scrim can catch outside taps to dismiss it).
 *
 * It intentionally does very little work in the background: no polling loops besides
 * the deliberately slow, user-configurable "random reaction" timer.
 */
class OverlayService : Service() {

    private val overlayLifecycleOwner = OverlayLifecycleOwner()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var app: KiraApplication
    private lateinit var windowManager: WindowManager

    private var bubbleView: ComposeView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var bubbleSizePx: Int = 0

    private var menuView: ComposeView? = null
    private var menuParams: WindowManager.LayoutParams? = null

    private var bubbleSizeDpState by mutableStateOf(96f)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as KiraApplication
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        overlayLifecycleOwner.performRestore()
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startForegroundWithNotification()

        serviceScope.launch {
            val size = app.settingsRepository.kiraSize.first()
            val position = app.settingsRepository.kiraPosition.first()
            addBubbleWindow(size, position)
            observeSettings()
        }

        serviceScope.launch {
            app.settingsRepository.reactionFrequency.collect { frequency ->
                app.behaviorController.setRandomBehaviorFrequency(frequency)
            }
        }
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationHelper.buildOverlayNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.OVERLAY_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.OVERLAY_NOTIFICATION_ID, notification)
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            app.settingsRepository.overlayEnabled.collect { enabled ->
                if (!enabled) stopSelf()
            }
        }
        serviceScope.launch {
            app.settingsRepository.kiraSize.collect { size -> applyBubbleSize(size) }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun sizeDp(size: KiraSize): Int = when (size) {
        KiraSize.SMALL -> 64
        KiraSize.MEDIUM -> 96
        KiraSize.LARGE -> 132
    }

    private fun addBubbleWindow(size: KiraSize, position: KiraPosition) {
        val dp = sizeDp(size)
        bubbleSizeDpState = dp.toFloat()
        bubbleSizePx = dpToPx(dp)

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val initialX = if (position.edge == ScreenEdge.LEFT) 0 else (screenW - bubbleSizePx).coerceAtLeast(0)
        val maxY = (screenH - bubbleSizePx).coerceAtLeast(0)
        val initialY = (position.verticalFraction * maxY).toInt().coerceIn(0, maxY)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setContent {
                OverlayBubble(
                    modelStatusRepository = app.modelStatusRepository,
                    behaviorController = app.behaviorController,
                    sizeDp = bubbleSizeDpState.dp,
                    onTap = ::onBubbleTap,
                    onLongPress = ::openMenu,
                    onDrag = ::onBubbleDrag,
                    onDragEnd = ::onBubbleDragEnd,
                )
            }
        }
        bubbleView = view

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        windowManager.addView(view, bubbleParams)
    }

    private fun applyBubbleSize(size: KiraSize) {
        val newSizePx = dpToPx(sizeDp(size))
        bubbleSizeDpState = sizeDp(size).toFloat()
        bubbleSizePx = newSizePx
        clampBubbleToScreen()
    }

    private fun clampBubbleToScreen() {
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - bubbleSizePx).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - bubbleSizePx).coerceAtLeast(0)
        bubbleParams.x = bubbleParams.x.coerceIn(0, maxX)
        bubbleParams.y = bubbleParams.y.coerceIn(0, maxY)
        bubbleView?.let { runCatching { windowManager.updateViewLayout(it, bubbleParams) } }
    }

    private fun onBubbleTap(region: TouchRegion, gazeDx: Float, gazeDy: Float) {
        app.behaviorController.onTap(region, gazeDx, gazeDy, nowMillis = System.currentTimeMillis())
    }

    private fun onBubbleDrag(dx: Float, dy: Float) {
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - bubbleSizePx).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - bubbleSizePx).coerceAtLeast(0)
        bubbleParams.x = (bubbleParams.x + dx.toInt()).coerceIn(0, maxX)
        bubbleParams.y = (bubbleParams.y + dy.toInt()).coerceIn(0, maxY)
        bubbleView?.let { runCatching { windowManager.updateViewLayout(it, bubbleParams) } }
    }

    private fun onBubbleDragEnd() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val maxY = (metrics.heightPixels - bubbleSizePx).coerceAtLeast(0)

        val snapToLeft = (bubbleParams.x + bubbleSizePx / 2) < screenW / 2
        val targetX = if (snapToLeft) 0 else (screenW - bubbleSizePx).coerceAtLeast(0)
        bubbleParams.x = targetX
        bubbleView?.let { runCatching { windowManager.updateViewLayout(it, bubbleParams) } }

        val verticalFraction = if (maxY > 0) bubbleParams.y.toFloat() / maxY else 0f
        val position = KiraPosition(
            edge = if (snapToLeft) ScreenEdge.LEFT else ScreenEdge.RIGHT,
            verticalFraction = verticalFraction.coerceIn(0f, 1f),
        )
        serviceScope.launch { app.settingsRepository.setKiraPosition(position) }
    }

    private fun openMenu() {
        if (menuView != null) return

        val metrics = resources.displayMetrics
        val cardWidthPx = dpToPx(216)
        val cardHeightEstimatePx = dpToPx(260)
        val gapPx = dpToPx(8)

        val belowY = bubbleParams.y + bubbleSizePx + gapPx
        val anchorY = if (belowY + cardHeightEstimatePx > metrics.heightPixels) {
            (bubbleParams.y - cardHeightEstimatePx - gapPx).coerceAtLeast(0)
        } else {
            belowY
        }
        val anchorX = bubbleParams.x.coerceIn(0, (metrics.widthPixels - cardWidthPx).coerceAtLeast(0))
        val anchorOffset = IntOffset(anchorX, anchorY)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setContent {
                OverlayMenuOverlay(
                    anchorOffsetPx = anchorOffset,
                    onDismiss = ::closeMenu,
                    onChat = { closeMenu(); openChatGpt(this@OverlayService) },
                    onEmotions = { closeMenu(); cycleDebugEmotion() },
                    onSettings = { closeMenu(); openAppScreen(MainActivity.EXTRA_OPEN_SETTINGS) },
                    onHide = { closeMenu(); hideOverlay() },
                    onClose = { closeMenu(); exitApp() },
                )
            }
        }
        menuView = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        menuParams = params
        windowManager.addView(view, params)
    }

    private fun closeMenu() {
        val view = menuView ?: return
        runCatching { windowManager.removeView(view) }
        menuView = null
        menuParams = null
    }

    private fun cycleDebugEmotion() {
        val all = KiraEmotion.entries
        val current = app.behaviorController.emotion.emotion.value
        val next = all[(all.indexOf(current) + 1) % all.size]
        app.behaviorController.emotion.setEmotion(next)
    }

    private fun openAppScreen(extra: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(extra, true)
        }
        startActivity(intent)
    }

    private fun hideOverlay() {
        serviceScope.launch { app.settingsRepository.setOverlayEnabled(false) }
    }

    private fun exitApp() {
        serviceScope.launch { app.settingsRepository.setOverlayEnabled(false) }
        stopSelf()
    }

    override fun onDestroy() {
        closeMenu()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
