package com.example.labmob

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

private const val CUTSCENE_MS = 4_000L
private val CutsceneRed = Color(0xFFE60012)
private val CutsceneInk = Color(0xFF09090D)
private val CutscenePaper = Color(0xFFF4F1E9)

@Composable
internal fun TheurgySequence(round: HuntRound, frameElapsed: Long) {
    val context = LocalContext.current
    val elapsed = (CUTSCENE_MS - round.theurgyRemainingMilliseconds +
        (frameElapsed - round.sampledAtElapsedMs).coerceAtLeast(0L)).coerceIn(0L, CUTSCENE_MS)
    val intro = (elapsed / 480f).coerceIn(0f, 1f)
    val exit = ((elapsed - 3_100L) / 850f).coerceIn(0f, 1f)
    val easedExit = exit * exit * (3f - 2f * exit)
    var fracturePlayed by rememberSaveable { mutableStateOf(false) }
    var returnPlayed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(round.id, round.bonusesCollected) {
        if (!fracturePlayed && elapsed < 800L) {
            fracturePlayed = true
            playTheurgySound(context, R.raw.theurgy_fracture)
        }
    }
    LaunchedEffect(exit > 0f) {
        if (exit > 0f && !returnPlayed) {
            returnPlayed = true
            playTheurgySound(context, R.raw.theurgy_reentry)
        }
    }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - easedExit }.background(CutsceneInk)
        .testTag("theurgy_cutscene")) {
        TheurgyVideo(round, Modifier.fillMaxSize().graphicsLayer {
            alpha = intro
        })
        Canvas(Modifier.fillMaxSize()) {
            drawImpact(intro, exit, elapsed)
        }
    }
}

private fun DrawScope.drawImpact(intro: Float, exit: Float, elapsed: Long) {
    val width = size.width
    val height = size.height
    val retreat = width * intro
    val left = Path().apply {
        moveTo(0f - retreat, 0f)
        lineTo(width * 0.78f - retreat, 0f)
        lineTo(width * 0.35f - retreat, height)
        lineTo(0f - retreat, height)
        close()
    }
    val right = Path().apply {
        moveTo(width * 0.75f + retreat, 0f)
        lineTo(width * 1.35f + retreat, 0f)
        lineTo(width * 1.35f + retreat, height)
        lineTo(width * 0.32f + retreat, height)
        close()
    }
    drawPath(left, CutsceneRed)
    drawPath(right, CutsceneInk)
    if (elapsed < 650L) {
        val crack = (elapsed / 300f).coerceIn(0f, 1f)
        val center = Offset(width * 0.51f, height * 0.47f)
        val ends = listOf(
            Offset(width * 0.10f, height * 0.18f), Offset(width * 0.92f, height * 0.10f),
            Offset(width * 0.02f, height * 0.78f), Offset(width * 0.87f, height * 0.91f),
            Offset(width * 0.48f, height * 0.02f), Offset(width * 0.60f, height * 0.99f),
        )
        ends.forEachIndexed { index, end ->
            drawLine(if (index % 2 == 0) Color.White else CutsceneRed, center,
                Offset(center.x + (end.x - center.x) * crack, center.y + (end.y - center.y) * crack),
                strokeWidth = if (index % 2 == 0) 8f else 14f)
        }
    }
    if (exit > 0f) {
        val sweep = width * (1f - exit)
        val slash = Path().apply {
            moveTo(sweep - width * 0.38f, 0f)
            lineTo(sweep - width * 0.23f, 0f)
            lineTo(sweep + width * 0.17f, height)
            lineTo(sweep + width * 0.03f, height)
            close()
        }
        drawPath(slash, CutsceneRed.copy(alpha = (1f - exit) * 0.85f))
    }
    val flash = when {
        elapsed < 100L -> 1f - elapsed / 100f
        elapsed in 3_100L..3_190L -> 1f - (elapsed - 3_100L) / 90f
        else -> 0f
    }
    if (flash > 0f) drawRect(CutscenePaper.copy(alpha = flash * 0.52f))
}

@Composable
private fun TheurgyVideo(round: HuntRound, modifier: Modifier) {
    val context = LocalContext.current
    val key = "${round.id}:${round.bonusesCollected}"
    val resumeAt = (CUTSCENE_MS - round.theurgyRemainingMilliseconds).coerceIn(0L, CUTSCENE_MS - 100L).toInt()
    val holder = remember(context, key) { TheurgyVideoHolder(context, resumeAt) }
    DisposableEffect(holder) { onDispose { holder.release() } }
    AndroidView(factory = { holder.view }, modifier = modifier)
}

private class TheurgyVideoHolder(context: Context, private val resumeAt: Int) : TextureView.SurfaceTextureListener {
    private val player = MediaPlayer.create(context.applicationContext, R.raw.theurgy_cutscene)
    val view = TextureView(context).apply { surfaceTextureListener = this@TheurgyVideoHolder }
    private var surface: Surface? = null
    private var released = false

    init {
        player?.setOnVideoSizeChangedListener { _, _, _ ->
            applyVideoFit(view, player, view.width, view.height)
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (released) return
        surface = Surface(texture)
        player?.setSurface(surface)
        player?.seekTo(resumeAt)
        player?.setVolume(0.85f, 0.85f)
        player?.start()
        applyVideoFit(view, player, width, height)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        if (!released) applyVideoFit(view, player, width, height)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        if (!released) runCatching { player?.setSurface(null) }
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        if (released) return
        released = true
        view.surfaceTextureListener = null
        runCatching { player?.stop() }
        runCatching { player?.setSurface(null) }
        surface?.release()
        surface = null
        player?.release()
    }
}

private fun applyVideoFit(view: TextureView, player: MediaPlayer?, width: Int, height: Int) {
    val videoWidth = player?.videoWidth ?: 0
    val videoHeight = player?.videoHeight ?: 0
    if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return
    val videoAspect = videoWidth.toFloat() / videoHeight
    val viewAspect = width.toFloat() / height
    val scaleX = if (videoAspect < viewAspect) videoAspect / viewAspect else 1f
    val scaleY = if (videoAspect > viewAspect) viewAspect / videoAspect else 1f
    view.setTransform(Matrix().apply { setScale(scaleX, scaleY, width / 2f, height / 2f) })
}

private fun playTheurgySound(context: Context, resource: Int) {
    MediaPlayer.create(context.applicationContext, resource)?.apply {
        setVolume(1f, 1f)
        setOnCompletionListener { it.release() }
        setOnErrorListener { failed, _, _ -> failed.release(); true }
        start()
    }
}
