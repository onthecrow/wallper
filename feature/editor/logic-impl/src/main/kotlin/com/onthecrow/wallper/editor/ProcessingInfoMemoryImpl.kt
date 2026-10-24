package com.onthecrow.wallper.editor

import android.content.Context
import androidx.core.content.edit
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.editor.domain.ProcessingInfoMemory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Device-protected, like the rest of the app's preferences, although only the editor reads it. */
internal class ProcessingInfoMemoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchersProvider: DispatchersProvider,
) : ProcessingInfoMemory {

    private val preferences by lazy {
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    override suspend fun isSeen(): Boolean = withContext(dispatchersProvider.io) {
        preferences.getBoolean(KEY_PROCESSING_INFO_SEEN, false)
    }

    override fun markSeen() = preferences.edit { putBoolean(KEY_PROCESSING_INFO_SEEN, true) }

    private companion object {
        const val PREFS = "editor"
        const val KEY_PROCESSING_INFO_SEEN = "processing_info_seen"
    }
}
