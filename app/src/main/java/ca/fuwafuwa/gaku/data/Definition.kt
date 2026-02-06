package ca.fuwafuwa.gaku.data

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
    val content: String, // The definition text or serialized structured content
    val type: String     // "text" or "structured"
)