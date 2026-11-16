package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.wallpapers.domain.MakeSeriesPictureUseCase
import com.onthecrow.wallper.wallpapers.domain.MakeSeriesPictureUseCase.Companion.MAX_PICTURES
import com.onthecrow.wallper.wallpapers.series.SeriesPictures
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

internal class MakeSeriesPictureUseCaseImpl @Inject constructor(
    private val seriesPictures: SeriesPictures,
) : MakeSeriesPictureUseCase {

    override suspend fun invoke(groupId: Int, pictures: List<String?>): String? {
        val shown = pictures.take(MAX_PICTURES)
        return try {
            when (shown.size) {
                0 -> null
                // Its member's own picture: nothing to make, and what was made for more members goes.
                1 -> shown.single().also { seriesPictures.delete(groupId) }
                else -> seriesPictures.picture(groupId, shown)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "No picture for series $groupId")
            null
        }
    }
}
