package com.example.smartledger.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return Gson().toJson(list)
    }

    @TypeConverter
    fun fromDailyEntryList(list: List<DailyEntry>): String {
        return Gson().toJson(list)
    }

    @TypeConverter
    fun toDailyEntryList(value: String): List<DailyEntry> {
        val listType = object : TypeToken<List<DailyEntry>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromCustomFieldList(value: List<CustomField>?): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toCustomFieldList(value: String): List<CustomField>? {
        val listType = object : TypeToken<List<CustomField>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromCustomDailyEntryList(list: List<CustomDailyEntry>): String {
        return Gson().toJson(list)
    }

    @TypeConverter
    fun toCustomDailyEntryList(value: String): List<CustomDailyEntry> {
        val listType = object : TypeToken<List<CustomDailyEntry>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }
}
