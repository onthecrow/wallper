package com.onthecrow.wallper.blackhole.gl

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onthecrow.wallper.core.blackhole.physics.GyroTracker
import com.onthecrow.wallper.core.blackhole.physics.InputParams
import com.onthecrow.wallper.core.blackhole.physics.Quaternion

/**
 * Feeds the phone's tilt into [renderer] while [enabled] and the screen is resumed, through the same
 * [GyroTracker] as the wallpaper, so the response settings can be tried out here.
 */
@Composable
internal fun PreviewGyroscope(view: GLSurfaceView, renderer: PreviewGlRenderer, enabled: Boolean, input: InputParams) {
    val currentInput by rememberUpdatedState(input)
    LifecycleResumeEffect(view, enabled) {
        val sensorManager = view.context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val reset = Runnable { view.queueEvent { renderer.setGyroTarget(0.0, 0.0) } }
        if (!enabled || sensor == null) {
            onPauseOrDispose { reset.run() }
        } else {
            // 50 samples a second, off the main thread.
            val thread = HandlerThread("PreviewGyroscope").apply { start() }
            val handler = Handler(thread.looper)
            val listener = GyroListener(view, renderer) { currentInput }
            sensorManager.registerListener(listener, sensor, SENSOR_PERIOD_US, handler)
            onPauseOrDispose {
                sensorManager.unregisterListener(listener)
                // After any sample still queued there, so none of them moves the camera again.
                handler.post(reset)
                thread.quitSafely()
            }
        }
    }
}

/** Runs on the sensor thread; [input] reads Compose state, which is safe from any thread. */
private class GyroListener(
    private val view: GLSurfaceView,
    private val renderer: PreviewGlRenderer,
    private val input: () -> InputParams,
) : SensorEventListener {
    private val tracker = GyroTracker()
    private val quaternion = FloatArray(4)
    private var lastNanos = 0L

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getQuaternionFromVector(quaternion, event.values) // w, x, y, z
        val dt = if (lastNanos == 0L) 0.0 else (event.timestamp - lastNanos) / 1e9
        lastNanos = event.timestamp
        val orientation = Quaternion(
            quaternion[1].toDouble(), quaternion[2].toDouble(), quaternion[3].toDouble(), quaternion[0].toDouble(),
        )
        tracker.onSample(orientation, dt, displayRotationDeg(), input())
        val pitch = tracker.targetPitchDeg
        val yaw = tracker.targetYawDeg
        view.queueEvent { renderer.setGyroTarget(pitch, yaw) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun displayRotationDeg(): Int = when (view.display?.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
}

private const val SENSOR_PERIOD_US = 20_000
