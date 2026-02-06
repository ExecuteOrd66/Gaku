package ca.fuwafuwa.gaku.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "term_meta",
    indices = [Index(value = ["expression"]), Index(value = ["dictionaryId"])],
    foreignKeys = [ForeignKey(entity = Dictionary::class, parentColumns = ["id"], childColumns = ["dictionaryId"], onDelete = ForeignKey.CASCADE)]
)
data class TermMeta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dictionaryId: Long,
    val expression: String,
    val mode: String,
    val data: String
)

@Entity(
    tableName = "kanji_meta",
    indices = [Index(value = ["character"]), Index(value = ["dictionaryId"])],
    foreignKeys = [ForeignKey(entity = Dictionary::class, parentColumns = ["id"], childColumns = ["dictionaryId"], onDelete = ForeignKey.CASCADE)]
)
data class KanjiMeta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dictionaryId: Long,
    val character: String,
    val mode: String,
    val data: String
)

@Entity(
    tableName = "tag_meta",
    indices = [Index(value = ["name"]), Index(value = ["dictionaryId"])],
    foreignKeys = [ForeignKey(entity = Dictionary::class, parentColumns = ["id"], childColumns = ["dictionaryId"], onDelete = ForeignKey.CASCADE)]
)
data class TagMeta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dictionaryId: Long,
    val name: String,
    val category: String,
    val orderIndex: Int,
    val notes: String,
    val score: Int
)
