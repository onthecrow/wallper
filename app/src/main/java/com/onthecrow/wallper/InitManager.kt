package com.onthecrow.wallper

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.RawRes
import com.onthecrow.wallper.data.WallpaperDao
import com.onthecrow.wallper.data.WallpaperEntity
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

object InitManager {

    private var applicationDir: File? = null

    suspend fun populateDbIfNeeded(context: Context, wallpaperDao: WallpaperDao) {
        if (wallpaperDao.getAll().first().isNotEmpty()) return
        val list = listOf(
            createWallpaperEntity(context, R.raw.video1, false),
            createWallpaperEntity(context, R.raw.video2, false),
            createWallpaperEntity(context, R.raw.video3, true),
            createWallpaperEntity(context, R.raw.video5, false),
            createWallpaperEntity(context, R.raw.video9, false),
        )
        wallpaperDao.insertAll(*list.toTypedArray())
    }

    private fun createWallpaperEntity(context: Context, @RawRes rawRes: Int, isActive: Boolean): WallpaperEntity {
        applicationDir = context.getExternalFilesDir(null)
        val tmpFilePath = "${applicationDir}/tmp.mp4"
        copyFromResToStorage(context, rawRes, tmpFilePath)
        val frame = getVideoFirstFrame(tmpFilePath)
        File(tmpFilePath).delete()
        return WallpaperEntity(
            Uri.parse("android.resource://com.onthecrow.wallper/".plus(rawRes)).toString(),
            frame,
            isActive
        )
    }

    private fun copyFromResToStorage(context: Context, @RawRes rawRes: Int, path: String) {
        context.resources.openRawResource(rawRes).use { iS ->
            File(path).outputStream().use { oS ->
                val array = ByteArray(4000)
                while (iS.available() > 0) {
                    iS.read(array)
                    oS.write(array)
                }
            }
        }
    }

    private fun getVideoFirstFrame(filePath: String): String {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val frame = retriever.getFrameAtTime(0)?.let { tryToSaveImage(it) } ?: ""
        retriever.release()
        return frame
    }

    private fun tryToSaveImage(image: Bitmap): String {
        val folder = File("${applicationDir}/thumbnails")
        if (!folder.exists()) {
            folder.mkdir()
        }
        val thumbPath = folder.path.plus("/${Math.random() * 1000000}.jpg")
        try {
            val quality = 100
            val fos = FileOutputStream(File(thumbPath))
            image.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return thumbPath
    }
}