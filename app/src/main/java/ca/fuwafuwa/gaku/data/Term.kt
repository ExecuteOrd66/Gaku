package ca.fuwafuwa.gaku.data

@Entity(
    tableName = "terms",
    indices = [
        Index(value = ["expression"]),
        Index(value = ["reading"]),
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
    val tags: String,   // Space-separated tags (e.g., "noun common")
    val rules: String,  // Deinflection rules
    val score: Int,     // Frequency score
    val sequence: Int   // Entry sequence number
)