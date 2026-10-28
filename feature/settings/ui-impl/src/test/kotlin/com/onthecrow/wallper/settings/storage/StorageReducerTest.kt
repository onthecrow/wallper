package com.onthecrow.wallper.settings.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class StorageReducerTest {

    private val reducer = StorageReducer()

    @Test
    fun `nothing is counted until the wallpapers come, the largest first`() = runTest {
        assertNull(StorageState().items)
        assertNull(StorageState().totalBytes)

        val small = StorageItem(1, "a", StorageItem.Kind.PICTURE, 10)
        val large = StorageItem(2, "b", StorageItem.Kind.ORIGINAL_VIDEO, 1_000)
        val state = reducer.reduce(StorageState(), StorageEvent.OnItemsChanged(listOf(small, large)))

        assertEquals(listOf(large, small), state.items)
        assertEquals(1_010, state.totalBytes)
    }

    @Test
    fun `navigation changes nothing by itself`() = runTest {
        val state = StorageState(items = emptyList())

        assertSame(state, reducer.reduce(state, StorageEvent.OnBackClick))
        assertSame(state, reducer.reduce(state, StorageEvent.OnEditClick(1)))
    }

    @Test
    fun `a deletion waits for the confirmation, which ends it either way`() = runTest {
        val asked = reducer.reduce(StorageState(), StorageEvent.OnDeleteClick(4))
        assertEquals(4, asked.deleteCandidate)

        assertNull(reducer.reduce(asked, StorageEvent.OnDeleteConfirm(4)).deleteCandidate)
        assertNull(reducer.reduce(asked, StorageEvent.OnDeleteDismiss).deleteCandidate)
    }
}
