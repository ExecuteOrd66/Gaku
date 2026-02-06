package ca.fuwafuwa.gaku.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kanji",
    indices = [Index(value = ["character"]), Index(value = ["dictionaryId"])],
    foreignKeys = [
        ForeignKey(
            entity = Dictionary::class,
            parentColumns = ["id"],
            childColumns = ["dictionaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Kanji(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dictionaryId: Long,
    val character: String,
    val onyomi: String,
    val kunyomi: String,
    val tags: String,
    val meanings: String
)
