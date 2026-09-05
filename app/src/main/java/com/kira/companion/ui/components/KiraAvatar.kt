package com.kira.companion.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kira.companion.R
import com.kira.companion.image.KiraImageProvider
import com.kira.companion.model.KiraEmotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full Kira avatar: tries the shipped/user-supplied artwork first (see [KiraImageProvider]),
 * otherwise renders the built-in animated [KiraFace]. Handles idle sway/blink, a short "pop"
 * animation whenever the emotion changes away from IDLE, a smooth cross-fade between
 * emotions (never an instant image swap), and a small always-on accent layer (hearts,
 * tears, "Zzz", "!", "...") drawn on top regardless of whether the face itself is a photo
 * or the procedural drawing.
 */
@Composable
fun KiraAvatar(
    emotion: KiraEmotion,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 96.dp,
) {
    val context = LocalContext.current

    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(emotion) {
        if (emotion != KiraEmotion.IDLE) {
            scale.animateTo(1.18f, animationSpec = tween(140, easing = FastOutSlowInEasing))
            scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "kira-idle")
    val swayAmplitude = if (emotion == KiraEmotion.SLEEPY) 6f else 3f
    val swayDurationMs = when (emotion) {
        KiraEmotion.THINKING -> 700
        KiraEmotion.SLEEPY -> 2600
        else -> 1800
    }
    val sway by infiniteTransition.animateFloat(
        initialValue = -swayAmplitude,
        targetValue = swayAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(swayDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )

    val blink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3900
                0.05f at 4000
                1f at 4100
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )

    Box(
        modifier = modifier
            .size(sizeDp)
            .graphicsLayer(scaleX = scale.value, scaleY = scale.value, rotationZ = sway),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = emotion,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.88f))
                    .togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.06f))
            },
            label = "kira-emotion-crossfade",
        ) { targetEmotion ->
            var bitmap by remember(targetEmotion) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(targetEmotion) {
                bitmap = KiraImageProvider.loadOverrideBitmap(context, targetEmotion)
            }
            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap,
                    contentDescription = stringResource(R.string.content_desc_kira),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                KiraFace(
                    emotion = targetEmotion,
                    eyeOpenness = if (targetEmotion == KiraEmotion.SLEEPY) 0.08f else blink,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawEmotionAccent(emotion)
        }
    }
}

/** The built-in, dependency-free illustration of Kira: a Compose [Canvas] drawing. */
@Composable
fun KiraFace(emotion: KiraEmotion, eyeOpenness: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawKiraFace(emotion = emotion, eyeOpenness = eyeOpenness)
    }
}

private val HairColor = Color(0xFF211433)
private val SkinColor = Color(0xFFFFE3C7)
private val EyeColor = Color(0xFF6D28D9)
private val MouthColor = Color(0xFFB4587A)
private val BlushColor = Color(0x66F472B6)
private val AccentPink = Color(0xFFEC4899)
private val AngerRed = Color(0xFFDC2626)
private val TearBlue = Color(0xFF60A5FA)

