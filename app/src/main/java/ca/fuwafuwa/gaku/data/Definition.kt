package ca.fuwafuwa.gaku.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "definitions",
    indices = [Index(value = ["termId"])],
    foreignKeys = [
        ForeignKey(
            entity = Term::class,
            parentColumns = ["id"],
            childColumns = ["termId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Definition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termId: Long,
    val content: String,
    val type: String
)
