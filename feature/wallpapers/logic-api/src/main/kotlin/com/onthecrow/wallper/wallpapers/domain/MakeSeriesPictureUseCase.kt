package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.core.model.Tint

/**
 * The list's picture of a series: its first members' pictures side by side, parted by slanted lines, each
 * under its member's tint. A series of one shows its member's own picture.
 */
interface MakeSeriesPictureUseCase {

    /**
     * The path to group [groupId]'s picture of [parts], its members' in order, of which the first
     * [MAX_PICTURES] count. A picture is made only when the group has none of these parts yet, and then its
     * older one is deleted. Null if it can't be made. A series of one gets its member's picture as it is,
     * without the tint: whoever shows it lays the tint over.
     */
    suspend operator fun invoke(groupId: Int, parts: List<SeriesPart>): String?

    companion object {
        const val MAX_PICTURES = 4
    }
}

/**
 * A member's part of its series' picture: the [picture] of the wallpaper (null for one that has none: its part
 * stays empty) and the [tint] the wallpaper has over it.
 */
data class SeriesPart(val picture: String?, val tint: Tint? = null)
