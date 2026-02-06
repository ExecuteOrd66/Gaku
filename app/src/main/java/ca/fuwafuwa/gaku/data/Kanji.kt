package ca.fuwafuwa.gaku.data

@Entity(
    tableName = "kanji",
    indices = [Index(value = ["character"])],
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
    val onyomi: String,  // Space separated
    val kunyomi: String, // Space separated
    val tags: String,
    val meanings: String // Newline separated
    // Stats can be added here as JSON or columns
)