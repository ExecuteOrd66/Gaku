package ca.fuwafuwa.gaku.data

@Entity(tableName = "dictionaries")
data class Dictionary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val revision: String,
    val version: Int,
    val type: Int, // 0 = Terms, 1 = Kanji
    val isEnabled: Boolean = true
)