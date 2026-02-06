package ca.fuwafuwa.gaku.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionaries")
data class Dictionary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val revision: String,
    val format: Int,
    val sequenced: Boolean = false,
    val isEnabled: Boolean = true
)
