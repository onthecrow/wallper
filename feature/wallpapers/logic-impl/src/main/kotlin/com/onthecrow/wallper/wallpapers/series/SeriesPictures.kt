package com.onthecrow.wallper.wallpapers.series

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.zip.CRC32
import kotlin.math.max

/**
 * The list's pictures of series, made of their members' pictures as [SeriesPictureLayout] places them, kept in
 * [directory] as `<group id>_<key>.webp`. The key is made of what the picture shows: the members' pictures,
 * each file's path, size and time, and how it is drawn ([FORMAT_VERSION], [SIZE]). A black hole's preview is
 * named by its settings, so new settings make a new key too. A group has one picture at a time: its older ones
 * are deleted once the new one is made, and all of them with the group.
 */
internal class SeriesPictures(private val directory: File) {

    /** One at a time: two screens asking for the same picture still write one file. */
    private val lock = Mutex()

    /** The path to [groupId]'s picture of [pictures], made now unless it was before; null if it can't be. */
    suspend fun picture(groupId: Int, pictures: List<String?>): String? = lock.withLock {
        val file = File(directory, "${groupId}_${key(pictures)}.$EXTENSION")
        if (!file.exists() && !render(pictures, file)) return@withLock null
        // Older pictures go only now: the list keeps showing one until the new one is ready.
        pictureFiles().filter { it.groupId == groupId && it != file }.forEach(File::delete)
        file.path
    }

    /** Deletes [groupId]'s pictures: a series of one shows its member's own. */
    suspend fun delete(groupId: Int) = lock.withLock {
        pictureFiles().filter { it.groupId == groupId }.forEach(File::delete)
    }

    /** Deletes the pictures of every group but [groupIds]: of those that are gone. */
    suspend fun retain(groupIds: Set<Int>) = lock.withLock {
        pictureFiles().filter { it.groupId !in groupIds }.forEach(File::delete)
    }

    private fun pictureFiles(): List<File> = directory.listFiles()?.toList().orEmpty()

    private val File.groupId: Int? get() = name.substringBefore('_').toIntOrNull()

    /** What the picture of [pictures] is named by: it changes with any of them, or with how it is drawn. */
    internal fun key(pictures: List<String?>): Long = CRC32().apply {
        update("$FORMAT_VERSION $SIZE".toByteArray())
        pictures.forEach { path ->
            val stamp = path?.let { File(it).run { "$it:${length()}:${lastModified()}" } } ?: "-"
            update(" $stamp".toByteArray())
        }
    }.value

    /**
     * Draws the pictures one over the next, each over everything right of its left line: the parts meet without
     * a seam, the line's smoothing over the part before. A picture fills its part's box, scaled to cover it and
     * cut evenly on the sides, as on the list's cards.
     */
    private fun render(pictures: List<String?>, file: File): Boolean {
        val layout = SeriesPictureLayout(pictures.size, SIZE.toFloat(), SIZE.toFloat())
        val output = createBitmap(SIZE, SIZE)
        try {
            val canvas = Canvas(output)
            pictures.forEachIndexed { index, path ->
                val left = layout.boxLeft(index)
                val right = layout.boxRight(index)
                val area = Path().apply {
                    moveTo(if (index == 0) 0f else layout.lineX(index, 0f), 0f)
                    lineTo(layout.width, 0f)
                    lineTo(layout.width, layout.height)
                    lineTo(if (index == 0) 0f else layout.lineX(index, layout.height), layout.height)
                    close()
                }
                val picture = path?.let { decode(it, right - left, layout.height) }
                if (picture == null) {
                    canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
                } else {
                    val shader = BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(coverMatrix(picture, left, right - left, layout.height))
                    }
                    canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
                    picture.recycle()
                }
            }
            directory.mkdirs()
            // Written aside first: a picture cut short never passes for a made one.
            val partial = File(directory, "${file.name}.partial")
            val written = partial.outputStream().use { output.compress(format(), QUALITY, it) }
            return written && partial.renameTo(file)
        } finally {
            output.recycle()
        }
    }

    /** Scales [picture] to cover [width] × [height] at [left], 0, cut evenly on the sides. */
    private fun coverMatrix(picture: Bitmap, left: Float, width: Float, height: Float) = Matrix().apply {
        val scale = max(width / picture.width, height / picture.height)
        setScale(scale, scale)
        postTranslate(left + (width - picture.width * scale) / 2, (height - picture.height * scale) / 2)
    }

    /**
     * [path] decoded as stored, like the wallpaper does, and subsampled as far as it still covers [width] ×
     * [height]; null if it can't be.
     */
    private fun decode(path: String, width: Float, height: Float): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val scale = max(width / bounds.outWidth, height / bounds.outHeight)
        val sample = Integer.highestOneBit((1 / scale).toInt().coerceAtLeast(1))
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        /** Bump when the picture is drawn differently: the old ones are made again. */
        const val FORMAT_VERSION = 1

        /** The list's cards are square, about half the screen wide; the black hole's preview is as wide. */
        const val SIZE = 540
        const val EXTENSION = "webp"
        const val QUALITY = 90

        /** Lossy WebP, alpha kept for the empty parts. */
        fun format(): Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
    }
}
