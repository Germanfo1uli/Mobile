package com.example.labmob

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.sign

@Composable
internal fun TiltSensorEffect(enabled: Boolean, onTilt: (Float, Float) -> Unit) {
    val context = LocalContext.current
    val latestOnTilt = rememberUpdatedState(onTilt)

    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return@DisposableEffect onDispose { }
        var filteredX = 0f
        var filteredY = 0f
        var sentX = Float.NaN
        var sentY = Float.NaN
        var lastSentAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Sensor gravity points opposite to the visual direction in which
                // objects should slide on the screen, so both natural axes are inverted.
                val naturalX = (-event.values[0] / 6f).coerceIn(-1f, 1f)
                val naturalY = (event.values[1] / 6f).coerceIn(-1f, 1f)
                val rotation = (context as? Activity)?.windowManager?.defaultDisplay?.rotation
                    ?: Surface.ROTATION_0
                val (screenX, screenY) = when (rotation) {
                    Surface.ROTATION_90 -> -naturalY to naturalX
                    Surface.ROTATION_180 -> -naturalX to -naturalY
                    Surface.ROTATION_270 -> naturalY to -naturalX
                    else -> naturalX to naturalY
                }
                filteredX = filteredX * 0.72f + screenX * 0.28f
                filteredY = filteredY * 0.72f + screenY * 0.28f
                val x = applyDeadZone(filteredX)
                val y = applyDeadZone(filteredY)
                val now = SystemClock.elapsedRealtime()
                val changed = sentX.isNaN() || abs(x - sentX) + abs(y - sentY) >= 0.07f
                if (changed && now - lastSentAt >= 110L) {
                    sentX = x
                    sentY = y
                    lastSentAt = now
                    latestOnTilt.value(x, y)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
}

private fun applyDeadZone(value: Float): Float {
    val magnitude = abs(value)
    if (magnitude < 0.08f) return 0f
    return (sign(value) * ((magnitude - 0.08f) / 0.92f)).coerceIn(-1f, 1f)
}
