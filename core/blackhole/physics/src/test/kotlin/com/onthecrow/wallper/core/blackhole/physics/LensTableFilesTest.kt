package com.onthecrow.wallper.core.blackhole.physics

import java.io.EOFException
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LensTableFilesTest {

    private val directory = createTempDirectory("lens").toFile()
    private val files = LensTableFiles(directory)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `every quality of one camera distance stays cached`() {
        val high = key(nAlpha = 16)
        val medium = key(nAlpha = 8)
        val low = key(nAlpha = 4)

        listOf(high, medium, low).forEach { files.write(tables(it)) }

        listOf(high, medium, low).forEach { assertEquals(it, assertNotNull(files.read(it)).key) }
    }

    @Test
    fun `a new camera distance or K replaces the old files`() {
        val old = key(nAlpha = 16)
        files.write(tables(old))
        files.write(tables(key(nAlpha = 8)))

        files.write(tables(key(nAlpha = 16, cameraDistance = 40.0)))
        assertNull(files.read(old))
        assertEquals(1, directory.list()?.size)

        files.write(tables(key(nAlpha = 16, cameraDistance = 40.0, k = 5.0)))
        assertEquals(1, directory.list()?.size)
    }

    @Test
    fun `files of other names are left alone`() {
        val other = File(directory, "settings.json").apply { writeText("{}") }

        files.write(tables(key(nAlpha = 16)))
        files.write(tables(key(nAlpha = 16, cameraDistance = 40.0)))

        assertEquals("{}", other.readText())
    }

    @Test
    fun `a truncated file throws instead of reading as tables`() {
        val key = key(nAlpha = 16)
        files.write(tables(key))
        val file = directory.listFiles()!!.single()
        file.writeBytes(file.readBytes().copyOf(HEADER_BYTES + 8))

        assertFailsWith<EOFException> { files.read(key) }
    }

    private fun key(nAlpha: Int, cameraDistance: Double = 32.0, k: Double = 7.0) =
        LensTableKey(cameraDistance, nAlpha, nPhi = 4, k = k)

    private fun tables(key: LensTableKey) =
        LensTables(key, FloatArray(2 * key.nPhi * key.nAlpha), FloatArray(2 * key.nAlpha))

    private companion object {
        /** Magic, version, r_c, nAlpha, nPhi, K. */
        const val HEADER_BYTES = 4 + 4 + 8 + 4 + 4 + 8
    }
}