private fun DrawScope.drawKiraFace(emotion: KiraEmotion, eyeOpenness: Float) {
    val s = size.minDimension / 108f
    val ox = (size.width - 108f * s) / 2f
    val oy = (size.height - 108f * s) / 2f
    fun x(v: Float) = ox + v * s
    fun y(v: Float) = oy + v * s

    // Hair silhouette (a short, slightly tousled bob).
    val hairPath = Path().apply {
        moveTo(x(54f), y(18f))
        cubicTo(x(71.9f), y(18f), x(84f), y(32.9f), x(84f), y(49f))
        cubicTo(x(84f), y(60f), x(79f), y(68f), x(79f), y(78f))
        lineTo(x(74f), y(78f))
        cubicTo(x(74f), y(68f), x(76f), y(58f), x(68f), y(50f))
        cubicTo(x(64f), y(58f), x(44f), y(58f), x(40f), y(50f))
        cubicTo(x(32f), y(58f), x(34f), y(68f), x(34f), y(78f))
        lineTo(x(29f), y(78f))
        cubicTo(x(29f), y(68f), x(24f), y(60f), x(24f), y(49f))
        cubicTo(x(24f), y(32.9f), x(36.1f), y(18f), x(54f), y(18f))
        close()
    }
    drawPath(hairPath, color = HairColor)

    // Face
    val facePath = Path().apply {
        moveTo(x(54f), y(38f))
        cubicTo(x(67.25f), y(38f), x(76f), y(47.8f), x(76f), y(60f))
        cubicTo(x(76f), y(74.4f), x(65.9f), y(86f), x(54f), y(86f))
        cubicTo(x(42.1f), y(86f), x(32f), y(74.4f), x(32f), y(60f))
        cubicTo(x(32f), y(47.8f), x(40.75f), y(38f), x(54f), y(38f))
        close()
    }
    drawPath(facePath, color = SkinColor)

    // Hair clip accent
    val clipPath = Path().apply {
        moveTo(x(67f), y(34f))
        lineTo(x(73f), y(31f))
        lineTo(x(74.8f), y(36.6f))
        lineTo(x(68.8f), y(39.6f))
        close()
    }
    drawPath(clipPath, color = AccentPink)

    val leftEye = Offset(x(44f), y(60f))
    val rightEye = Offset(x(64f), y(60f))
    val eyeRadius = 5f * s

    fun openEye(center: Offset, pupilShiftX: Float = 0f) {
        val h = (eyeRadius * 2f) * eyeOpenness.coerceIn(0.05f, 1f)
        drawOval(
            color = EyeColor,
            topLeft = Offset(center.x - eyeRadius, center.y - h / 2f),
            size = Size(eyeRadius * 2f, h),
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.28f,
            center = Offset(
                center.x - eyeRadius * 0.35f + pupilShiftX * eyeRadius * 0.5f,
                center.y - h * 0.22f,
            ),
        )
    }

    fun closedEye(center: Offset) {
        drawArc(
            color = HairColor,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - eyeRadius, center.y - eyeRadius * 0.6f),
            size = Size(eyeRadius * 2f, eyeRadius * 1.2f),
            style = Stroke(width = 2.4f * s, cap = StrokeCap.Round),
        )
    }

    fun heartEye(center: Offset) {
        val r = eyeRadius * 0.62f
        drawCircle(color = AccentPink, radius = r, center = Offset(center.x - r * 0.65f, center.y - r * 0.3f))
        drawCircle(color = AccentPink, radius = r, center = Offset(center.x + r * 0.65f, center.y - r * 0.3f))
        val trianglePath = Path().apply {
            moveTo(center.x - r * 1.55f, center.y - r * 0.1f)
            lineTo(center.x + r * 1.55f, center.y - r * 0.1f)
            lineTo(center.x, center.y + r * 1.5f)
            close()
        }
        drawPath(trianglePath, color = AccentPink)
    }

    fun eyebrow(center: Offset, angleDeg: Float) {
        val length = eyeRadius * 1.6f
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = (length / 2f * cos(rad)).toFloat()
        val dy = (length / 2f * sin(rad)).toFloat()
        val browCenter = Offset(center.x, center.y - eyeRadius * 2.1f)
        drawLine(
            color = HairColor,
            start = Offset(browCenter.x - dx, browCenter.y - dy),
            end = Offset(browCenter.x + dx, browCenter.y + dy),
            strokeWidth = 2.2f * s,
            cap = StrokeCap.Round,
        )
    }

    when (emotion) {
        KiraEmotion.IDLE -> {
            openEye(leftEye)
            openEye(rightEye)
        }
        KiraEmotion.HAPPY, KiraEmotion.EXCITED, KiraEmotion.LAUGHING -> {
            closedEye(leftEye)
            closedEye(rightEye)
        }
        KiraEmotion.SAD -> {
            openEye(leftEye)
            openEye(rightEye)
            eyebrow(leftEye, 20f)
            eyebrow(rightEye, 160f)
        }
        KiraEmotion.CRYING -> {
            closedEye(leftEye)
            closedEye(rightEye)
            eyebrow(leftEye, 20f)
            eyebrow(rightEye, 160f)
        }
        KiraEmotion.ANGRY -> {
            openEye(leftEye)
            openEye(rightEye)
            eyebrow(leftEye, 160f)
            eyebrow(rightEye, 20f)
        }
        KiraEmotion.SLEEPY -> {
            closedEye(leftEye)
            closedEye(rightEye)
        }
        KiraEmotion.CONFUSED -> {
            openEye(leftEye, pupilShiftX = 1f)
            openEye(rightEye, pupilShiftX = 1f)
            eyebrow(leftEye, 10f)
        }
        KiraEmotion.SHY -> {
            openEye(leftEye, pupilShiftX = -1f)
            openEye(rightEye, pupilShiftX = -1f)
        }
        KiraEmotion.LOVE -> {
            heartEye(leftEye)
            heartEye(rightEye)
        }
        KiraEmotion.SURPRISED -> {
            openEye(leftEye)
            openEye(rightEye)
            eyebrow(leftEye, -10f)
            eyebrow(rightEye, 190f)
        }
        KiraEmotion.THINKING -> {
            openEye(leftEye, pupilShiftX = 1f)
            openEye(rightEye, pupilShiftX = 1f)
        }
        KiraEmotion.WINK -> {
            closedEye(leftEye)
            openEye(rightEye)
        }
    }

    val mouthPath = Path()
    var drawStrokedMouth = true
    when (emotion) {
        KiraEmotion.HAPPY, KiraEmotion.LOVE -> {
            mouthPath.moveTo(x(46f), y(71f))
            mouthPath.quadraticTo(x(54f), y(78f), x(62f), y(71f))
        }
        KiraEmotion.EXCITED, KiraEmotion.LAUGHING -> {
            drawOval(color = MouthColor, topLeft = Offset(x(48f), y(70f)), size = Size(12f * s, 9f * s))
            drawStrokedMouth = false
        }
        KiraEmotion.SAD, KiraEmotion.CRYING -> {
            mouthPath.moveTo(x(47f), y(76f))
            mouthPath.quadraticTo(x(54f), y(70f), x(61f), y(76f))
        }
        KiraEmotion.ANGRY -> {
            mouthPath.moveTo(x(47f), y(74f))
            mouthPath.lineTo(x(61f), y(74f))
        }
        KiraEmotion.SURPRISED -> {
            drawOval(color = MouthColor, topLeft = Offset(x(50f), y(70f)), size = Size(8f * s, 10f * s))
            drawStrokedMouth = false
        }
        else -> {
            mouthPath.moveTo(x(48f), y(73f))
            mouthPath.quadraticTo(x(54f), y(76f), x(60f), y(73f))
        }
    }
    if (drawStrokedMouth) {
        drawPath(
            mouthPath,
            color = MouthColor,
            style = Stroke(width = 2.6f * s, cap = StrokeCap.Round),
        )
    }
}

