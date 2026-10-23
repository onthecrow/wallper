package com.onthecrow.wallper.core.blackhole.physics

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LensTablesTest {

    private val key = LensTableKey(cameraDistance = 32.0, nAlpha = 32, nPhi = 16, k = 7.0)

    @Test
    fun `tables survive the cache file`() = runBlocking {
        val tables = LensTables.generate(key)
        val bytes = ByteArrayOutputStream().also(tables::writeTo).toByteArray()

        val read = assertNotNull(LensTables.readFrom(ByteArrayInputStream(bytes), key))

        assertContentEquals(tables.trajectories, read.trajectories)
        assertContentEquals(tables.phiEnd, read.phiEnd)
    }

    @Test
    fun `a cache file of other tables is rejected`() = runBlocking {
        val bytes = ByteArrayOutputStream().also(LensTables.generate(key)::writeTo).toByteArray()

        assertNull(LensTables.readFrom(ByteArrayInputStream(bytes), key.copy(cameraDistance = 40.0)))
    }

    @Test
    fun `captured rays end at the horizon and escaped rays at zero`() = runBlocking {
        val tables = LensTables.generate(key)

        assertEquals(1f, tables.u(branch = 0, alphaIndex = key.nAlpha - 1, phiIndex = key.nPhi - 1))
        assertEquals(0f, tables.u(branch = 1, alphaIndex = key.nAlpha - 1, phiIndex = key.nPhi - 1))
        assertEquals(1 / 32f, tables.u(branch = 1, alphaIndex = 5, phiIndex = 0), 1e-6f)
    }
}
