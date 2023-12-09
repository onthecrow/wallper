package com.onthecrow.wallper.data.tipeconverters

import android.graphics.Rect
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WallperTypeConverters {
    @TypeConverter
    fun rectFromString(value: String?): Rect? {
        return value?.let {
            Json.decodeFromString<ArrayList<Int>>(it).run {
                Rect(get(0), get(1), get(2), get(3))
            }
        }
    }

    @TypeConverter
    fun rectToString(rect: Rect?): String? {
        return rect?.run {
            Json.encodeToString(arrayListOf(left, top, right, bottom))
        }
    }
}