package com.diary.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 日记附件类型
 */
enum class AttachmentType {
    IMAGE, VIDEO
}

/**
 * 日记附件
 */
data class DiaryAttachment(
    val type: AttachmentType,
    val uri: String,         // 本地文件 URI
    val thumbnailUri: String? = null,
    val fileName: String = "",
    val fileSize: Long = 0L
)

/**
 * 位置信息
 */
data class DiaryLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

/**
 * 日记实体
 */
@Entity(tableName = "diary_entries")
@TypeConverters(AttachmentConverter::class, LocationConverter::class)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String = "",

    val content: String = "",

    val attachments: List<DiaryAttachment> = emptyList(),

    val location: DiaryLocation? = null,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis(),

    val isFavorite: Boolean = false
)

class AttachmentConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromAttachments(attachments: List<DiaryAttachment>): String {
        return gson.toJson(attachments)
    }

    @TypeConverter
    fun toAttachments(data: String): List<DiaryAttachment> {
        if (data.isEmpty()) return emptyList()
        val type = object : TypeToken<List<DiaryAttachment>>() {}.type
        return gson.fromJson(data, type) ?: emptyList()
    }
}

class LocationConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromLocation(location: DiaryLocation?): String {
        return gson.toJson(location)
    }

    @TypeConverter
    fun toLocation(data: String): DiaryLocation? {
        if (data.isEmpty()) return null
        return gson.fromJson(data, DiaryLocation::class.java)
    }
}
