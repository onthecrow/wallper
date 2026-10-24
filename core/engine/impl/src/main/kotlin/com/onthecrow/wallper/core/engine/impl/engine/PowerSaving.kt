package com.onthecrow.wallper.core.engine.impl.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Whether the phone asks apps to save power: Battery Saver is on, or it is hot (severe thermal status
 * and up, API 29+; the black hole goes still at the same point).
 */
internal class PowerSaving @Inject constructor(@param:ApplicationContext private val context: Context) {

    val isSaving: Flow<Boolean> = callbackFlow {
        val powerManager = context.getSystemService(PowerManager::class.java)
        fun current() = powerManager.isPowerSaveMode || isHot(powerManager)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(current())
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Typed as Any: the listener interface doesn't exist before API 29.
        val thermalListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { trySend(current()) }
                .also(powerManager::addThermalStatusListener)
        } else {
            null
        }
        trySend(current())

        awaitClose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager.removeThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener)
            }
        }
    }.distinctUntilChanged()

    private fun isHot(powerManager: PowerManager): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
}
