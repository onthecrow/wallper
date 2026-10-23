package com.onthecrow.wallper.blackhole.physics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The tables for [params] at [quality]. */
fun lensTableKey(params: BlackHoleParams, quality: QualityLevel) =
    LensTableKey(params.camera.distance, quality.tableAlpha, quality.tablePhi, params.lens.tableK)

/** What the tables depend on; part of the cache file name and header. */
data class LensTableKey(val cameraDistance: Double, val nAlpha: Int, val nPhi: Int, val k: Double) {
    val alphaC: Double get() = LensMath.alphaC(cameraDistance)
}

/**
 * Precomputed photon trajectories for every ray from a camera at `key.cameraDistance` (§5.3). Thanks to
 * spherical symmetry a ray depends only on its angle α to the hole, so one table serves every pixel.
 */
class LensTables(
    val key: LensTableKey,
    /** u(φ) per ray: `[(branch · nPhi + j) · nAlpha + i]`, branch 0 captured, 1 escaped; a GL 2D array. */
    val trajectories: FloatArray,
    /** φ_end per ray: `[branch · nAlpha + i]`. */
    val phiEnd: FloatArray,
) {
    init {
        require(trajectories.size == 2 * key.nPhi * key.nAlpha) { "trajectories size" }
        require(phiEnd.size == 2 * key.nAlpha) { "phiEnd size" }
    }

    fun u(branch: Int, alphaIndex: Int, phiIndex: Int): Float =
        trajectories[(branch * key.nPhi + phiIndex) * key.nAlpha + alphaIndex]

    fun phiEnd(branch: Int, alphaIndex: Int): Float = phiEnd[branch * key.nAlpha + alphaIndex]

    fun writeTo(output: OutputStream) {
        val data = DataOutputStream(output.buffered())
        data.writeInt(MAGIC)
        data.writeInt(FORMAT_VERSION)
        data.writeDouble(key.cameraDistance)
        data.writeInt(key.nAlpha)
        data.writeInt(key.nPhi)
        data.writeDouble(key.k)
        data.writeFloats(trajectories)
        data.writeFloats(phiEnd)
        data.flush()
    }

    companion object {
        const val FORMAT_VERSION = 1
        private const val MAGIC = 0x424C484C // "BLHL"
        private const val RAYS_PER_TASK = 32

        /** Traces 2 · nAlpha rays in parallel; hundreds of ms to a few seconds on a phone. */
        suspend fun generate(key: LensTableKey): LensTables = coroutineScope {
            val trajectories = FloatArray(2 * key.nPhi * key.nAlpha)
            val phiEnd = FloatArray(2 * key.nAlpha)
            (0 until 2 * key.nAlpha).chunked(RAYS_PER_TASK).map { rays ->
                async(Dispatchers.Default) {
                    val column = FloatArray(key.nPhi)
                    for (ray in rays) {
                        val branch = ray / key.nAlpha
                        val i = ray % key.nAlpha
                        val t = i.toDouble() / (key.nAlpha - 1)
                        val alpha = LensMath.alphaOf(branch, t, key.cameraDistance, key.k)
                        phiEnd[ray] = LensMath.traceRay(alpha, key.cameraDistance, column).toFloat()
                        for (j in 0 until key.nPhi) trajectories[(branch * key.nPhi + j) * key.nAlpha + i] = column[j]
                    }
                }
            }.awaitAll()
            LensTables(key, trajectories, phiEnd)
        }

        /** Reads tables written by [writeTo]; null if the stream holds other tables or another format. */
        fun readFrom(input: InputStream, expected: LensTableKey): LensTables? {
            val data = DataInputStream(input.buffered())
            if (data.readInt() != MAGIC || data.readInt() != FORMAT_VERSION) return null
            val key = LensTableKey(data.readDouble(), data.readInt(), data.readInt(), data.readDouble())
            if (key != expected) return null
            val trajectories = data.readFloats(2 * key.nPhi * key.nAlpha)
            val phiEnd = data.readFloats(2 * key.nAlpha)
            return LensTables(key, trajectories, phiEnd)
        }

        private fun DataOutputStream.writeFloats(values: FloatArray) {
            val bytes = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            bytes.asFloatBuffer().put(values)
            write(bytes.array())
        }

        private fun DataInputStream.readFloats(count: Int): FloatArray {
            val bytes = ByteArray(count * Float.SIZE_BYTES)
            readFully(bytes)
            return FloatArray(count).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
        }
    }
}
