package com.onthecrow.wallper.core.engine.impl.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/** Emits each time the screen turns off: whatever shows next is a new look at the phone. */
internal class ScreenOffs @Inject constructor(@param:ApplicationContext private val context: Context) {

    val events: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        // Screen broadcasts reach only receivers registered at runtime.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
