package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallpaperGroupsTest {

    private val dao = FakeWallpaperDao()
    private val storage = FakeMediaStorage()
    private val repository = WallpapersRepository(dao)
    private val activations = WallpaperActivations()
    private val settings = FakeAppSettings()
    private val add = AddWallpaperUseCaseImpl(repository)
    private val saveGroup = SaveWallpaperGroupUseCaseImpl(repository, ActivateWallpaperUseCaseImpl(repository, activations), settings)
    private val delete = DeleteWallpaperUseCaseImpl(storage, repository)

    private val video = NewWallpaper.Media(
        type = MediaType.VIDEO,
        originalPath = "originals/a",
        thumbnailPath = "thumbnails/a",
        crop = CropRect(10, 20, 110, 220),
        clip = ClipRange(500, 1500),
        mediaInfo = MediaInfo(1920, 1080, 0, 3000),
    )
    private val processed = NewWallpaper.ProcessedVideo(path = "originals/b.mp4", thumbnailPath = "thumbnails/b")

    @Test
    fun `a new group lists its members in order and becomes the active wallpaper`() = runTest {
        val first = add(video)
        val second = add(processed)

        val id = saveGroup(id = null, memberIds = listOf(second, first), slideDuration = SlideDuration(30_000))

        val group = assertIs<Wallpaper.Group>(GetWallpaperUseCaseImpl(repository)(id))
        assertEquals(listOf(second, first), group.members.map { it.id })
        assertEquals(SlideDuration(30_000), group.slideDuration)
        assertTrue(group.isActive)
        assertEquals(group, ObserveWallpapersUseCaseImpl(repository)().first().last())
    }

    @Test
    fun `a new group stays inactive if the user doesn't want new wallpapers to be active`() = runTest {
        settings.settings.value = AppSettings(activateNewWallpapers = false)
        val member = add(video)

        val id = saveGroup(null, listOf(member), SlideDuration.DEFAULT)

        assertEquals(false, GetWallpaperUseCaseImpl(repository)(id)?.isActive)
    }

    @Test
    fun `the active group feeds the engine a series of its members`() = runTest {
        val first = add(video)
        val second = add(processed)
        saveGroup(null, listOf(first, second), SlideDuration.ON_WAKE)

        val spec = assertIs<WallpaperSpec.Series>(ActiveWallpaperSourceImpl(repository, activations).activeWallpaperFlow.first())

        assertEquals(SlideDuration.ON_WAKE, spec.slideDuration)
        assertEquals(WallpaperSpec.ProcessedVideo("originals/b.mp4", "thumbnails/b"), spec.slides[1])
        assertIs<WallpaperSpec.Media>(spec.slides[0])
    }

    @Test
    fun `an edit replaces the members and the time, and keeps the place and the activity`() = runTest {
        val first = add(video)
        val second = add(processed)
        val id = saveGroup(null, listOf(first, second), SlideDuration.DEFAULT)

        assertEquals(id, saveGroup(id, listOf(second), SlideDuration(900_000)))

        val group = assertIs<Wallpaper.Group>(GetWallpaperUseCaseImpl(repository)(id))
        assertEquals(listOf(second), group.members.map { it.id })
        assertEquals(SlideDuration(900_000), group.slideDuration)
        assertTrue(group.isActive)
    }

    @Test
    fun `only a group can be saved as one`() = runTest {
        val member = add(video)

        assertFailsWith<IllegalStateException> { saveGroup(member, listOf(member), SlideDuration.DEFAULT) }
    }

    @Test
    fun `deleting a group leaves its members and their files`() = runTest {
        val member = add(video)
        val id = saveGroup(null, listOf(member), SlideDuration.DEFAULT)

        delete(id)

        assertNull(GetWallpaperUseCaseImpl(repository)(id))
        assertIs<Wallpaper.Media>(GetWallpaperUseCaseImpl(repository)(member))
        assertEquals(emptyList(), storage.deletedPaths)
    }

    @Test
    fun `a deleted member leaves its groups, and a group left empty goes too`() = runTest {
        val first = add(video)
        val second = add(processed)
        val both = saveGroup(null, listOf(first, second), SlideDuration.DEFAULT)
        val onlyFirst = saveGroup(null, listOf(first), SlideDuration.DEFAULT)

        delete(first)

        assertEquals(listOf(second), assertIs<Wallpaper.Group>(GetWallpaperUseCaseImpl(repository)(both)).members.map { it.id })
        assertNull(GetWallpaperUseCaseImpl(repository)(onlyFirst))
    }

    @Test
    fun `groups take no storage and aren't listed there`() = runTest {
        val member = add(video)
        saveGroup(null, listOf(member), SlideDuration.DEFAULT)

        val listed = ObserveWallpaperStorageUseCaseImpl(repository, storage)().first()

        assertEquals(listOf(member), listed.map { it.wallpaper.id })
    }

    @Test
    fun `a built-in can be a member`() = runTest {
        val builtIn = dao.insert(grouplessBuiltIn(BuiltInWallpaper.BLACK_HOLE.key)).toInt()

        val id = saveGroup(null, listOf(builtIn), SlideDuration.DEFAULT)

        val group = assertIs<Wallpaper.Group>(GetWallpaperUseCaseImpl(repository)(id))
        assertIs<Wallpaper.BuiltIn>(group.members.single())
    }

    private fun grouplessBuiltIn(key: String) = WallpaperEntity(
        type = MediaType.IMAGE,
        originalPath = "",
        processedPath = null,
        thumbnailPath = "",
        cropLeft = 0,
        cropTop = 0,
        cropRight = 0,
        cropBottom = 0,
        clipStartMs = 0,
        clipEndMs = 0,
        mediaWidth = 0,
        mediaHeight = 0,
        mediaRotation = 0,
        mediaDurationMs = 0,
        isActive = false,
        builtIn = key,
    )

    private class FakeAppSettings : AppSettingsSource {
        override val settings = MutableStateFlow(AppSettings())

        override fun update(transform: (AppSettings) -> AppSettings) {
            settings.value = transform(settings.value)
        }
    }
}
