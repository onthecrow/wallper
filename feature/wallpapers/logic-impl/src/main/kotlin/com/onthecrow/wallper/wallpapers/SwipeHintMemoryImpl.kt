package com.onthecrow.wallper.wallpapers

import android.content.Context
import androidx.core.content.edit
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.wallpapers.domain.SwipeHintMemory
import com.onthecrow.wallper.wallpapers.domain.SwipeHintMemory.Companion.SHOWS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Device-protected, like the rest of the app's preferences, although only the series editor reads it. */
internal class SwipeHintMemoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchersProvider: DispatchersProvider,
) : SwipeHintMemory {

    private val preferences by lazy {
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    override suspend fun isDue(): Boolean = withContext(dispatchersProvider.io) {
        preferences.getInt(KEY_SWIPE_HINT_SHOWN, 0) < SHOWS
    }

    override fun countShown() = preferences.edit {
        putInt(KEY_SWIPE_HINT_SHOWN, preferences.getInt(KEY_SWIPE_HINT_SHOWN, 0) + 1)
    }

    private companion object {
        const val PREFS = "wallpapers"
        const val KEY_SWIPE_HINT_SHOWN = "swipe_hint_shown"
    }
}
