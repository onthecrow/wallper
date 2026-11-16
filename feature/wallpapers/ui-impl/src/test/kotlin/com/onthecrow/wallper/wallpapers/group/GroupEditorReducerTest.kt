package com.onthecrow.wallper.wallpapers.group

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.core.model.SlideTransition
import com.onthecrow.wallper.core.model.TransitionEffect
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GroupEditorReducerTest {

    private val reducer = GroupEditorReducer()
    private val items = (1..4).map { WallpaperItem(it, "picture-$it", isActive = false) }
    private val listed = GroupEditorState(wallpapers = items)

    @Test
    fun `a new group starts by picking, an edited one by arranging once it is read`() {
        val new = GroupEditorState.initial(groupId = null)
        val edited = GroupEditorState.initial(groupId = 3)

        assertEquals(GroupEditorStep.PICK, new.step)
        assertTrue(new.isLoaded)
        assertEquals(GroupEditorStep.ARRANGE, edited.step)
        assertFalse(edited.isLoaded)
    }

    @Test
    fun `picked wallpapers become the members in the list's order`() = runTest {
        var state = listed
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(3))
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(1))
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(2))
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(2))

        state = reducer.reduce(state, GroupEditorEvent.OnPickConfirm)

        assertEquals(GroupEditorStep.ARRANGE, state.step)
        assertEquals(listOf(1, 3), state.members)
        assertEquals(emptySet(), state.picked)
    }

    @Test
    fun `fewer than two picked, nothing to confirm`() = runTest {
        assertSame(listed, reducer.reduce(listed, GroupEditorEvent.OnPickConfirm))

        val one = reducer.reduce(listed, GroupEditorEvent.OnWallpaperClick(2))
        assertFalse(one.canConfirmPick)
        assertSame(one, reducer.reduce(one, GroupEditorEvent.OnPickConfirm))

        // Adding to members: those unticked down to one can't go on either.
        val adding = reducer.reduce(listed.copy(step = GroupEditorStep.ARRANGE, members = listOf(3, 1)), GroupEditorEvent.OnAddClick)
        val unticked = reducer.reduce(adding, GroupEditorEvent.OnWallpaperClick(1))
        assertSame(unticked, reducer.reduce(unticked, GroupEditorEvent.OnPickConfirm))
    }

    @Test
    fun `adding keeps the members' order, new ones follow and unticked ones leave`() = runTest {
        val arranged = listed.copy(step = GroupEditorStep.ARRANGE, members = listOf(3, 1))

        var state = reducer.reduce(arranged, GroupEditorEvent.OnAddClick)
        assertEquals(GroupEditorStep.PICK, state.step)
        assertEquals(setOf(3, 1), state.picked)
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(1))
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(4))
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(2))
        state = reducer.reduce(state, GroupEditorEvent.OnPickConfirm)

        assertEquals(listOf(3, 2, 4), state.members)
    }

    @Test
    fun `back from adding changes nothing`() = runTest {
        val arranged = listed.copy(step = GroupEditorStep.ARRANGE, members = listOf(3, 1))
        val adding = reducer.reduce(reducer.reduce(arranged, GroupEditorEvent.OnAddClick), GroupEditorEvent.OnWallpaperClick(4))

        val state = reducer.reduce(adding, GroupEditorEvent.OnPickCancel)

        // Only the way it slides: back.
        assertEquals(arranged.copy(stepGoesBack = true), state)
    }

    @Test
    fun `steps slide on as they open and back as they close, a new group's first pick going on`() = runTest {
        var state = reducer.reduce(listed, GroupEditorEvent.OnWallpaperClick(1))
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(2))
        state = reducer.reduce(state, GroupEditorEvent.OnPickConfirm)
        assertEquals(GroupEditorStep.ARRANGE, state.step)
        assertFalse(state.stepGoesBack)

        state = reducer.reduce(state, GroupEditorEvent.OnAddClick)
        assertFalse(state.stepGoesBack)
        state = reducer.reduce(state, GroupEditorEvent.OnWallpaperClick(3))
        state = reducer.reduce(state, GroupEditorEvent.OnPickConfirm)
        assertTrue(state.stepGoesBack)

        state = reducer.reduce(state, GroupEditorEvent.OnAddClick)
        state = reducer.reduce(state, GroupEditorEvent.OnPickCancel)
        assertTrue(state.stepGoesBack)
    }

    @Test
    fun `members dropped in a new order keep it`() = runTest {
        val arranged = listed.copy(step = GroupEditorStep.ARRANGE, members = listOf(3, 1, 2))

        val state = reducer.reduce(arranged, GroupEditorEvent.OnMembersReorder(listOf(1, 2, 3)))

        assertEquals(listOf(1, 2, 3), state.members)
    }

    @Test
    fun `a swiped member leaves`() = runTest {
        val arranged = listed.copy(step = GroupEditorStep.ARRANGE, members = listOf(3, 1, 2))

        assertEquals(listOf(3, 2), reducer.reduce(arranged, GroupEditorEvent.OnMemberRemove(1)).members)
    }

    @Test
    fun `save waits for two members and saves them with the time`() = runTest {
        val one = listed.copy(step = GroupEditorStep.ARRANGE, members = listOf(2))
        assertSame(one, reducer.reduce(one, GroupEditorEvent.OnSaveClick))

        val two = reducer.reduce(one.copy(members = listOf(2, 4)), GroupEditorEvent.OnDurationPick(SlideDuration.ON_WAKE))
        val saving = reducer.reduce(two, GroupEditorEvent.OnSaveClick)

        assertEquals(GroupSaveRequest(listOf(2, 4), SlideDuration.ON_WAKE, SlideTransition.NONE), saving.save)
        assertFalse(saving.canSave)
        assertNull(reducer.reduce(saving, GroupEditorEvent.OnSaveFailed).save)
    }

    @Test
    fun `a member deleted meanwhile is neither shown nor saved`() = runTest {
        val arranged = listed.copy(step = GroupEditorStep.ARRANGE, members = listOf(2, 9, 4))

        val saving = reducer.reduce(arranged, GroupEditorEvent.OnSaveClick)

        assertEquals(listOf(2, 4), saving.memberItems.map { it.id })
        assertEquals(listOf(2, 4), saving.save?.memberIds)
    }

    @Test
    fun `hidden built-ins aren't offered unless they are members already`() = runTest {
        val blackHole = WallpaperItem(9, null, false, BuiltInWallpaper.BLACK_HOLE)
        val state = reducer.reduce(GroupEditorState(), GroupEditorEvent.OnWallpapersChanged(items + blackHole, showsBuiltIns = false))

        assertEquals(items, state.pickable)
        assertEquals(items + blackHole, state.copy(members = listOf(9)).pickable)
    }

    @Test
    fun `a rendered preview becomes the built-in's picture and survives list updates`() = runTest {
        val blackHole = WallpaperItem(9, null, false, BuiltInWallpaper.BLACK_HOLE)
        var state = reducer.reduce(GroupEditorState(), GroupEditorEvent.OnWallpapersChanged(listOf(blackHole), showsBuiltIns = true))

        state = reducer.reduce(state, GroupEditorEvent.OnBuiltInPreviewLoaded(BuiltInWallpaper.BLACK_HOLE, "previews/bh.png"))
        state = reducer.reduce(state, GroupEditorEvent.OnWallpapersChanged(listOf(blackHole), showsBuiltIns = true))

        assertEquals("previews/bh.png", state.wallpapers.single().picturePath)
    }

    @Test
    fun `the transition's step opens only where transitions are offered, and back from it keeps the pick`() = runTest {
        val arranging = listed.copy(step = GroupEditorStep.ARRANGE, members = listOf(1, 2))
        assertSame(arranging, reducer.reduce(arranging, GroupEditorEvent.OnTransitionClick))

        var state = reducer.reduce(arranging, GroupEditorEvent.OnTransitionsOffered(true))
        state = reducer.reduce(state, GroupEditorEvent.OnTransitionClick)
        assertEquals(GroupEditorStep.TRANSITION, state.step)
        assertNull(state.previewSlides)

        state = reducer.reduce(state, GroupEditorEvent.OnTransitionEffectPick(TransitionEffect.RIPPLE))
        state = reducer.reduce(state, GroupEditorEvent.OnTransitionDurationPick(60_000))
        state = reducer.reduce(state, GroupEditorEvent.OnTransitionClose)

        assertEquals(GroupEditorStep.ARRANGE, state.step)
        assertEquals(SlideTransition(TransitionEffect.RIPPLE, SlideTransition.MAX_DURATION_MS), state.transition)
    }

    @Test
    fun `slides read for a step that was left are dropped`() = runTest {
        val arranging = listed.copy(step = GroupEditorStep.ARRANGE, offersTransitions = true)

        val state = reducer.reduce(arranging, GroupEditorEvent.OnPreviewSlidesLoaded(emptyList()))

        assertNull(state.previewSlides)
    }

    @Test
    fun `the save takes the transition along`() = runTest {
        val state = listed.copy(
            step = GroupEditorStep.ARRANGE,
            members = listOf(1, 2),
            transition = SlideTransition(TransitionEffect.LUMA, 800),
        )

        val saving = reducer.reduce(state, GroupEditorEvent.OnSaveClick)

        assertEquals(SlideTransition(TransitionEffect.LUMA, 800), saving.save?.transition)
    }
}