/**
 * Small floating accents (hearts, tears, "Zzz", "!", "...") drawn on top of whichever face
 * is showing - the shipped artwork or the procedural [KiraFace] alike - so the emotion
 * always reads clearly regardless of the underlying art.
 */
private fun DrawScope.drawEmotionAccent(emotion: KiraEmotion) {
    val s = size.minDimension / 108f
    val ox = (size.width - 108f * s) / 2f
    val oy = (size.height - 108f * s) / 2f
    fun x(v: Float) = ox + v * s
    fun y(v: Float) = oy + v * s

    when (emotion) {
        KiraEmotion.EXCITED -> {
            drawSparkle(Offset(x(26f), y(30f)), 4.5f * s, AccentPink)
            drawSparkle(Offset(x(84f), y(28f)), 3.5f * s, AccentPink)
        }
        KiraEmotion.SAD -> drawDroplet(Offset(x(46f), y(67f)), 3f * s, TearBlue)
        KiraEmotion.CRYING -> {
            drawDroplet(Offset(x(42f), y(68f)), 3.6f * s, TearBlue)
            drawDroplet(Offset(x(66f), y(68f)), 3.6f * s, TearBlue)
        }
        KiraEmotion.ANGRY -> drawAngerMark(Offset(x(76f), y(42f)), 5f * s, AngerRed)
        KiraEmotion.SLEEPY -> drawZzz(Offset(x(80f), y(34f)), s)
        KiraEmotion.CONFUSED -> drawDroplet(Offset(x(76f), y(46f)), 4f * s, TearBlue)
        KiraEmotion.LOVE -> {
            drawHeartFloat(Offset(x(80f), y(28f)), 6f * s, AccentPink)
            drawCircle(color = BlushColor, radius = 6f * s, center = Offset(x(37f), y(68f)))
            drawCircle(color = BlushColor, radius = 6f * s, center = Offset(x(71f), y(68f)))
        }
        KiraEmotion.SHY -> {
            drawCircle(color = BlushColor, radius = 7f * s, center = Offset(x(37f), y(68f)))
            drawCircle(color = BlushColor, radius = 7f * s, center = Offset(x(71f), y(68f)))
        }
        KiraEmotion.THINKING -> drawDots(Offset(x(78f), y(38f)), s)
        KiraEmotion.SURPRISED -> drawExclaim(Offset(x(80f), y(32f)), 5f * s, HairColor)
        KiraEmotion.WINK -> drawHeartFloat(Offset(x(78f), y(30f)), 5f * s, AccentPink)
        KiraEmotion.IDLE, KiraEmotion.HAPPY, KiraEmotion.LAUGHING -> Unit
    }
}

