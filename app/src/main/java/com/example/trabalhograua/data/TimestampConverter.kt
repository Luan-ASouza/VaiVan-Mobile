package com.example.trabalhograua.data

import androidx.room.TypeConverter
import com.google.firebase.Timestamp
import java.util.Date

class TimestampConverter {

    @TypeConverter
    fun fromTimestamp(timestamp: Timestamp?): Long? {
        return timestamp?.toDate()?.time
    }

    @TypeConverter
    fun toTimestamp(milliseconds: Long?): Timestamp? {
        return milliseconds?.let { Timestamp(Date(it)) }
    }
}