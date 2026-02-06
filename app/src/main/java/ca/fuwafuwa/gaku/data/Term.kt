package ca.fuwafuwa.gaku.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "terms",
    indices = [
        Index(value = ["expression"]),
        Index(value = ["reading"]),
        Index(value = ["sequence"]),
        Index(value = ["dictionaryId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Dictionary::class,
            parentColumns = ["id"],
            childColumns = ["dictionaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Term(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dictionaryId: Long,
    val expression: String,
    val reading: String,
    val tags: String,
    val rules: String,
    val score: Int,
    val sequence: Int,
    val termTags: String = ""
)