private fun DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
    drawLine(color, Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
}

private fun DrawScope.drawDroplet(center: Offset, r: Float, color: Color) {
    drawCircle(color = color, radius = r, center = Offset(center.x, center.y + r * 0.3f))
    val path = Path().apply {
        moveTo(center.x, center.y - r * 1.4f)
        lineTo(center.x - r * 0.8f, center.y)
        lineTo(center.x + r * 0.8f, center.y)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawAngerMark(center: Offset, r: Float, color: Color) {
    drawLine(color, Offset(center.x - r, center.y - r), Offset(center.x + r, center.y + r), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
    drawLine(color, Offset(center.x + r, center.y - r), Offset(center.x - r, center.y + r), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
}

private fun DrawScope.drawZzz(anchor: Offset, s: Float) {
    val color = Color(0xFF8B5CF6)
    drawCircle(color = color.copy(alpha = 0.9f), radius = 2.2f * s, center = anchor)
    drawCircle(color = color.copy(alpha = 0.6f), radius = 1.6f * s, center = Offset(anchor.x + 5f * s, anchor.y - 5f * s))
    drawCircle(color = color.copy(alpha = 0.4f), radius = 1.1f * s, center = Offset(anchor.x + 9f * s, anchor.y - 9f * s))
}

private fun DrawScope.drawDots(anchor: Offset, s: Float) {
    val color = Color(0xFF6D28D9)
    for (i in 0..2) {
        drawCircle(color = color, radius = 1.6f * s, center = Offset(anchor.x + i * 5f * s, anchor.y))
    }
}

private fun DrawScope.drawExclaim(center: Offset, r: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - r * 0.18f, center.y - r * 1.3f),
        size = Size(r * 0.36f, r * 1.6f),
        cornerRadius = CornerRadius(r * 0.18f, r * 0.18f),
    )
    drawCircle(color = color, radius = r * 0.22f, center = Offset(center.x, center.y + r * 0.55f))
}

private fun DrawScope.drawHeartFloat(center: Offset, r: Float, color: Color) {
    drawCircle(color = color, radius = r * 0.55f, center = Offset(center.x - r * 0.5f, center.y))
    drawCircle(color = color, radius = r * 0.55f, center = Offset(center.x + r * 0.5f, center.y))
    val path = Path().apply {
        moveTo(center.x - r * 1.05f, center.y + r * 0.05f)
        lineTo(center.x + r * 1.05f, center.y + r * 0.05f)
        lineTo(center.x, center.y + r * 1.1f)
        close()
    }
    drawPath(path, color = color)
}
