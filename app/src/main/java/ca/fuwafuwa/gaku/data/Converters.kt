package ca.fuwafuwa.gaku.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromDefinitionList(value: List<Definition>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDefinitionList(value: String?): List<Definition> {
        if (value.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<Definition>>() {}.type
        return gson.fromJson(value, type)
    }
}