package com.example.vaivan.data.local

import androidx.room.TypeConverter
import com.google.firebase.Timestamp
import java.util.Date

class Converters {

    @TypeConverter
    fun fromTimestamp(timestamp: Timestamp?): Long? {
        return timestamp?.toDate()?.time
    }

    @TypeConverter
    fun toTimestamp(milliseconds: Long?): Timestamp? {
        return milliseconds?.let { Timestamp(Date(it)) }
    }

    @TypeConverter
    fun fromList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        if (value.isBlank()) return emptyList()

        return value.split(",")
    }
}