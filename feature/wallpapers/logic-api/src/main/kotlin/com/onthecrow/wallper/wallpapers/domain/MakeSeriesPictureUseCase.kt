package com.onthecrow.wallper.wallpapers.domain

/**
 * The list's picture of a series: its first members' pictures side by side, parted by slanted lines. A series
 * of one shows its member's own picture.
 */
interface MakeSeriesPictureUseCase {

    /**
     * The path to group [groupId]'s picture of [pictures], its members' pictures in order (null for one that has
     * none: its part stays empty), of which the first [MAX_PICTURES] count. A picture is made only when the group
     * has none of these pictures yet, and then its older one is deleted. Null if it can't be made.
     */
    suspend operator fun invoke(groupId: Int, pictures: List<String?>): String?

    companion object {
        const val MAX_PICTURES = 4
    }
}
