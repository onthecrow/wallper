package com.onthecrow.wallper.wallpapers.series

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SeriesPicturesTest {

    private val directory = createTempDirectory("series").toFile()
    private val pictures = SeriesPictures(directory)

    @Test
    fun `the key follows what the picture shows`() {
        val first = File(directory.parentFile, "member-a").apply { writeText("a") }
        val second = File(directory.parentFile, "member-b").apply { writeText("b") }
        val key = pictures.key(listOf(first.path, second.path))

        assertEquals(key, pictures.key(listOf(first.path, second.path)))
        assertNotEquals(key, pictures.key(listOf(second.path, first.path)))
        assertNotEquals(key, pictures.key(listOf(first.path, null)))
        // The same file written again: a thumbnail made anew under its old name.
        second.writeText("bb")
        assertNotEquals(key, pictures.key(listOf(first.path, second.path)))
    }

    @Test
    fun `a group's pictures go with it, the others' stay`() = runTest {
        val kept = picture("3_100.webp")
        val gone = listOf(picture("4_100.webp"), picture("4_200.webp.partial"))

        pictures.delete(4)

        assertEquals(listOf(kept), directory.listFiles()?.toList())
        gone.forEach { assertEquals(false, it.exists()) }
    }

    @Test
    fun `only the pictures of the groups there are stay`() = runTest {
        val kept = listOf(picture("3_100.webp"), picture("5_100.webp"))
        picture("4_100.webp")
        picture("stray")

        pictures.retain(setOf(3, 5))

        assertEquals(kept.toSet(), directory.listFiles()?.toSet())
    }

    private fun picture(name: String) = File(directory, name).apply { writeText("x") }
}
